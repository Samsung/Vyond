// See LICENSE.SiFive for license details.
// See LICENSE for license details.

/**
 * WorldGuard Checker (WGC) implementation is based on the PMP in Rocketchip.
 * WGC adds extra registers "perm" for each entry 
 * and modifies a logic to check the permission.
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters, Field, Config}

import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

import sifive.blocks.util._

case class WGCheckerParams(
  postfix: String,
  mwid: Int,
  widWidth: Int,
  nSlots: Int,

  address: BigInt,
  size: Int,
  paddrBits: Int    = 64,
  lgMaxSize: Int    = 3,   // 2^3=8bytes
  granularity: Int  = 4,   // 4bytes
  lgAlign: Int      = 2    // 2^2=4bytes
) extends DeviceParams

case class WGCheckerAttachParams(
  device: WGCheckerParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing) extends DeviceAttachParams
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLWGChecker = where {
    val name = s"wgchecker_${WGChecker.nextId()}_${device.postfix}"
    val tlbus = where.locateTLBusWrapper(controlWhere)
    val wgcClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val wgc = wgcClockDomainWrapper { LazyModule(new TLWGChecker(device, tlbus.beatBytes)) }
    wgc.suggestName(name)

    tlbus.coupleTo(s"device_named_$name") { bus =>
      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, tlbus.beatBytes, tlbus.beatBytes)))
        tlbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(tlbus) := _ }
        blocker
      }

      wgcClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          tlbus.dtsClk.map(_.bind(wgc.device))
          tlbus.fixedClockNode
        case _: RationalCrossing =>
          tlbus.clockNode
        case _: AsynchronousCrossing =>
          val wgcClockGroup = ClockGroup()
          wgcClockGroup := where.allClockGroupsNode
          blockerOpt.map { _.clockNode := wgcClockGroup } .getOrElse { wgcClockGroup }
      })

      (wgc.controlXing(controlXType)
        := TLFragmenter(tlbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })

    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := wgc.intXing(intXType)
    wgc
  }
}

class TLWGChecker(params: WGCheckerParams, beatBytes: Int)(implicit p: Parameters)
extends WGChecker(params, beatBytes) with HasTLControlRegMap

class WGChecker(params: WGCheckerParams, beatBytes: Int)(implicit p: Parameters)
extends RegisterRouter(
  RegisterRouterParams(
    name = s"wgchecker_${params.postfix}",
    compat = Seq("sr,worldguard"),
    base = params.address,
    size = params.size,
    beatBytes = beatBytes))
with HasInterruptSources with HasTLControlRegMap 
{
  def nInterrupts = 1 

  // If clientFn and managerFn don't have to be provided, use Identity Node
  val wgc_node = new TLAdapterNode(
    clientFn  = { c => c },
    managerFn = {
      sp => sp.v1copy(
        managers = sp.managers.map(_.v1copy(alwaysGrantsT = false, mayDenyGet = true, mayDenyPut = true)),
        endSinkId = if (sp.endSinkId == 0) { 0} else { sp.endSinkId + 1 },
        minLatency = 1 min sp.minLatency,
        responseFields = if (p(UseWGTLCustomField)) Seq(WGTLCustomField(params.widWidth)) else Seq(),
        requestKeys = if (p(UseWGTLCustomField)) Seq(WGTLCustomFieldKey) else Seq()
      )
    }
  ) 

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {

    val mwidReg = RegInit(params.mwid.U(params.widWidth.W))  // need to be set from outside (Input pin via worldguard port)
    //------------------------------------------------------------------------------------
    // MMIO Registers
    //------------------------------------------------------------------------------------
    val vendorReg                 = RegInit(0.U(32.W))
    val impidReg                  = RegInit(0.U(16.W))
    val nSlotsReg                 = RegInit(params.nSlots.U(32.W))
    val errorCauseSlotWidRWReg    = RegInit({
      val w = Wire(new WGCErrorCauseSlotWidRW)
      w.res15_10  := 0.U
      w.at        := 0.U
      w.res7_2    := 0.U
      w.wid       := 0.U
      w
    })
    val errorCauseSlotBeIpReg     = RegInit({
      val w = Wire(new WGCErrorCauseSlotBeIp)
      w.i       := false.B
      w.b       := false.B
      w.res29_0 :=0.U
      w
    })
    val errorAddrReg              = RegInit(0.U(64.W)) // why is it 32 bit ? phsicall address space is 64(47) bits...

    val wgcReg                    = RegInit(VecInit(Seq.fill(params.nSlots) {
      val w = Wire(new WGCReg(params.paddrBits, params.granularity, params.lgAlign))
      w.addr          := 0.U
      w.perm          := (3.U << mwidReg * 2.U)
      w.cfg.l         := 0.U
      w.cfg.res30_12  := 0.U
      w.cfg.iw        := 0.U
      w.cfg.ir        := 0.U
      w.cfg.ew        := 0.U
      w.cfg.er        := 0.U
      w.cfg.res7_2    := 0.U
      w.cfg.a         := 0.U
      w
    }))

    //------------------------------------------------------------------------------------
    // Register Mapping
    // TODO: Check permission to access WGC Registers 
    //------------------------------------------------------------------------------------
    val mapping = Seq(
      WGCCtrlReg.vendor -> Seq(RegField.r(vendorReg.getWidth, vendorReg, RegFieldDesc("VENDOR", "", volatile=true))),
      WGCCtrlReg.impid  -> Seq(RegField.r(impidReg.getWidth, impidReg, RegFieldDesc("IMPID", "", volatile=true))),
      WGCCtrlReg.nslots -> Seq(RegField.r(nSlotsReg.getWidth, nSlotsReg, RegFieldDesc("NSLOTS", "", volatile=true))),
      WGCCtrlReg.errorCauseSlotWidRW -> Seq(
        RegField.r(errorCauseSlotWidRWReg.wid.getWidth, errorCauseSlotWidRWReg.wid, RegFieldDesc("WID_Value", "", volatile=true)),
        RegField.r(errorCauseSlotWidRWReg.res7_2.getWidth, errorCauseSlotWidRWReg.res7_2, RegFieldDesc("Reserved", "", volatile=true)),
        RegField.r(errorCauseSlotWidRWReg.at.getWidth, errorCauseSlotWidRWReg.at, RegFieldDesc("Access_TYpe", "", volatile=true)),
        RegField.r(errorCauseSlotWidRWReg.res15_10.getWidth, errorCauseSlotWidRWReg.res15_10, RegFieldDesc("Reserved", "", volatile=true))),
      WGCCtrlReg.errorCauseSlotBeIp -> Seq(
        RegField(errorCauseSlotBeIpReg.res29_0.getWidth,
          RegReadFn({ valid => (true.B, errorCauseSlotBeIpReg.res29_0) }),
          RegWriteFn({(valid, data) => when (valid) { errorCauseSlotBeIpReg.res29_0 := 0.U }; true.B }),
          RegFieldDesc("Reserved", "", volatile=true)),
        RegField(errorCauseSlotBeIpReg.b.getWidth, errorCauseSlotBeIpReg.b, RegFieldDesc("Bus_Error_Generated", "", volatile=true)),
        RegField(errorCauseSlotBeIpReg.i.getWidth, errorCauseSlotBeIpReg.i, RegFieldDesc("Interrupt_Generate", "", volatile=true))),
      WGCCtrlReg.errorAddr -> Seq(RegField.r(errorAddrReg.getWidth, errorAddrReg, RegFieldDesc("ERROR_ADDR", "", volatile=true))),
    )

    val wgcRegMap = wgcReg.zipWithIndex.map { case (r, i) =>
      val m: Seq[RegField.Map] = Seq(
        WGCCtrlReg.slotBase + 0x20 * (i+1) -> Seq(RegField(r.addr.getWidth,
          RegReadFn({ valid => (true.B, r.addr) }),
          RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.addr := data }; true.B }),
          RegFieldDesc(s"wgcReg_${i}_Addr", "", volatile=true))),
        WGCCtrlReg.slotBase + 0x20 * (i+1) + 0x8 -> Seq(RegField(r.perm.getWidth,
          RegReadFn({ valid => (true.B, r.perm) }),
          RegWriteFn({ (valid, data) =>
            when (valid) {
              r.perm := data | (3.U << mwidReg * 2.U)
            }
            true.B
          }),
          RegFieldDesc(s"wgcReg_${i}_Perm", "", volatile=true))),
        WGCCtrlReg.slotBase + 0x20 * (i+1) + 0x10 -> Seq(
          RegField(r.cfg.a.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.a) }),
            RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.cfg.a := data }; true.B }),
            RegFieldDesc(s"wgcReg_${i}_Cfg.a",  "", volatile=true)),  // <<--- make it read only after testing is done
          RegField(r.cfg.res7_2.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.res7_2) }),
            RegWriteFn({(valid, data) => when (valid) { r.cfg.res7_2 := 0.U }; true.B}),
            RegFieldDesc(s"wgcReg_${i}_Cfg.res","", volatile=true)),
          RegField(r.cfg.er.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.er) }),
            RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.cfg.er := data }; true.B }),
            RegFieldDesc(s"wgcReg_${i}_Cfg.er", "", volatile=true)),
          RegField(r.cfg.ew.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.ew) }),
            RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.cfg.ew := data }; true.B }),
            RegFieldDesc(s"wgcReg_${i}_Cfg.ew", "", volatile=true)),
          RegField(r.cfg.ir.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.ir) }),
            RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.cfg.ir := data }; true.B }),
            RegFieldDesc(s"wgcReg_${i}_Cfg.ir", "", volatile=true)),
          RegField(r.cfg.iw.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.iw) }),
            RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.cfg.iw := data }; true.B }),
            RegFieldDesc(s"wgcReg_${i}_Cfg.iw", "", volatile=true)),
          RegField(r.cfg.res30_12.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.res30_12) }),
            RegWriteFn({(valid, data) => when (valid) { r.cfg.res30_12 := 0.U }; true.B}),
            RegFieldDesc(s"wgcReg_${i}_Cfg.res","", volatile=true)),
          RegField(r.cfg.l.getWidth,
            RegReadFn({ valid => (true.B, r.cfg.l) }),
            RegWriteFn({(valid, data) => when (valid && !r.cfg.l) { r.cfg.l := data }; true.B }),
            RegFieldDesc(s"wgcReg_${i}_Cfg.l",  "", volatile=true)))
      )
      m
    }
    regmap(mapping ++ wgcRegMap.flatten.toSeq:_*)

    (wgc_node.in zip wgc_node.out) foreach {
      case ((in, edgeIn), (out, edgeOut)) => {
        out.waiveAll :<>= in.waiveAll // waive wid field

        val wgchecker = Module(new Checker(params.nSlots, params.paddrBits, params.lgMaxSize, params.granularity, params.lgAlign, params.widWidth))
        wgchecker.io.mwid := mwidReg
        wgchecker.io.wgc  := wgcReg.map(WGC(_, params.paddrBits, params.granularity, params.lgAlign))

        // IO between wgchecker and Bundle A
        wgchecker.io.size := in.a.bits.size
        wgchecker.io.addr := in.a.bits.address
        in.a.bits.user.lift(WGTLCustomFieldKey).foreach { x => 
        wgchecker.io.wid := x.wid }
        val wid = wgchecker.io.wid 
        
        //------------------------------------------------------------------------------------
        // Check if the reqeustor (wid) is allowed to access the address
        //------------------------------------------------------------------------------------
        val mySinkId = edgeOut.manager.endSinkId.U
        val a_first = edgeIn.first(in.a)
        dontTouch(a_first)
        val (d_first, d_last, _) = edgeIn.firstlast(in.d)

        val r = TLMessages.Get <= in.a.bits.opcode && in.a.bits.opcode <= TLMessages.AcquirePerm
        val w = in.a.bits.opcode <= TLMessages.LogicalData
        val rv = ~(Mux(r, wgchecker.io.r, true.B))   // read violation
        val wv = ~(Mux(w, wgchecker.io.w, true.B))   // write violation
        val allowFirst = Mux(in.a.valid, ~rv & ~wv, true.B)
        val allow = allowFirst holdUnless a_first  // don't change our mind in the middle transaction.
        dontTouch(allow)

        //------------------------------------------------------------------------------------
        // Buss Error (Deny)
        //------------------------------------------------------------------------------------
        val er = Mux(rv, wgchecker.io.cfg.er, false.B)  // Need signal bus error due to read violation?
        val ew = Mux(wv, wgchecker.io.cfg.ew, false.B)
    
        //------------------------------------------------------------------------------------
        // Generate Interrupt
        //------------------------------------------------------------------------------------
        val ir = Mux(rv, wgchecker.io.cfg.ir, false.B)  // Need signal interrupt due to read violation?
        val iw = Mux(wv, wgchecker.io.cfg.iw, false.B)
        interrupts(0) := ir | iw


        //------------------------------------------------------------------------------------
        // Update error status
        //------------------------------------------------------------------------------------
        when (in.a.valid && !allow && a_first) {
          errorAddrReg            := in.a.bits.address
          errorCauseSlotWidRWReg  := Cat(0.U(6.W), w, r, 0.U(6.W), wid).asTypeOf(new WGCErrorCauseSlotWidRW)
          errorCauseSlotBeIpReg   := Cat(ir | iw, er | ew, 0.U(30.W)).asTypeOf(new WGCErrorCauseSlotBeIp)
        } .elsewhen (in.a.valid && allow) {
          errorAddrReg            := 0.U(errorAddrReg.getWidth.W)
          errorCauseSlotWidRWReg  := 0.U.asTypeOf(new WGCErrorCauseSlotWidRW)
          errorCauseSlotBeIpReg   := 0.U.asTypeOf(new WGCErrorCauseSlotBeIp)
        }



        // Track the progress of transactions from A => D
        val d_rack  = edgeIn.manager.anySupportAcquireB.B && in.d.bits.opcode === TLMessages.ReleaseAck
        val flight = RegInit(0.U(log2Ceil(edgeIn.client.endSourceId+1+1).W)) // +1 for inclusive range +1 for a_first vs. d_last
        val denyWait = RegInit(false.B) // deny already inflight?
        flight := flight + (a_first && in.a.fire) - (d_last && !d_rack && in.d.fire)

        // Discard denied A traffic, but first block it until there is nothing in-flight
        val deny_ready = !denyWait && flight === 0.U
        dontTouch(deny_ready)
        in.a.ready := Mux(allow, out.a.ready, !a_first || deny_ready)
        out.a.valid := in.a.valid && allow

        // Frame an appropriate deny message
        val denyValid = RegInit(false.B)
        val deny = Reg(in.d.bits.cloneType)
        val d_opcode = TLMessages.adResponse(in.a.bits.opcode)
        val d_grant = edgeIn.manager.anySupportAcquireB.B && deny.opcode === TLMessages.Grant
        when (in.a.valid && !allow && deny_ready && a_first) {
          denyValid    := true.B
          denyWait     := true.B
          deny.opcode  := d_opcode
          deny.param   := 0.U // toT, but error grants must be handled transiently (ie: you don't keep permissions)
          deny.size    := in.a.bits.size
          deny.source  := in.a.bits.source
          deny.sink    := mySinkId
          deny.denied  := er | ew
          deny.data    := 0.U
          deny.corrupt := Mux(er | ew, d_opcode(0), false.B)   // false during testing
        }
        when (denyValid && in.d.ready && d_last) {
          denyValid := false.B
          when (!d_grant) {
            denyWait := false.B
          }
        }

        //when (in.a.valid) {
        //  printf(p"Request\n")
        //  printf(p"  mwid               : 0x${Hexadecimal(mwidReg)}\n")
        //  printf(p"  wid                : 0x${Hexadecimal(wid)}\n")
        //  printf(p"  source             : 0x${Hexadecimal(in.a.bits.source)}\n")
        //  printf(p"  addr               : 0x${Hexadecimal(in.a.bits.address)}\n")
        //  printf(p"  opcode             : 0x${Hexadecimal(in.a.bits.opcode)}\n")
        //  printf(p"  size               : 0x${Hexadecimal(in.a.bits.size)}\n")
        //  printf(p"  allowFirst         : ${allowFirst}\n")
        //  printf(p"  deny_ready         : ${deny_ready}\n")
        //  printf(p"  a_first            : ${a_first}\n")
        //  printf(p"  allow              : ${allow}\n")
        //  printf(p"  interrupt(ir/iw)   : ${ir}/${iw}\n")
        //  printf(p"  bus error(er/ew)   : ${er}/${ew}\n")
        //}
        val out_d = Wire(in.d.bits.cloneType)
        out_d := out.d.bits

        // Deny can have unconditional priority, because the only out.d message possible is
        // ReleaseAck, because we waited for all A=>D traffic to complete. ReleaseAck is
        // single-beat, so it's safe to just arbitrate without counting the responses.
        in.d.valid := out.d.valid || denyValid
        out.d.ready := !denyValid && in.d.ready
        in.d.bits := Mux(denyValid, deny, out_d)

        // Track when a request is not allowed to be promoted toT
        if (edgeIn.manager.anySupportAcquireB) {
          val wSourceVec = Reg(Vec(edgeIn.client.endSourceId, Bool()))
          val aWOk = true.B//PriorityMux(sel, pmps.map(_.w(0)))
          val dWOk = wSourceVec(in.d.bits.source)
          val bypass = (edgeIn.manager.minLatency == 0).B && in.a.valid && in.a.bits.source === in.d.bits.source
          val d_grant = in.d.bits.opcode === TLMessages.Grant || in.d.bits.opcode === TLMessages.GrantData
          val dWHeld = Mux(bypass, aWOk, dWOk) holdUnless d_first

          when (d_grant && !dWHeld) {
            in.d.bits.param := TLPermissions.toB
          }

          when (in.a.fire && a_first) {
            wSourceVec(in.a.bits.source) := aWOk
          }

          edgeIn.client.unusedSources.foreach { id =>
            wSourceVec(id) := true.B
          }

          // Swallow GrantAcks
          val isMyId = mySinkId === in.e.bits.sink
          out.e.valid := in.e.valid && !isMyId
          in.e.ready := out.e.ready || isMyId

          when (in.e.fire && isMyId) {
            denyWait := false.B
          }
        }
      }
    }
  }
}



object WGChecker {
  val nextId = { var i = -1; () => { i+= 1; i} }
}
