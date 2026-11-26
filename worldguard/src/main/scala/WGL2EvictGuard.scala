// See LICENSE for license details.

/**
 * WGL2EvictGuard -- shared plumbing that lets the L2 ask the memory-port
 * WGChecker whether a WID may touch an address, before it evicts a line that
 * belongs to a different WID.
 *
 * Why this exists: a cross-WID miss (tag match, WID miss = `onlyTagHit`) makes
 * the L2 write the victim back and refill under the requester's WID. MSHR.scala
 * deliberately issues that writeback under the *requester's* wid, because using
 * the line's own (stale) wid gets the Release denied and no ReleaseAck ever
 * returns -- a confirmed deadlock. That workaround assumes the requester is
 * allowed at the address. When it is not, the Release is denied instead, and
 * TileLink's ReleaseAck carries no `denied` bit, so the same wedge comes back.
 *
 * Asking first removes the assumption: no permission -> refuse the request
 * outright (denied Grant) and leave the line and its dirty state untouched; the
 * line is never evicted on behalf of a world that could not have read it.
 * A requester that *is* permitted takes exactly the path it takes today, which
 * is what keeps I1 ("a line exists only while some slot admits its WID") true.
 *
 * The checker's slot state reaches the L2 over a BundleBridge rather than a
 * BoringUtils tap: the name-based boring API is deprecated in Chisel 6 and its
 * WiringTransform annotations fail in this flow ("More than one
 * SourceAnnotation defined for pin"), while the replacement (`bore`/`tapAndRead`)
 * needs a Scala reference across two separate LazyModules. A BundleBridge is
 * the mechanism rocket-chip already uses for non-TileLink signals between
 * LazyModules, and diplomacy resolves it regardless of construction order.
 */

package worldguard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters, Config}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

/** Enables the guard. Off by default so existing builds are bit-identical. */
case object WGL2EvictGuardKey extends Field[Boolean](false)

/**
 * The memory-port checker's rule state, as the L2 needs to see it. `wgc`
 * carries the slots already expanded to WGC (mask computed), so the L2 feeds a
 * Checker instance exactly the way WGChecker does and cannot drift from it.
 */
class WGCheckerState(val nSlots: Int, val paddrBits: Int, val granularity: Int,
                     val lgAlign: Int, val widWidth: Int) extends Bundle {
  val mwid     = UInt(widWidth.W)
  val wgc      = Vec(nSlots, new WGC(paddrBits, granularity, lgAlign))
  val slot0Cfg = new WGCConfig
}

object WGCheckerState {
  def apply(params: WGCheckerParams): WGCheckerState =
    new WGCheckerState(params.nSlots, params.paddrBits, params.granularity,
                       params.lgAlign, params.widWidth)
}

/**
 * The parameters of the memory-port checker, derived from Parameters in one
 * place so System.scala (which builds the checker) and the L2 (which sizes its
 * sink and its Checker replica) cannot disagree about slot count or alignment.
 */
object WGMemPortCheckerParams {
  def apply(p: Parameters): WGCheckerParams = WGCheckerParams(
    postfix     = s"wgp_memport_",
    mwid        = p(NWorlds) - 1,
    widWidth    = log2Ceil(p(NWorlds)),
    nSlots      = p(NWGSlots),
    address     = 0x6000000,
    size        = 4096,
    lgMaxSize   = 6,
    granularity = 64,
    lgAlign     = 6,
    exportState = p(WGL2EvictGuardKey))
}

/**
 * Carries the L2's sink from where the coherence manager builds it to where
 * the memory-port checker is built. The L2 is constructed while the bus
 * topology comes up, the checker later in a subsystem trait, so the sink is
 * parked here and the checker side makes the connection when it has both ends.
 */
trait CanHaveWGL2EvictGuard { this: BaseSubsystem =>
  var wgl2GuardSink: Option[BundleBridgeSink[WGCheckerState]] = None
}

/** Turns the guard on. Off by default, so other builds are unchanged. */
class WithWGL2EvictGuard extends Config((site, here, up) => {
  case WGL2EvictGuardKey => true
})
