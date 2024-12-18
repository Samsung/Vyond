// See LICENSE.SiFive for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard.examples

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters, Field, Config}

import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._


import sifive.blocks.util._

import worldguard._


case object WGPeripheryKey extends Field[Option[(WGPeripheryParams, WGCheckerParams)]](None)
trait CanHaveWGPeriphery { this: BaseSubsystem =>
  p(WGPeripheryKey) match {
    case Some((wgpParams, wgcParams)) => {
      val wgc = WGCheckerAttachParams(wgcParams).attachTo(this)
      WGPeripheryAttachParams(wgpParams).attachTo(this, wgc.wgc_node)
    }
    case None => None
  }
}

trait WGDeviceAttachParams {
  val device: DeviceParams
  val controlWhere: TLBusWrapperLocation
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType

  def attachTo(where: Attachable, wgc_node: TLAdapterNode)(implicit p: Parameters): LazyModule
}

case class WGPeripheryParams(
  address: BigInt,
  size: BigInt,
  width: Int) extends DeviceParams

case class WGPeripheryAttachParams(
  device: WGPeripheryParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends WGDeviceAttachParams
{
  def attachTo(where: Attachable, wgc_node: TLAdapterNode)(implicit p: Parameters): TLWGPeriphery = where {
    val name = s"wgperiphery_${WGPeriphery.nextId()}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val wgpClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val wgp = wgpClockDomainWrapper { LazyModule(new TLWGPeriphery(device, tlbus.beatBytes)) }
    wgp.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>
      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      wgpClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(wgp.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val wgpClockGroup = ClockGroup()
          wgpClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := wgpClockGroup } .getOrElse { wgpClockGroup }
      })

      (wgp.controlXing(controlXType)
        := wgc_node
        := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes, holdFirstDeny = true)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })

    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := wgp.intXing(intXType)

    wgp
  }
}

class TLWGPeriphery(params: WGPeripheryParams, beatBytes: Int)(implicit p: Parameters)
extends WGPeriphery(params, beatBytes) with HasTLControlRegMap

class WGPeriphery(params: WGPeripheryParams, beatBytes: Int)(implicit p: Parameters)
extends RegisterRouter(
  RegisterRouterParams(
    name = "wgperiphery",
    compat = Seq("sr,worldguard"),
    base = params.address,
    size = params.size,
    beatBytes = beatBytes))
with HasInterruptSources with HasTLControlRegMap
{
  def nInterrupts = 1 
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val reg = RegInit(0xa.U(params.width.W))
    regmap(
      0x00 -> Seq(
        RegField(params.width,
          RegReadFn({ valid => { (true.B, reg) } }),
          RegWriteFn({(valid, data) => when (valid) { reg := data }; true.B }))
      )
    )
  }
}

object WGPeriphery {
  val nextId = { var i = -1; () => { i+= 1; i} }
}
