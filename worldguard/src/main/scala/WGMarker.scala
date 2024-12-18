// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters, Config}

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

case object WGMarkerBaseAddressKey extends Field[BigInt](0)

object WGMCtrlReg {
  val vendor        = 0x00
  val impid         = 0x04
  val wid           = 0x08
  val lockValid     = 0x0c
}
class WGMLockValid extends Bundle {
  val res   = UInt(6.W)
  val v = Bool()
  val l  = Bool()
}


case class WGMarkerParams(
  val postfix: String,
  val wid: Int,
  val widWidth: Int,

  val address: BigInt,
  val size: Int
) extends DeviceParams {
  require (widWidth == 2, "Current version supports only 4 world ids")
  require (wid < 4, "wid cannot be greater than 3")
}

case class WGMarkerAttachParams(
  device: WGMarkerParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLWGMarker = where {
    val name = s"wgmarker_${WGMarker.nextId()}_${device.postfix}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val wgmClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val wgm = wgmClockDomainWrapper { LazyModule(new TLWGMarker(device, tlbus.beatBytes)) }
    wgm.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>
      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      wgmClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(wgm.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val wgmClockGroup = ClockGroup()
          wgmClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := wgmClockGroup } .getOrElse { wgmClockGroup }
      })

      (wgm.controlXing(controlXType)
        := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes, holdFirstDeny = true)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })

    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := wgm.intXing(intXType)

    wgm
  }
}



class TLWGMarker(params: WGMarkerParams, beatBytes: Int)(implicit p: Parameters)
extends WGMarker(params, beatBytes) with HasTLControlRegMap 

class WGMarker(params: WGMarkerParams, beatBytes: Int)(implicit p: Parameters) extends RegisterRouter(
  RegisterRouterParams(
    name = s"wgmarker_${params.postfix}",
    compat = Seq("sr,worldguard"),
    base = params.address,
    size = params.size,
    beatBytes = beatBytes))
with HasInterruptSources with HasTLControlRegMap
{
  //val device = new SimpleDevice("wgmarker", Seq("sr,worldguarld"))
  //val wgm_node= TLRegisterNode(
  //  address = Seq(AddressSet(params.address, params.size-1)),
  //  device = device,
  //  beatBytes = beatBytes)
 
  def nInterrupts = 1

  val rf = if (p(UseWGTLCustomField)) Seq(WGTLCustomField(params.widWidth)) else Seq()
  val rk = if (p(UseWGTLCustomField)) Seq(WGTLCustomFieldKey) else Seq()

  val wgm_node = new TLAdapterNode(
    clientFn  = { c => c.v1copy(requestFields = rf) },
    managerFn = { m => m },
  )
  
  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {

    //------------------------------------------------------------------------------
    // MMIO Registers
    //------------------------------------------------------------------------------
    val vendorReg       = RegInit(0.U(32.W))
    val impidReg        = RegInit(0.U(16.W))
    val widReg          = RegInit(params.wid.U(params.widWidth.W))
    val lockValidReg    = RegInit({
      val w = Wire(new WGMLockValid)
      w.res := 0.U
      w.l := 0.U
      w.v := 0.U
      w
    })

    val mapping = Seq(
      WGMCtrlReg.vendor -> Seq(RegField.r(vendorReg.getWidth, vendorReg)),
      WGMCtrlReg.impid -> Seq(RegField.r(impidReg.getWidth, impidReg)),
      WGMCtrlReg.wid -> Seq(RegField(widReg.getWidth,
        RegReadFn({ valid => (true.B, widReg) }),
        RegWriteFn({(valid, data) => when (valid & !lockValidReg.l) { widReg := data }; true.B }))),
      WGMCtrlReg.lockValid -> Seq(
        RegField(lockValidReg.l.getWidth,
          RegReadFn({ valid => (true.B, lockValidReg.l) }),
          RegWriteFn({(valid, data) => when (valid & !lockValidReg.l) { lockValidReg.l := data }; true.B })),
        RegField(lockValidReg.v.getWidth,
          RegReadFn({ valid => (true.B, lockValidReg.v) }),
          RegWriteFn({(valid, data) => when (valid & !lockValidReg.l) { lockValidReg.v := data }; true.B })),
        RegField(lockValidReg.res.getWidth,
          RegReadFn({ valid => (true.B, lockValidReg.res) }),
          RegWriteFn({(valid, data) => when (valid) { lockValidReg.v := 0.U }; true.B }))
      )
    )

    regmap(mapping:_*)

    (wgm_node.in zip wgm_node.out) foreach {
      case ((in, edgeIn), (out, edgeOut)) => {
        out.waiveAll :<>= in.waiveAll
        when (in.a.valid) {
          out.a.bits.user.lift(WGTLCustomFieldKey).foreach { x =>
          x.wid := widReg
          }
        }
      }
    }
  }
}

object WGMarker
{
  val nextId = { var i = -1; () => { i+= 1; i} }
}
