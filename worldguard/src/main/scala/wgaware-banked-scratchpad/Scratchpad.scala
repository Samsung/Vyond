// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard

import chisel3._
import chisel3.util._

import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.prci.{ClockSinkDomain, ClockSinkParameters}

case object WGScratchpadBankKey extends Field[Option[WGCheckerParams]](None)
case object WGBankedScratchpadKey extends Field[Seq[WGBankedScratchpadParams]](Nil)

case class WGBankedScratchpadParams(
  base: BigInt,
  size: BigInt,
  busWhere: TLBusWrapperLocation = SBUS,
  banks: Int = 4,
  subBanks: Int = 2,
  name: String = "banked-scratchpad",
  disableMonitors: Boolean = false,
  buffer: BufferParams = BufferParams.none,
  outerBuffer: BufferParams = BufferParams.none
)

class WGScratchpadBank(subBanks: Int, address: AddressSet, beatBytes: Int, devOverride: MemoryDevice, buffer: BufferParams)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val mask = (subBanks - 1) * p(CacheBlockBytes)
  val xbar = TLXbar()
  (0 until subBanks).map { sb =>
    val ram = LazyModule(new TLRAM(
      address = AddressSet(address.base + sb * p(CacheBlockBytes), address.mask - mask),
      beatBytes = beatBytes,
      devOverride = Some(devOverride)))

    ram.node :=  TLFragmenter(beatBytes, p(CacheBlockBytes)) := TLBuffer(buffer) := xbar
  }
}

trait CanHaveWGBankedScratchpad { this: BaseSubsystem =>
  p(WGBankedScratchpadKey).zipWithIndex.foreach { case (params, si) =>
    val bus = locateTLBusWrapper(params.busWhere)

    require (params.subBanks >= 1)

    val name = params.name
    val banks = params.banks
    val bankStripe = p(CacheBlockBytes)*params.subBanks
    val mask = (params.banks-1)*bankStripe
    val device = new MemoryDevice

    def genBanks()(implicit p: Parameters) = (0 until banks).map { b =>
      val bank = LazyModule(new WGScratchpadBank(
        params.subBanks,
        AddressSet(params.base + bankStripe * b, params.size - 1 - mask),
        bus.beatBytes,
        device,
        params.buffer))
      val nWorlds = p(NWorlds)
      val base = 0x2060000
      val wgcParams = WGCheckerParams(
        postfix = s"wgpscratchpadbank_${b}",
        mwid      = nWorlds - 1,
        widWidth  = log2Ceil(nWorlds),
        nSlots    = 4,
        address   = base + 0x10000 * b,
        size      = 4096)
      val wgc = WGCheckerAttachParams(wgcParams).attachTo(this)
      bank.clockNode := bus.fixedClockNode
      bus.coupleTo(s"$name-$si-$b") { bank.xbar := wgc.wgc_node := bus { TLBuffer(params.outerBuffer) } := _ }
      //bus.coupleTo(s"$name-$si-$b") { bank.xbar := bus { TLBuffer(params.outerBuffer) } := _ }
    }

    if (params.disableMonitors) DisableMonitors { implicit p => genBanks()(p) } else genBanks()
  }
}

