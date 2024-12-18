// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard.rocket

import scala.collection.mutable.ListBuffer

import chisel3._
import chisel3.util.{isPow2,log2Ceil,log2Up,Decoupled,Valid}
import chisel3.dontTouch

import org.chipsalliance.cde.config._

import freechips.rocketchip.rocket._
import freechips.rocketchip.amba._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

import worldguard._

class WGL1Metadata(implicit p: Parameters) extends L1HellaCacheBundle()(p) {
  val coh = new ClientMetadata
  val tag = UInt(tagBits.W)
  val wid = UInt(log2Ceil(p(NWorlds)).W)
}

object WGL1Metadata {
  def apply(tag: Bits, coh: ClientMetadata, wid: UInt)(implicit p: Parameters) = {
    val meta = Wire(new WGL1Metadata)
    meta.tag := tag
    meta.coh := coh
    meta.wid := wid
    meta
  }
}

trait HasWGHellaCache { this: BaseTile =>
  val module: HasWGHellaCacheModule
  implicit val p: Parameters
  var nDCachePorts = 0
  lazy val dcache: WGHellaCache = LazyModule(p(BuildWGHellaCache)(this)(p))

  tlMasterXbar.node := TLWidthWidget(tileParams.dcache.get.rowBits/8) := dcache.node
  dcache.hartIdSinkNodeOpt.map { _ := hartIdNexusNode }
  dcache.mmioAddressPrefixSinkNodeOpt.map { _ := mmioAddressPrefixNexusNode }
  InModuleBody {
    dcache.module match {
      case module: WGDCacheModule => module.tlb_port := DontCare
      case other => other
    }
  }
}

trait HasWGHellaCacheModule {
  val outer: HasWGHellaCache with HasTileParameters
  implicit val p: Parameters
  val dcachePorts = ListBuffer[WGHellaCacheIO]()
  val dcacheArb = Module(new WGHellaCacheArbiter(outer.nDCachePorts)(outer.p))
  outer.dcache.module.io.cpu <> dcacheArb.io.mem
}

case object BuildWGHellaCache extends Field[BaseTile => Parameters => WGHellaCache](WGHellaCacheFactory.apply)

object WGHellaCacheFactory {
  def apply(tile: BaseTile)(p: Parameters): WGHellaCache = {
    require (tile.tileParams.dcache.get.nMSHRs == 0) // FIXME: extend NonBlockingDcache for wid
    new WGDCache(log2Ceil(p(NWorlds)), tile.tileId, tile.crossing)(p)
    //if (tile.tileParams.dcache.get.nMSHRs == 0)
    //  new WGDCache(tile.tileId, tile.crossing)(p)
    //else
    //  new NonBlockingDCache(tile.tileId)(p)
  }
}

trait HasWGCoreMemOp extends HasL1HellaCacheParameters {
  val addr = UInt(coreMaxAddrBits.W)
  val idx  = (usingVM && untagBits > pgIdxBits).option(UInt(coreMaxAddrBits.W))
  val tag  = UInt((coreParams.dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val cmd  = UInt(M_SZ.W)
  val size = UInt(log2Ceil(coreDataBytes.log2 + 1).W)
  val signed = Bool()
  val dprv = UInt(PRV.SZ.W)
  val dv = Bool()
}

class WGHellaCacheReqInternal(implicit p: Parameters) extends CoreBundle()(p) with HasWGCoreMemOp {
  val phys = Bool()
  val no_alloc = Bool()
  val no_xcpt = Bool()
  val wid = UInt(log2Ceil(p(NWorlds)).W)
}

class WGHellaCacheReq(implicit p: Parameters) extends WGHellaCacheReqInternal()(p) with HasCoreData

class WGHellaCacheIO(implicit p: Parameters) extends CoreBundle()(p) {
  val req = Decoupled(new WGHellaCacheReq)
  val s1_kill = Output(Bool()) // kill previous cycle's req
  val s1_data = Output(new HellaCacheWriteData()) // data for previous cycle's req
  val s2_nack = Input(Bool()) // req from two cycles ago is rejected
  val s2_nack_cause_raw = Input(Bool()) // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill = Output(Bool()) // kill req from two cycles ago
  val s2_uncached = Input(Bool()) // advisory signal that the access is MMIO
  val s2_paddr = Input(UInt(paddrBits.W)) // translated address

  val resp = Flipped(Valid(new HellaCacheResp))
  val replay_next = Input(Bool())
  val s2_xcpt = Input(new HellaCacheExceptions)
  val s2_gpa = Input(UInt(vaddrBitsExtended.W))
  val s2_gpa_is_pte = Input(Bool())
  val uncached_resp = tileParams.dcache.get.separateUncachedResp.option(Flipped(Decoupled(new HellaCacheResp)))
  val ordered = Input(Bool())
  val perf = Input(new HellaCachePerfEvents())

  val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
  val clock_enabled = Input(Bool()) // is D$ currently being clocked?
}

class WGHellaCacheBundle(val outer: WGHellaCache)(implicit p: Parameters) extends CoreBundle()(p) {
  val cpu = Flipped((new WGHellaCacheIO))
  val ptw = new WGTLBPTWIO()
  val errors = new DCacheErrors
}

abstract class WGHellaCache(tileId: Int)(implicit p: Parameters) extends LazyModule
    with HasNonDiplomaticTileParameters {
  protected val cfg = tileParams.dcache.get

  protected def cacheClientParameters = cfg.scratch.map(x => Seq()).getOrElse(Seq(TLMasterParameters.v1(
    name          = s"Core ${tileId} DCache",
    sourceId      = IdRange(0, 1 max cfg.nMSHRs),
    supportsProbe = TransferSizes(cfg.blockBytes, cfg.blockBytes))))

  protected def mmioClientParameters = Seq(TLMasterParameters.v1(
    name          = s"Core ${tileId} DCache MMIO",
    sourceId      = IdRange(firstMMIO, firstMMIO + cfg.nMMIOs),
    requestFifo   = true))

  def firstMMIO = (cacheClientParameters.map(_.sourceId.end) :+ 0).max

  val rf = if (tileParams.core.useVM) {
    val f = if (p(UseWGTLCustomField)) Seq(WGTLCustomField(log2Ceil(p(NWorlds)))) else Seq()
    f
  } else {
    val f = Seq(AMBAProtField())
    f
  }
  val rk = if (p(UseWGTLCustomField)) Seq(WGTLCustomFieldKey) else Seq()
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = cacheClientParameters ++ mmioClientParameters,
    minLatency = 1,
    requestFields = rf)))

  val hartIdSinkNodeOpt = cfg.scratch.map(_ => BundleBridgeSink[UInt]())
  val mmioAddressPrefixSinkNodeOpt = cfg.scratch.map(_ => BundleBridgeSink[UInt]())

  val module: WGHellaCacheModule

  def flushOnFenceI = cfg.scratch.isEmpty && !node.edges.out(0).manager.managers.forall(m => !m.supportsAcquireB || !m.executable || m.regionType >= RegionType.TRACKED || m.regionType <= RegionType.IDEMPOTENT)

  def canSupportCFlushLine = !usingVM || cfg.blockBytes * cfg.nSets <= (1 << pgIdxBits)

  require(!tileParams.core.haveCFlush || cfg.scratch.isEmpty, "CFLUSH_D_L1 instruction requires a D$")
}

class WGHellaCacheModule(outer: WGHellaCache) extends LazyModuleImp(outer)
    with HasL1HellaCacheParameters {
  implicit val edge = outer.node.edges.out(0)
  val (tl_out, _) = outer.node.out(0)
  val io = IO(new WGHellaCacheBundle(outer))
  val io_hartid = outer.hartIdSinkNodeOpt.map(_.bundle)
  val io_mmio_address_prefix = outer.mmioAddressPrefixSinkNodeOpt.map(_.bundle)
  dontTouch(io.cpu.resp) // Users like to monitor these fields even if the core ignores some signals
  dontTouch(io.cpu.s1_data)

  require(rowBits == edge.bundle.dataBits)

  private val fifoManagers = edge.manager.managers.filter(TLFIFOFixer.allVolatile)
  fifoManagers.foreach { m =>
    require (m.fifoId == fifoManagers.head.fifoId,
      s"IOMSHRs must be FIFO for all regions with effects, but HellaCache sees\n"+
      s"${m.nodePath.map(_.name)}\nversus\n${fifoManagers.head.nodePath.map(_.name)}")
  }
}
