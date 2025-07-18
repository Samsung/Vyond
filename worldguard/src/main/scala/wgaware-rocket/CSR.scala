// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard.rocket

import chisel3._
import chisel3.util.{BitPat, Cat, Fill, Mux1H, PopCount, PriorityMux, RegEnable, UIntToOH, Valid, log2Ceil, log2Up}
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property

import scala.collection.mutable.LinkedHashMap
import Instructions._
import CustomInstructions._

import worldguard._

class WGCSRDecodeIO(implicit p: Parameters) extends CSRDecodeIO()(p) {
  val wg_access_illegal = Output(Bool())
}

class WGCSRFileIO(hasBeu: Boolean)(implicit p: Parameters) extends CoreBundle
    with HasCoreParameters {
  val ungated_clock = Input(Clock())
  val interrupts = Input(new CoreInterrupts(hasBeu))
  val hartid = Input(UInt(hartIdLen.W))
  val rw = new Bundle {
    val addr = Input(UInt(CSR.ADDRSZ.W))
    val cmd = Input(Bits(CSR.SZ.W))
    val rdata = Output(Bits(xLen.W))
    val wdata = Input(Bits(xLen.W))
  }

  val decode = Vec(decodeWidth, new WGCSRDecodeIO)

  val csr_stall = Output(Bool()) // stall retire for wfi
  val rw_stall = Output(Bool()) // stall rw, rw will have no effect while rw_stall
  val eret = Output(Bool())
  val singleStep = Output(Bool())

  val status = Output(new MStatus())
  val hstatus = Output(new HStatus())
  val gstatus = Output(new MStatus())
  val ptbr = Output(new PTBR())
  val hgatp = Output(new PTBR())
  val vsatp = Output(new PTBR())
  val evec = Output(UInt(vaddrBitsExtended.W))
  val exception = Input(Bool())
  val retire = Input(UInt(log2Up(1+retireWidth).W))
  val cause = Input(UInt(xLen.W))
  val pc = Input(UInt(vaddrBitsExtended.W))
  val tval = Input(UInt(vaddrBitsExtended.W))
  val htval = Input(UInt(((maxSVAddrBits + 1) min xLen).W))
  val gva = Input(Bool())
  val time = Output(UInt(xLen.W))
  val fcsr_rm = Output(Bits(FPConstants.RM_SZ.W))
  val fcsr_flags = Flipped(Valid(Bits(FPConstants.FLAGS_SZ.W)))
  val set_fs_dirty = coreParams.haveFSDirty.option(Input(Bool()))
  val rocc_interrupt = Input(Bool())
  val interrupt = Output(Bool())
  val interrupt_cause = Output(UInt(xLen.W))
  val bp = Output(Vec(nBreakpoints, new BP))
  val pmp = Output(Vec(nPMPs, new PMP))
  val counters = Vec(nPerfCounters, new PerfCounterIO)
  val csrw_counter = Output(UInt(CSR.nCtr.W))
  val inhibit_cycle = Output(Bool())
  val inst = Input(Vec(retireWidth, UInt(iLen.W)))
  val trace = Output(Vec(retireWidth, new TracedInstruction))
  val mcontext = Output(UInt(coreParams.mcontextWidth.W))
  val scontext = Output(UInt(coreParams.scontextWidth.W))
  val fiom = Output(Bool())

  val vector = usingVector.option(new Bundle {
    val vconfig = Output(new VConfig())
    val vstart = Output(UInt(maxVLMax.log2.W))
    val vxrm = Output(UInt(2.W))
    val set_vs_dirty = Input(Bool())
    val set_vconfig = Flipped(Valid(new VConfig))
    val set_vstart = Flipped(Valid(vstart))
    val set_vxsat = Input(Bool())
  })

  val wid = Output(UInt(log2Ceil(p(NWorlds)).W))
}

class WGCSRFile(
  perfEventSets: EventSets = new EventSets(Seq()),
  customCSRs: Seq[CustomCSR] = Nil,
  roccCSRs: Seq[CustomCSR] = Nil,
  hasBeu: Boolean = true)(implicit p: Parameters)
    extends CoreModule()(p)
    with HasCoreParameters {
  val io = IO(new WGCSRFileIO(hasBeu) {
    val customCSRs = Vec(WGCSRFile.this.customCSRs.size, new CustomCSRIO)
    val roccCSRs = Vec(WGCSRFile.this.roccCSRs.size, new CustomCSRIO)
  })

  io.rw_stall := false.B

  val reset_mstatus = WireDefault(0.U.asTypeOf(new MStatus()))
  reset_mstatus.mpp := PRV.M.U
  reset_mstatus.prv := PRV.M.U
  reset_mstatus.xs := (if (usingRoCC) 3.U else 0.U)
  val reg_mstatus = RegInit(reset_mstatus)

  val new_prv = WireDefault(reg_mstatus.prv)
  reg_mstatus.prv := legalizePrivilege(new_prv)

  val nWorlds       = p(NWorlds)
  require(nWorlds > 0)
  val widWidth      = log2Ceil(nWorlds)
  val reg_mwid      = RegInit((nWorlds-1).U(widWidth.W))          // mwid = nWorld - 1
  val reg_mwidlist  = RegInit(~(1 << (nWorlds-1)).U(nWorlds.W))  // mwidlist = { x in Z || 0 <= x < mwid}   
  val reg_mlwid     = RegInit(~0.U(widWidth.W))                   // mlwid in mwidlist
  val reg_mwiddeleg = RegInit(0.U(nWorlds.W))                     // mwiddeleg in mwidlist \ {mlwid}
  val reg_slwid     = RegInit(~0.U(widWidth.W))                   // slwid in mwiddeleg

  val reset_dcsr = WireDefault(0.U.asTypeOf(new DCSR()))
  reset_dcsr.xdebugver := 1.U
  reset_dcsr.prv := PRV.M.U
  val reg_dcsr = RegInit(reset_dcsr)

  val (supported_interrupts, delegable_interrupts) = {
    val sup = Wire(new MIP)
    sup.usip := false.B
    sup.ssip := usingSupervisor.B
    sup.vssip := usingHypervisor.B
    sup.msip := true.B
    sup.utip := false.B
    sup.stip := usingSupervisor.B
    sup.vstip := usingHypervisor.B
    sup.mtip := true.B
    sup.ueip := false.B
    sup.seip := usingSupervisor.B
    sup.vseip := usingHypervisor.B
    sup.meip := true.B
    sup.sgeip := false.B
    sup.rocc := usingRoCC.B
    sup.debug := false.B
    sup.zero1 := false.B
    sup.lip foreach { _ := true.B }
    val supported_high_interrupts = if (io.interrupts.buserror.nonEmpty && !usingNMI) (BigInt(1) << CSR.busErrorIntCause).U else 0.U

    val del = WireDefault(sup)
    del.msip := false.B
    del.mtip := false.B
    del.meip := false.B

    (sup.asUInt | supported_high_interrupts, del.asUInt)
  }
  val delegable_base_exceptions = Seq(
    Causes.misaligned_fetch,
    Causes.fetch_page_fault,
    Causes.breakpoint,
    Causes.load_page_fault,
    Causes.store_page_fault,
    Causes.misaligned_load,
    Causes.misaligned_store,
    Causes.illegal_instruction,
    Causes.user_ecall,
  )
  val delegable_hypervisor_exceptions = Seq(
    Causes.virtual_supervisor_ecall,
    Causes.fetch_guest_page_fault,
    Causes.load_guest_page_fault,
    Causes.virtual_instruction,
    Causes.store_guest_page_fault,
  )
  val delegable_exceptions = (
    delegable_base_exceptions
    ++ (if (usingHypervisor) delegable_hypervisor_exceptions else Seq())
  ).map(1 << _).sum.U

  val hs_delegable_exceptions = Seq(
    Causes.misaligned_fetch,
    Causes.fetch_access,
    Causes.illegal_instruction,
    Causes.breakpoint,
    Causes.misaligned_load,
    Causes.load_access,
    Causes.misaligned_store,
    Causes.store_access,
    Causes.user_ecall,
    Causes.fetch_page_fault,
    Causes.load_page_fault,
    Causes.store_page_fault).map(1 << _).sum.U

  val (hs_delegable_interrupts, mideleg_always_hs) = {
    val always = WireDefault(0.U.asTypeOf(new MIP()))
    always.vssip := usingHypervisor.B
    always.vstip := usingHypervisor.B
    always.vseip := usingHypervisor.B

    val deleg = WireDefault(always)
    deleg.lip.foreach { _ := usingHypervisor.B }

    (deleg.asUInt, always.asUInt)
  }

  val reg_debug = RegInit(false.B)
  val reg_dpc = Reg(UInt(vaddrBitsExtended.W))
  val reg_dscratch0 = Reg(UInt(xLen.W))
  val reg_dscratch1 = (p(DebugModuleKey).map(_.nDscratch).getOrElse(1) > 1).option(Reg(UInt(xLen.W)))
  val reg_singleStepped = Reg(Bool())

  val reg_mcontext = (coreParams.mcontextWidth > 0).option(RegInit(0.U(coreParams.mcontextWidth.W)))
  val reg_scontext = (coreParams.scontextWidth > 0).option(RegInit(0.U(coreParams.scontextWidth.W)))

  val reg_tselect = Reg(UInt(log2Up(nBreakpoints).W))
  val reg_bp = Reg(Vec(1 << log2Up(nBreakpoints), new BP))
  val reg_pmp = Reg(Vec(nPMPs, new PMPReg))

  val reg_mie = Reg(UInt(xLen.W))
  val (reg_mideleg, read_mideleg) = {
    val reg = Reg(UInt(xLen.W))
    (reg, Mux(usingSupervisor.B, reg & delegable_interrupts | mideleg_always_hs, 0.U))
  }
  val (reg_medeleg, read_medeleg) = {
    val reg = Reg(UInt(xLen.W))
    (reg, Mux(usingSupervisor.B, reg & delegable_exceptions, 0.U))
  }
  val reg_mip = Reg(new MIP)
  val reg_mepc = Reg(UInt(vaddrBitsExtended.W))
  val reg_mcause = RegInit(0.U(xLen.W))
  val reg_mtval = Reg(UInt(vaddrBitsExtended.W))
  val reg_mtval2 = Reg(UInt(((maxSVAddrBits + 1) min xLen).W))
  val reg_mscratch = Reg(Bits(xLen.W))
  val mtvecWidth = paddrBits min xLen
  val reg_mtvec = mtvecInit match {
    case Some(addr) => RegInit(addr.U(mtvecWidth.W))
    case None => Reg(UInt(mtvecWidth.W))
  }

  val reset_mnstatus = WireDefault(0.U.asTypeOf(new MNStatus()))
  reset_mnstatus.mpp := PRV.M.U
  val reg_mnscratch = Reg(Bits(xLen.W))
  val reg_mnepc = Reg(UInt(vaddrBitsExtended.W))
  val reg_mncause = RegInit(0.U(xLen.W))
  val reg_mnstatus = RegInit(reset_mnstatus)
  val reg_rnmie = RegInit(true.B)
  val nmie = reg_rnmie

  val reg_menvcfg = RegInit(0.U.asTypeOf(new Envcfg))
  val reg_senvcfg = RegInit(0.U.asTypeOf(new Envcfg))
  val reg_henvcfg = RegInit(0.U.asTypeOf(new Envcfg))

  val delegable_counters = ((BigInt(1) << (nPerfCounters + CSR.firstHPM)) - 1).U
  val (reg_mcounteren, read_mcounteren) = {
    val reg = Reg(UInt(32.W))
    (reg, Mux(usingUser.B, reg & delegable_counters, 0.U))
  }
  val (reg_scounteren, read_scounteren) = {
    val reg = Reg(UInt(32.W))
    (reg, Mux(usingSupervisor.B, reg & delegable_counters, 0.U))
  }

  val (reg_hideleg, read_hideleg) = {
    val reg = Reg(UInt(xLen.W))
    (reg, Mux(usingHypervisor.B, reg & hs_delegable_interrupts, 0.U))
  }
  val (reg_hedeleg, read_hedeleg) = {
    val reg = Reg(UInt(xLen.W))
    (reg, Mux(usingHypervisor.B, reg & hs_delegable_exceptions, 0.U))
  }
  val hs_delegable_counters = delegable_counters
  val (reg_hcounteren, read_hcounteren) = {
    val reg = Reg(UInt(32.W))
    (reg, Mux(usingHypervisor.B, reg & hs_delegable_counters, 0.U))
  }
  val reg_hstatus = RegInit(0.U.asTypeOf(new HStatus))
  val reg_hgatp = Reg(new PTBR)
  val reg_htval = Reg(reg_mtval2.cloneType)
  val read_hvip = reg_mip.asUInt & hs_delegable_interrupts
  val read_hie = reg_mie & hs_delegable_interrupts

  val (reg_vstvec, read_vstvec) = {
    val reg = Reg(UInt(vaddrBitsExtended.W))
    (reg, formTVec(reg).sextTo(xLen))
  }
  val reg_vsstatus = Reg(new MStatus)
  val reg_vsscratch = Reg(Bits(xLen.W))
  val reg_vsepc = Reg(UInt(vaddrBitsExtended.W))
  val reg_vscause = Reg(Bits(xLen.W))
  val reg_vstval = Reg(UInt(vaddrBitsExtended.W))
  val reg_vsatp = Reg(new PTBR)

  val reg_sepc = Reg(UInt(vaddrBitsExtended.W))
  val reg_scause = Reg(Bits(xLen.W))
  val reg_stval = Reg(UInt(vaddrBitsExtended.W))
  val reg_sscratch = Reg(Bits(xLen.W))
  val reg_stvec = Reg(UInt((if (usingHypervisor) vaddrBitsExtended else vaddrBits).W))
  val reg_satp = Reg(new PTBR)
  val reg_wfi = withClock(io.ungated_clock) { RegInit(false.B) }

  val reg_fflags = Reg(UInt(5.W))
  val reg_frm = Reg(UInt(3.W))
  val reg_vconfig = usingVector.option(Reg(new VConfig))
  val reg_vstart = usingVector.option(Reg(UInt(maxVLMax.log2.W)))
  val reg_vxsat = usingVector.option(Reg(Bool()))
  val reg_vxrm = usingVector.option(Reg(UInt(io.vector.get.vxrm.getWidth.W)))

  val reg_mcountinhibit = RegInit(0.U((CSR.firstHPM + nPerfCounters).W))
  io.inhibit_cycle := reg_mcountinhibit(0)
  val reg_instret = WideCounter(64, io.retire, inhibit = reg_mcountinhibit(2))
  val reg_cycle = if (enableCommitLog) WideCounter(64, io.retire,     inhibit = reg_mcountinhibit(0))
    else withClock(io.ungated_clock) { WideCounter(64, !io.csr_stall, inhibit = reg_mcountinhibit(0)) }
  val reg_hpmevent = io.counters.map(c => RegInit(0.U(xLen.W)))
    (io.counters zip reg_hpmevent) foreach { case (c, e) => c.eventSel := e }
  val reg_hpmcounter = io.counters.zipWithIndex.map { case (c, i) =>
    WideCounter(CSR.hpmWidth, c.inc, reset = false, inhibit = reg_mcountinhibit(CSR.firstHPM+i)) }

  val mip = WireDefault(reg_mip)
  mip.lip := (io.interrupts.lip: Seq[Bool])
  mip.mtip := io.interrupts.mtip
  mip.msip := io.interrupts.msip
  mip.meip := io.interrupts.meip
  // seip is the OR of reg_mip.seip and the actual line from the PLIC
  io.interrupts.seip.foreach { mip.seip := reg_mip.seip || _ }
  // Simimlar sort of thing would apply if the PLIC had a VSEIP line:
  //io.interrupts.vseip.foreach { mip.vseip := reg_mip.vseip || _ }
  mip.rocc := io.rocc_interrupt
  val read_mip = mip.asUInt & supported_interrupts
  val read_hip = read_mip & hs_delegable_interrupts
  val high_interrupts = (if (usingNMI) 0.U else io.interrupts.buserror.map(_ << CSR.busErrorIntCause).getOrElse(0.U))

  val pending_interrupts = high_interrupts | (read_mip & reg_mie)
  val d_interrupts = io.interrupts.debug << CSR.debugIntCause
  val (nmi_interrupts, nmiFlag) = io.interrupts.nmi.map(nmi =>
    (((nmi.rnmi && reg_rnmie) << CSR.rnmiIntCause) |
    io.interrupts.buserror.map(_ << CSR.rnmiBEUCause).getOrElse(0.U),
    !io.interrupts.debug && nmi.rnmi && reg_rnmie)).getOrElse(0.U, false.B)
  val m_interrupts = Mux(nmie && (reg_mstatus.prv <= PRV.S.U || reg_mstatus.mie), ~(~pending_interrupts | read_mideleg), 0.U)
  val s_interrupts = Mux(nmie && (reg_mstatus.v || reg_mstatus.prv < PRV.S.U || (reg_mstatus.prv === PRV.S.U && reg_mstatus.sie)), pending_interrupts & read_mideleg & ~read_hideleg, 0.U)
  val vs_interrupts = Mux(nmie && (reg_mstatus.v && (reg_mstatus.prv < PRV.S.U || reg_mstatus.prv === PRV.S.U && reg_vsstatus.sie)), pending_interrupts & read_hideleg, 0.U)
  val (anyInterrupt, whichInterrupt) = chooseInterrupt(Seq(vs_interrupts, s_interrupts, m_interrupts, nmi_interrupts, d_interrupts))
  val interruptMSB = BigInt(1) << (xLen-1)
  val interruptCause = interruptMSB.U + (nmiFlag << (xLen-2)) + whichInterrupt
  io.interrupt := (anyInterrupt && !io.singleStep || reg_singleStepped) && !(reg_debug || io.status.cease)
  io.interrupt_cause := interruptCause
  io.bp := reg_bp take nBreakpoints
  io.mcontext := reg_mcontext.getOrElse(0.U)
  io.scontext := reg_scontext.getOrElse(0.U)
  io.fiom := (reg_mstatus.prv < PRV.M.U && reg_menvcfg.fiom) || (reg_mstatus.prv < PRV.S.U && reg_senvcfg.fiom) || (reg_mstatus.v && reg_henvcfg.fiom)
  io.pmp := reg_pmp.map(PMP(_))


  io.wid := Mux(reg_mstatus.prv === PRV.M.U, reg_mwid,
    Mux(reg_mstatus.prv === PRV.S.U, reg_mlwid, Mux(reg_mwiddeleg === 0.U, reg_mlwid, reg_slwid)))

  val isaMaskString =
    (if (usingMulDiv) "M" else "") +
    (if (usingAtomics) "A" else "") +
    (if (fLen >= 32) "F" else "") +
    (if (fLen >= 64) "D" else "") +
    (if (usingVector) "V" else "") +
    (if (usingCompressed) "C" else "")
  val isaString = (if (coreParams.useRVE) "E" else "I") +
    isaMaskString +
    (if (customIsaExt.isDefined || usingRoCC) "X" else "") +
    (if (usingSupervisor) "S" else "") +
    (if (usingHypervisor) "H" else "") +
    (if (usingUser) "U" else "")
  val isaMax = (BigInt(log2Ceil(xLen) - 4) << (xLen-2)) | isaStringToMask(isaString)
  val reg_misa = RegInit(isaMax.U)
  val read_mstatus = io.status.asUInt.extract(xLen-1,0)
  val read_mtvec = formTVec(reg_mtvec).padTo(xLen)
  val read_stvec = formTVec(reg_stvec).sextTo(xLen)

  val read_mapping = LinkedHashMap[Int,Bits](
    WGCSRs.tselect -> reg_tselect,
    WGCSRs.tdata1 -> reg_bp(reg_tselect).control.asUInt,
    WGCSRs.tdata2 -> reg_bp(reg_tselect).address.sextTo(xLen),
    WGCSRs.tdata3 -> reg_bp(reg_tselect).textra.asUInt,
    WGCSRs.misa -> reg_misa,
    WGCSRs.mstatus -> read_mstatus,
    WGCSRs.mtvec -> read_mtvec,
    WGCSRs.mip -> read_mip,
    WGCSRs.mie -> reg_mie,
    WGCSRs.mscratch -> reg_mscratch,
    WGCSRs.mepc -> readEPC(reg_mepc).sextTo(xLen),
    WGCSRs.mtval -> reg_mtval.sextTo(xLen),
    WGCSRs.mcause -> reg_mcause,
    WGCSRs.mhartid -> io.hartid)

  val debug_csrs = if (!usingDebug) LinkedHashMap() else LinkedHashMap[Int,Bits](
    WGCSRs.dcsr -> reg_dcsr.asUInt,
    WGCSRs.dpc -> readEPC(reg_dpc).sextTo(xLen),
    WGCSRs.dscratch0 -> reg_dscratch0.asUInt) ++
    reg_dscratch1.map(r => WGCSRs.dscratch1 -> r)

  val read_mnstatus = WireInit(0.U.asTypeOf(new MNStatus()))
  read_mnstatus.mpp := reg_mnstatus.mpp
  read_mnstatus.mpv := reg_mnstatus.mpv
  read_mnstatus.mie := reg_rnmie
  val nmi_csrs = if (!usingNMI) LinkedHashMap() else LinkedHashMap[Int,Bits](
    CustomCSRs.mnscratch -> reg_mnscratch,
    CustomCSRs.mnepc -> readEPC(reg_mnepc).sextTo(xLen),
    CustomCSRs.mncause -> reg_mncause,
    CustomCSRs.mnstatus -> read_mnstatus.asUInt)

  val context_csrs = LinkedHashMap[Int,Bits]() ++
    reg_mcontext.map(r => WGCSRs.mcontext -> r) ++
    reg_scontext.map(r => WGCSRs.scontext -> r)

  val read_fcsr = Cat(reg_frm, reg_fflags)
  val fp_csrs = LinkedHashMap[Int,Bits]() ++
    usingFPU.option(WGCSRs.fflags -> reg_fflags) ++
    usingFPU.option(WGCSRs.frm -> reg_frm) ++
    (usingFPU || usingVector).option(WGCSRs.fcsr -> read_fcsr)

  val read_vcsr = Cat(reg_vxrm.getOrElse(0.U), reg_vxsat.getOrElse(0.U))
  val vector_csrs = if (!usingVector) LinkedHashMap() else LinkedHashMap[Int,Bits](
    WGCSRs.vxsat -> reg_vxsat.get,
    WGCSRs.vxrm -> reg_vxrm.get,
    WGCSRs.vcsr -> read_vcsr,
    WGCSRs.vstart -> reg_vstart.get,
    WGCSRs.vtype -> reg_vconfig.get.vtype.asUInt,
    WGCSRs.vl -> reg_vconfig.get.vl,
    WGCSRs.vlenb -> (vLen / 8).U)

  val wg_csrs = LinkedHashMap[Int,Bits](
    WGCSRs.mwid -> reg_mwid,    // Temporary mwid csr for debugging
    WGCSRs.mlwid -> reg_mlwid,
    WGCSRs.slwid -> reg_slwid,
    WGCSRs.mwiddeleg -> reg_mwiddeleg
  )

  read_mapping ++= debug_csrs
  read_mapping ++= nmi_csrs
  read_mapping ++= context_csrs
  read_mapping ++= fp_csrs
  read_mapping ++= vector_csrs
  read_mapping ++= wg_csrs

  if (coreParams.haveBasicCounters) {
    read_mapping += WGCSRs.mcountinhibit -> reg_mcountinhibit
    read_mapping += WGCSRs.mcycle -> reg_cycle
    read_mapping += WGCSRs.minstret -> reg_instret

    for (((e, c), i) <- (reg_hpmevent.padTo(CSR.nHPM, 0.U)
                         zip reg_hpmcounter.map(x => x: UInt).padTo(CSR.nHPM, 0.U)).zipWithIndex) {
      read_mapping += (i + CSR.firstHPE) -> e // mhpmeventN
      read_mapping += (i + CSR.firstMHPC) -> c // mhpmcounterN
      read_mapping += (i + CSR.firstHPC) -> c // hpmcounterN
      if (xLen == 32) {
        read_mapping += (i + CSR.firstMHPCH) -> (c >> 32) // mhpmcounterNh
        read_mapping += (i + CSR.firstHPCH) -> (c >> 32) // hpmcounterNh
      }
    }

    if (usingUser) {
      read_mapping += WGCSRs.mcounteren -> read_mcounteren
    }
    read_mapping += WGCSRs.cycle -> reg_cycle
    read_mapping += WGCSRs.instret -> reg_instret

    if (xLen == 32) {
      read_mapping += WGCSRs.mcycleh -> (reg_cycle >> 32)
      read_mapping += WGCSRs.minstreth -> (reg_instret >> 32)
      read_mapping += WGCSRs.cycleh -> (reg_cycle >> 32)
      read_mapping += WGCSRs.instreth -> (reg_instret >> 32)
    }
  }

  if (usingUser) {
    read_mapping += WGCSRs.menvcfg -> reg_menvcfg.asUInt
    if (xLen == 32)
      read_mapping += WGCSRs.menvcfgh -> (reg_menvcfg.asUInt >> 32)
  }

  val sie_mask = {
    val sgeip_mask = WireInit(0.U.asTypeOf(new MIP))
    sgeip_mask.sgeip := true.B
    read_mideleg & ~(hs_delegable_interrupts | sgeip_mask.asUInt)
  }
  if (usingSupervisor) {
    val read_sie = reg_mie & sie_mask
    val read_sip = read_mip & sie_mask
    val read_sstatus = WireDefault(0.U.asTypeOf(new MStatus))
    read_sstatus.sd := io.status.sd
    read_sstatus.uxl := io.status.uxl
    read_sstatus.sd_rv32 := io.status.sd_rv32
    read_sstatus.mxr := io.status.mxr
    read_sstatus.sum := io.status.sum
    read_sstatus.xs := io.status.xs
    read_sstatus.fs := io.status.fs
    read_sstatus.vs := io.status.vs
    read_sstatus.spp := io.status.spp
    read_sstatus.spie := io.status.spie
    read_sstatus.sie := io.status.sie

    read_mapping += WGCSRs.sstatus -> (read_sstatus.asUInt)(xLen-1,0)
    read_mapping += WGCSRs.sip -> read_sip.asUInt
    read_mapping += WGCSRs.sie -> read_sie.asUInt
    read_mapping += WGCSRs.sscratch -> reg_sscratch
    read_mapping += WGCSRs.scause -> reg_scause
    read_mapping += WGCSRs.stval -> reg_stval.sextTo(xLen)
    read_mapping += WGCSRs.satp -> reg_satp.asUInt
    read_mapping += WGCSRs.sepc -> readEPC(reg_sepc).sextTo(xLen)
    read_mapping += WGCSRs.stvec -> read_stvec
    read_mapping += WGCSRs.scounteren -> read_scounteren
    read_mapping += WGCSRs.mideleg -> read_mideleg
    read_mapping += WGCSRs.medeleg -> read_medeleg
    read_mapping += WGCSRs.senvcfg -> reg_senvcfg.asUInt
  }

  val pmpCfgPerCSR = xLen / new PMPConfig().getWidth
  def pmpCfgIndex(i: Int) = (xLen / 32) * (i / pmpCfgPerCSR)
  if (reg_pmp.nonEmpty) {
    require(reg_pmp.size <= CSR.maxPMPs)
    val read_pmp = reg_pmp.padTo(CSR.maxPMPs, 0.U.asTypeOf(new PMP))
    for (i <- 0 until read_pmp.size by pmpCfgPerCSR)
      read_mapping += (WGCSRs.pmpcfg0 + pmpCfgIndex(i)) -> read_pmp.map(_.cfg).slice(i, i + pmpCfgPerCSR).asUInt
    for ((pmp, i) <- read_pmp.zipWithIndex)
      read_mapping += (WGCSRs.pmpaddr0 + i) -> pmp.readAddr
  }

  // implementation-defined CSRs
  def generateCustomCSR(csr: CustomCSR, csr_io: CustomCSRIO) = {
    require(csr.mask >= 0 && csr.mask.bitLength <= xLen)
    require(!read_mapping.contains(csr.id))
    val reg = csr.init.map(init => RegInit(init.U(xLen.W))).getOrElse(Reg(UInt(xLen.W)))
    val read = io.rw.cmd =/= CSR.N && io.rw.addr === csr.id.U
    csr_io.ren := read
    when (read && csr_io.stall) { io.rw_stall := true.B }
    read_mapping += csr.id -> reg
    reg
  }
  val reg_custom = customCSRs.zip(io.customCSRs).map(t => generateCustomCSR(t._1, t._2))
  val reg_rocc = roccCSRs.zip(io.roccCSRs).map(t => generateCustomCSR(t._1, t._2))

  if (usingHypervisor) {
    read_mapping += WGCSRs.mtinst -> 0.U
    read_mapping += WGCSRs.mtval2 -> reg_mtval2

    val read_hstatus = io.hstatus.asUInt.extract(xLen-1,0)

    read_mapping += WGCSRs.hstatus -> read_hstatus
    read_mapping += WGCSRs.hedeleg -> read_hedeleg
    read_mapping += WGCSRs.hideleg -> read_hideleg
    read_mapping += WGCSRs.hcounteren-> read_hcounteren
    read_mapping += WGCSRs.hgatp -> reg_hgatp.asUInt
    read_mapping += WGCSRs.hip -> read_hip
    read_mapping += WGCSRs.hie -> read_hie
    read_mapping += WGCSRs.hvip -> read_hvip
    read_mapping += WGCSRs.hgeie -> 0.U
    read_mapping += WGCSRs.hgeip -> 0.U
    read_mapping += WGCSRs.htval -> reg_htval
    read_mapping += WGCSRs.htinst -> 0.U
    read_mapping += WGCSRs.henvcfg -> reg_henvcfg.asUInt
    if (xLen == 32)
      read_mapping += WGCSRs.henvcfgh -> (reg_henvcfg.asUInt >> 32)

    val read_vsie = (read_hie & read_hideleg) >> 1
    val read_vsip = (read_hip & read_hideleg) >> 1
    val read_vsepc = readEPC(reg_vsepc).sextTo(xLen)
    val read_vstval = reg_vstval.sextTo(xLen)
    val read_vsstatus = io.gstatus.asUInt.extract(xLen-1,0)

    read_mapping += WGCSRs.vsstatus -> read_vsstatus
    read_mapping += WGCSRs.vsip -> read_vsip
    read_mapping += WGCSRs.vsie -> read_vsie
    read_mapping += WGCSRs.vsscratch -> reg_vsscratch
    read_mapping += WGCSRs.vscause -> reg_vscause
    read_mapping += WGCSRs.vstval -> read_vstval
    read_mapping += WGCSRs.vsatp -> reg_vsatp.asUInt
    read_mapping += WGCSRs.vsepc -> read_vsepc
    read_mapping += WGCSRs.vstvec -> read_vstvec
  }

  // mimpid, marchid, mvendorid, and mconfigptr are 0 unless overridden by customWGCSRs
  Seq(WGCSRs.mimpid, WGCSRs.marchid, WGCSRs.mvendorid, WGCSRs.mconfigptr).foreach(id => read_mapping.getOrElseUpdate(id, 0.U))

  val decoded_addr = {
    val addr = Cat(io.status.v, io.rw.addr)
    val pats = for (((k, _), i) <- read_mapping.zipWithIndex)
      yield (BitPat(k.U), (0 until read_mapping.size).map(j => BitPat((i == j).B)))
    val decoded = DecodeLogic(addr, Seq.fill(read_mapping.size)(X), pats)
    val unvirtualized_mapping = (for (((k, _), v) <- read_mapping zip decoded) yield k -> v.asBool).toMap

    for ((k, v) <- unvirtualized_mapping) yield k -> {
      val alt = CSR.mode(k) match {
        case PRV.S => unvirtualized_mapping.lift(k + (1 << CSR.modeLSB))
        case PRV.H => unvirtualized_mapping.lift(k - (1 << CSR.modeLSB))
        case _ => None
      }
      alt.map(Mux(reg_mstatus.v, _, v)).getOrElse(v)
    }
  }

  val wdata = readModifyWriteCSR(io.rw.cmd, io.rw.rdata, io.rw.wdata)

  val system_insn = io.rw.cmd === CSR.I
  val hlsv = Seq(HLV_B, HLV_BU, HLV_H, HLV_HU, HLV_W, HLV_WU, HLV_D, HSV_B, HSV_H, HSV_W, HSV_D, HLVX_HU, HLVX_WU)
  val decode_table = Seq(        ECALL->       List(Y,N,N,N,N,N,N,N,N),
                                 EBREAK->      List(N,Y,N,N,N,N,N,N,N),
                                 MRET->        List(N,N,Y,N,N,N,N,N,N),
                                 CEASE->       List(N,N,N,Y,N,N,N,N,N),
                                 WFI->         List(N,N,N,N,Y,N,N,N,N)) ++
    usingDebug.option(           DRET->        List(N,N,Y,N,N,N,N,N,N)) ++
    usingNMI.option(             MNRET->       List(N,N,Y,N,N,N,N,N,N)) ++
    coreParams.haveCFlush.option(CFLUSH_D_L1-> List(N,N,N,N,N,N,N,N,N)) ++
    usingSupervisor.option(      SRET->        List(N,N,Y,N,N,N,N,N,N)) ++
    usingVM.option(              SFENCE_VMA->  List(N,N,N,N,N,Y,N,N,N)) ++
    usingHypervisor.option(      HFENCE_VVMA-> List(N,N,N,N,N,N,Y,N,N)) ++
    usingHypervisor.option(      HFENCE_GVMA-> List(N,N,N,N,N,N,N,Y,N)) ++
    (if (usingHypervisor)        hlsv.map(_->  List(N,N,N,N,N,N,N,N,Y)) else Seq())
  val insn_call :: insn_break :: insn_ret :: insn_cease :: insn_wfi :: _ :: _ :: _ :: _ :: Nil = {
    val insn = ECALL.value.U | (io.rw.addr << 20)
    DecodeLogic(insn, decode_table(0)._2.map(x=>X), decode_table).map(system_insn && _.asBool)
  }

  for (io_dec <- io.decode) {
    val addr = io_dec.inst(31, 20)

    def decodeAny(m: LinkedHashMap[Int,Bits]): Bool = m.map { case(k: Int, _: Bits) => addr === k.U }.reduce(_||_)
    def decodeFast(s: Seq[Int]): Bool = DecodeLogic(addr, s.map(_.U), (read_mapping -- s).keys.toList.map(_.U))

    val _ :: is_break :: is_ret :: _ :: is_wfi :: is_sfence :: is_hfence_vvma :: is_hfence_gvma :: is_hlsv :: Nil =
      DecodeLogic(io_dec.inst, decode_table(0)._2.map(x=>X), decode_table).map(_.asBool)
    val is_counter = (addr.inRange(CSR.firstCtr.U, (CSR.firstCtr + CSR.nCtr).U) || addr.inRange(CSR.firstCtrH.U, (CSR.firstCtrH + CSR.nCtr).U))

    val allow_wfi = (!usingSupervisor).B || reg_mstatus.prv > PRV.S.U || !reg_mstatus.tw && (!reg_mstatus.v || !reg_hstatus.vtw)
    val allow_sfence_vma = (!usingVM).B || reg_mstatus.prv > PRV.S.U || !Mux(reg_mstatus.v, reg_hstatus.vtvm, reg_mstatus.tvm)
    val allow_hfence_vvma = (!usingHypervisor).B || !reg_mstatus.v && (reg_mstatus.prv >= PRV.S.U)
    val allow_hlsv = (!usingHypervisor).B || !reg_mstatus.v && (reg_mstatus.prv >= PRV.S.U || reg_hstatus.hu)
    val allow_sret = (!usingSupervisor).B || reg_mstatus.prv > PRV.S.U || !Mux(reg_mstatus.v, reg_hstatus.vtsr, reg_mstatus.tsr)
    val counter_addr = addr(log2Ceil(read_mcounteren.getWidth)-1, 0)
    val allow_counter = (reg_mstatus.prv > PRV.S.U || read_mcounteren(counter_addr)) &&
      (!usingSupervisor.B || reg_mstatus.prv >= PRV.S.U || read_scounteren(counter_addr)) &&
      (!usingHypervisor.B || !reg_mstatus.v || read_hcounteren(counter_addr))
    io_dec.fp_illegal := io.status.fs === 0.U || reg_mstatus.v && reg_vsstatus.fs === 0.U || !reg_misa('f'-'a')
    io_dec.vector_illegal := io.status.vs === 0.U || reg_mstatus.v && reg_vsstatus.vs === 0.U || !reg_misa('v'-'a')
    io_dec.fp_csr := decodeFast(fp_csrs.keys.toList)
    io_dec.rocc_illegal := io.status.xs === 0.U || reg_mstatus.v && reg_vsstatus.xs === 0.U || !reg_misa('x'-'a')
    val csr_addr_legal = reg_mstatus.prv >= CSR.mode(addr) ||
      usingHypervisor.B && !reg_mstatus.v && reg_mstatus.prv === PRV.S.U && CSR.mode(addr) === PRV.H.U
    val csr_exists = decodeAny(read_mapping)
    io_dec.read_illegal := !csr_addr_legal ||
      !csr_exists ||
      ((addr === WGCSRs.satp.U || addr === WGCSRs.hgatp.U) && !allow_sfence_vma) ||
      is_counter && !allow_counter ||
      decodeFast(debug_csrs.keys.toList) && !reg_debug ||
      decodeFast(vector_csrs.keys.toList) && io_dec.vector_illegal ||
      io_dec.fp_csr && io_dec.fp_illegal
    io_dec.write_illegal := addr(11,10).andR
    io_dec.write_flush := {
      val addr_m = addr | (PRV.M.U << CSR.modeLSB)
      !(addr_m >= WGCSRs.mscratch.U && addr_m <= WGCSRs.mtval.U)
    }
    io_dec.system_illegal := !csr_addr_legal && !is_hlsv ||
      is_wfi && !allow_wfi ||
      is_ret && !allow_sret ||
      is_ret && addr(10) && addr(7) && !reg_debug ||
      (is_sfence || is_hfence_gvma) && !allow_sfence_vma ||
      is_hfence_vvma && !allow_hfence_vvma ||
      is_hlsv && !allow_hlsv

    io_dec.virtual_access_illegal := reg_mstatus.v && csr_exists && (
      CSR.mode(addr) === PRV.H.U ||
      is_counter && read_mcounteren(counter_addr) && (!read_hcounteren(counter_addr) || !reg_mstatus.prv(0) && !read_scounteren(counter_addr)) ||
      CSR.mode(addr) === PRV.S.U && !reg_mstatus.prv(0) ||
      addr === WGCSRs.satp.U && reg_mstatus.prv(0) && reg_hstatus.vtvm)

    io_dec.virtual_system_illegal := reg_mstatus.v && (
      is_hfence_vvma ||
      is_hfence_gvma ||
      is_hlsv ||
      is_wfi && (!reg_mstatus.prv(0) || !reg_mstatus.tw && reg_hstatus.vtw) ||
      is_ret && CSR.mode(addr) === PRV.S.U && (!reg_mstatus.prv(0) || reg_hstatus.vtsr) ||
      is_sfence && (!reg_mstatus.prv(0) || reg_hstatus.vtvm))

    // Temporarily disabled to manually change the mwid
    io_dec.wg_access_illegal := false.B //(reg_mwidlist === 0.U && (addr === WGCSRs.mlwid.U || addr === WGCSRs.slwid.U)) ||
    //(reg_mwiddeleg === 0.U && (addr === WGCSRs.slwid.U)) ||
    //((addr === WGCSRs.mwiddeleg.U || addr === WGCSRs.mlwid.U) && reg_mstatus.prv =/= PRV.M.U) ||
    //(addr === WGCSRs.slwid.U && reg_mstatus.prv === PRV.U.U)

    val condition1 = (reg_mwidlist === 0.U && (addr === WGCSRs.mlwid.U || addr === WGCSRs.slwid.U))
    val condition2 = (reg_mwiddeleg === 0.U && (addr === WGCSRs.slwid.U))
    val condition3 = ((addr === WGCSRs.mwiddeleg.U || addr === WGCSRs.mlwid.U) && reg_mstatus.prv =/= PRV.M.U)
    val condition4 = (addr === WGCSRs.slwid.U && reg_mstatus.prv === PRV.U.U)
    //printf(p"Debug wg_access_illegal condition1: ${condition1} condition2: ${condition2} condition3: ${condition3} condition4: ${condition4}\n")
    //printf(p"    nWorlds   :  ${nWorlds}\n");
    //printf(p"    mwid      :  ${reg_mwid}\n");
    //printf(p"    mwidlist  :  ${reg_mwidlist}\n");
    //printf(p"    mlwid     :  ${reg_mlwid}\n");
    //printf(p"    mwiddeleg :  ${reg_mwiddeleg}\n");
    //printf(p"    slwid     :  ${reg_slwid}\n");
    //printf(p"    privilege :  ${reg_mstatus.prv}\n");
  }

  val cause =
    Mux(insn_call, Causes.user_ecall.U + Mux(reg_mstatus.prv(0) && reg_mstatus.v, PRV.H.U, reg_mstatus.prv),
    Mux[UInt](insn_break, Causes.breakpoint.U, io.cause))
  val cause_lsbs = cause(log2Ceil(1 + CSR.busErrorIntCause)-1, 0)
  val causeIsDebugInt = cause(xLen-1) && cause_lsbs === CSR.debugIntCause.U
  val causeIsDebugTrigger = !cause(xLen-1) && cause_lsbs === CSR.debugTriggerCause.U
  val causeIsDebugBreak = !cause(xLen-1) && insn_break && Cat(reg_dcsr.ebreakm, reg_dcsr.ebreakh, reg_dcsr.ebreaks, reg_dcsr.ebreaku)(reg_mstatus.prv)
  val trapToDebug = usingDebug.B && (reg_singleStepped || causeIsDebugInt || causeIsDebugTrigger || causeIsDebugBreak || reg_debug)
  val debugEntry = p(DebugModuleKey).map(_.debugEntry).getOrElse(BigInt(0x800))
  val debugException = p(DebugModuleKey).map(_.debugException).getOrElse(BigInt(0x808))
  val debugTVec = Mux(reg_debug, Mux(insn_break, debugEntry.U, debugException.U), debugEntry.U)
  val delegate = usingSupervisor.B && reg_mstatus.prv <= PRV.S.U && Mux(cause(xLen-1), read_mideleg(cause_lsbs), read_medeleg(cause_lsbs))
  val delegateVS = reg_mstatus.v && delegate && Mux(cause(xLen-1), read_hideleg(cause_lsbs), read_hedeleg(cause_lsbs))
  def mtvecBaseAlign = 2
  def mtvecInterruptAlign = {
    require(reg_mip.getWidth <= xLen)
    log2Ceil(xLen)
  }
  val notDebugTVec = {
    val base = Mux(delegate, Mux(delegateVS, read_vstvec, read_stvec), read_mtvec)
    val interruptOffset = cause(mtvecInterruptAlign-1, 0) << mtvecBaseAlign
    val interruptVec = Cat(base >> (mtvecInterruptAlign + mtvecBaseAlign), interruptOffset)
    val doVector = base(0) && cause(cause.getWidth-1) && (cause_lsbs >> mtvecInterruptAlign) === 0.U
    Mux(doVector, interruptVec, base >> mtvecBaseAlign << mtvecBaseAlign)
  }

  val causeIsRnmiInt = cause(xLen-1) && cause(xLen-2) && (cause_lsbs === CSR.rnmiIntCause.U || cause_lsbs === CSR.rnmiBEUCause.U)
  val causeIsRnmiBEU = cause(xLen-1) && cause(xLen-2) && cause_lsbs === CSR.rnmiBEUCause.U
  val causeIsNmi = causeIsRnmiInt
  val nmiTVecInt = io.interrupts.nmi.map(nmi => nmi.rnmi_interrupt_vector).getOrElse(0.U)
  val nmiTVecXcpt = io.interrupts.nmi.map(nmi => nmi.rnmi_exception_vector).getOrElse(0.U)
  val trapToNmiInt = usingNMI.B && causeIsNmi
  val trapToNmiXcpt = usingNMI.B && !nmie
  val trapToNmi = trapToNmiInt || trapToNmiXcpt
  val nmiTVec = (Mux(causeIsNmi, nmiTVecInt, nmiTVecXcpt)>>1)<<1

  val tvec = Mux(trapToDebug, debugTVec, Mux(trapToNmi, nmiTVec, notDebugTVec))
  io.evec := tvec
  io.ptbr := reg_satp
  io.hgatp := reg_hgatp
  io.vsatp := reg_vsatp
  io.eret := insn_call || insn_break || insn_ret
  io.singleStep := reg_dcsr.step && !reg_debug
  io.status := reg_mstatus
  io.status.sd := io.status.fs.andR || io.status.xs.andR || io.status.vs.andR
  io.status.debug := reg_debug
  io.status.isa := reg_misa
  io.status.uxl := (if (usingUser) log2Ceil(xLen) - 4 else 0).U
  io.status.sxl := (if (usingSupervisor) log2Ceil(xLen) - 4 else 0).U
  io.status.dprv := Mux(reg_mstatus.mprv && !reg_debug, reg_mstatus.mpp, reg_mstatus.prv)
  io.status.dv := reg_mstatus.v || Mux(reg_mstatus.mprv && !reg_debug, reg_mstatus.mpv, false.B)
  io.status.sd_rv32 := (xLen == 32).B && io.status.sd
  io.status.mpv := reg_mstatus.mpv
  io.status.gva := reg_mstatus.gva
  io.hstatus := reg_hstatus
  io.hstatus.vsxl := (if (usingSupervisor) log2Ceil(xLen) - 4 else 0).U
  io.gstatus := reg_vsstatus
  io.gstatus.sd := io.gstatus.fs.andR || io.gstatus.xs.andR || io.gstatus.vs.andR
  io.gstatus.uxl := (if (usingUser) log2Ceil(xLen) - 4 else 0).U
  io.gstatus.sd_rv32 := (xLen == 32).B && io.gstatus.sd

  val exception = insn_call || insn_break || io.exception
  assert(PopCount(insn_ret :: insn_call :: insn_break :: io.exception :: Nil) <= 1.U, "these conditions must be mutually exclusive")

  when (insn_wfi && !io.singleStep && !reg_debug) { reg_wfi := true.B }
  when (pending_interrupts.orR || io.interrupts.debug || exception) { reg_wfi := false.B }
  io.interrupts.nmi.map(nmi => when (nmi.rnmi) { reg_wfi := false.B } )

  when (io.retire(0) || exception) { reg_singleStepped := true.B }
  when (!io.singleStep) { reg_singleStepped := false.B }
  assert(!io.singleStep || io.retire <= 1.U)
  assert(!reg_singleStepped || io.retire === 0.U)

  val epc = formEPC(io.pc)
  val tval = Mux(insn_break, epc, io.tval)

  when (exception) {
    when (trapToDebug) {
      when (!reg_debug) {
        reg_mstatus.v := false.B
        reg_debug := true.B
        reg_dpc := epc
        reg_dcsr.cause := Mux(reg_singleStepped, 4.U, Mux(causeIsDebugInt, 3.U, Mux[UInt](causeIsDebugTrigger, 2.U, 1.U)))
        reg_dcsr.prv := trimPrivilege(reg_mstatus.prv)
        reg_dcsr.v := reg_mstatus.v
        new_prv := PRV.M.U
      }
    }.elsewhen (trapToNmiInt) {
      when (reg_rnmie) {
        reg_mstatus.v := false.B
        reg_mnstatus.mpv := reg_mstatus.v
        reg_rnmie := false.B
        reg_mnepc := epc
        reg_mncause := (BigInt(1) << (xLen-1)).U | Mux(causeIsRnmiBEU, 3.U, 2.U)
        reg_mnstatus.mpp := trimPrivilege(reg_mstatus.prv)
        new_prv := PRV.M.U
      }
    }.elsewhen (delegateVS && nmie) {
      reg_mstatus.v := true.B
      reg_vsstatus.spp := reg_mstatus.prv
      reg_vsepc := epc
      reg_vscause := Mux(cause(xLen-1), Cat(cause(xLen-1, 2), 1.U(2.W)), cause)
      reg_vstval := tval
      reg_vsstatus.spie := reg_vsstatus.sie
      reg_vsstatus.sie := false.B
      new_prv := PRV.S.U
    }.elsewhen (delegate && nmie) {
      reg_mstatus.v := false.B
      reg_hstatus.spvp := Mux(reg_mstatus.v, reg_mstatus.prv(0),reg_hstatus.spvp)
      reg_hstatus.gva := io.gva
      reg_hstatus.spv := reg_mstatus.v
      reg_sepc := epc
      reg_scause := cause
      reg_stval := tval
      reg_htval := io.htval
      reg_mstatus.spie := reg_mstatus.sie
      reg_mstatus.spp := reg_mstatus.prv
      reg_mstatus.sie := false.B
      new_prv := PRV.S.U
    }.otherwise {
      reg_mstatus.v := false.B
      reg_mstatus.mpv := reg_mstatus.v
      reg_mstatus.gva := io.gva
      reg_mepc := epc
      reg_mcause := cause
      reg_mtval := tval
      reg_mtval2 := io.htval
      reg_mstatus.mpie := reg_mstatus.mie
      reg_mstatus.mpp := trimPrivilege(reg_mstatus.prv)
      reg_mstatus.mie := false.B
      new_prv := PRV.M.U
    }
  }

  for (i <- 0 until supported_interrupts.getWidth) {
    val en = exception && (supported_interrupts & (BigInt(1) << i).U) =/= 0.U && cause === (BigInt(1) << (xLen - 1)).U + i.U
    val delegable = (delegable_interrupts & (BigInt(1) << i).U) =/= 0.U
    property.cover(en && !delegate, s"INTERRUPT_M_$i")
    property.cover(en && delegable && delegate, s"INTERRUPT_S_$i")
  }
  for (i <- 0 until xLen) {
    val supported_exceptions: BigInt = 0x8fe |
      (if (usingCompressed && !coreParams.misaWritable) 0 else 1) |
      (if (usingUser) 0x100 else 0) |
      (if (usingSupervisor) 0x200 else 0) |
      (if (usingVM) 0xb000 else 0)
    if (((supported_exceptions >> i) & 1) != 0) {
      val en = exception && cause === i.U
      val delegable = (delegable_exceptions & (BigInt(1) << i).U) =/= 0.U
      property.cover(en && !delegate, s"EXCEPTION_M_$i")
      property.cover(en && delegable && delegate, s"EXCEPTION_S_$i")
    }
  }

  when (insn_ret) {
    val ret_prv = WireInit(UInt(), DontCare)
    when (usingSupervisor.B && !io.rw.addr(9)) {
      when (!reg_mstatus.v) {
        reg_mstatus.sie := reg_mstatus.spie
        reg_mstatus.spie := true.B
        reg_mstatus.spp := PRV.U.U
        ret_prv := reg_mstatus.spp
        reg_mstatus.v := usingHypervisor.B && reg_hstatus.spv
        io.evec := readEPC(reg_sepc)
        reg_hstatus.spv := false.B
      }.otherwise {
        reg_vsstatus.sie := reg_vsstatus.spie
        reg_vsstatus.spie := true.B
        reg_vsstatus.spp := PRV.U.U
        ret_prv := reg_vsstatus.spp
        reg_mstatus.v := usingHypervisor.B
        io.evec := readEPC(reg_vsepc)
      }
    }.elsewhen (usingDebug.B && io.rw.addr(10) && io.rw.addr(7)) {
      ret_prv := reg_dcsr.prv
      reg_mstatus.v := usingHypervisor.B && reg_dcsr.v && reg_dcsr.prv <= PRV.S.U
      reg_debug := false.B
      io.evec := readEPC(reg_dpc)
    }.elsewhen (usingNMI.B && io.rw.addr(10) && !io.rw.addr(7)) {
      ret_prv := reg_mnstatus.mpp
      reg_mstatus.v := usingHypervisor.B && reg_mnstatus.mpv && reg_mnstatus.mpp <= PRV.S.U
      reg_rnmie := true.B
      io.evec := readEPC(reg_mnepc)
    }.otherwise {
      reg_mstatus.mie := reg_mstatus.mpie
      reg_mstatus.mpie := true.B
      reg_mstatus.mpp := legalizePrivilege(PRV.U.U)
      reg_mstatus.mpv := false.B
      ret_prv := reg_mstatus.mpp
      reg_mstatus.v := usingHypervisor.B && reg_mstatus.mpv && reg_mstatus.mpp <= PRV.S.U
      io.evec := readEPC(reg_mepc)
    }

    new_prv := ret_prv
    when (usingUser.B && ret_prv <= PRV.S.U) {
      reg_mstatus.mprv := false.B
    }
  }

  io.time := reg_cycle
  io.csr_stall := reg_wfi || io.status.cease
  io.status.cease := RegEnable(true.B, false.B, insn_cease)
  io.status.wfi := reg_wfi

  for ((io, reg) <- io.customCSRs zip reg_custom) {
    io.wen := false.B
    io.wdata := wdata
    io.value := reg
  }

  for ((io, reg) <- io.roccCSRs zip reg_rocc) {
    io.wen := false.B
    io.wdata := wdata
    io.value := reg
  }

  io.rw.rdata := Mux1H(for ((k, v) <- read_mapping) yield decoded_addr(k) -> v)

  // cover access to register
  val coverable_counters = read_mapping.filterNot { case (k, _) =>
    k >= CSR.firstHPC + nPerfCounters && k < CSR.firstHPC + CSR.nHPM
  }
  coverable_counters.foreach( {case (k, v) => {
    when (!k.U(11,10).andR) {  // Cover points for RW CSR registers
      property.cover(io.rw.cmd.isOneOf(CSR.W, CSR.S, CSR.C) && io.rw.addr===k.U, "CSR_access_"+k.toString, "Cover Accessing Core CSR field")
    } .otherwise { // Cover points for RO CSR registers
      property.cover(io.rw.cmd===CSR.R && io.rw.addr===k.U, "CSR_access_"+k.toString, "Cover Accessing Core CSR field")
    }
  }})

  val set_vs_dirty = WireDefault(io.vector.map(_.set_vs_dirty).getOrElse(false.B))
  io.vector.foreach { vio =>
    when (set_vs_dirty) {
      assert(reg_mstatus.vs > 0.U)
      when (reg_mstatus.v) { reg_vsstatus.vs := 3.U }
      reg_mstatus.vs := 3.U
    }
  }

  val set_fs_dirty = WireDefault(io.set_fs_dirty.getOrElse(false.B))
  if (coreParams.haveFSDirty) {
    when (set_fs_dirty) {
      assert(reg_mstatus.fs > 0.U)
      when (reg_mstatus.v) { reg_vsstatus.fs := 3.U }
      reg_mstatus.fs := 3.U
    }
  }

  io.fcsr_rm := reg_frm
  when (io.fcsr_flags.valid) {
    reg_fflags := reg_fflags | io.fcsr_flags.bits
    set_fs_dirty := true.B
  }

  io.vector.foreach { vio =>
    when (vio.set_vxsat) {
      reg_vxsat.get := true.B
      set_vs_dirty := true.B
    }
  }

  val csr_wen = io.rw.cmd.isOneOf(CSR.S, CSR.C, CSR.W) && !io.rw_stall
  io.csrw_counter := Mux(coreParams.haveBasicCounters.B && csr_wen && (io.rw.addr.inRange(WGCSRs.mcycle.U, (WGCSRs.mcycle + CSR.nCtr).U) || io.rw.addr.inRange(WGCSRs.mcycleh.U, (WGCSRs.mcycleh + CSR.nCtr).U)), UIntToOH(io.rw.addr(log2Ceil(CSR.nCtr+nPerfCounters)-1, 0)), 0.U)
  when (csr_wen) {
    val scause_mask = ((BigInt(1) << (xLen-1)) + 31).U /* only implement 5 LSBs and MSB */
    val satp_valid_modes = 0 +: (minPgLevels to pgLevels).map(new PTBR().pgLevelsToMode(_))

    when (decoded_addr(WGCSRs.mwiddeleg)) {
      val valid_candidate = reg_mwidlist & ~(1.U << reg_mlwid(widWidth, 0))   // mwiddeleg \in mwidlist - {mlwid}
      // If the mwidlist is not zero,
      // Illegal writes to the mwiddeleg sets mwiddeleg to the lowest legal value from the mwidlist
      when (reg_mwidlist =/= 0.U && ( ~valid_candidate & wdata) =/= 0.U) {
        reg_mwiddeleg := valid_candidate & (-valid_candidate).asUInt
      } .otherwise {
        reg_mwiddeleg := wdata
      }
    }
    
    when (decoded_addr(WGCSRs.mlwid)) {
      // At reset, and illegal writes to the mlwid, mlwid is set to the loweest value from the mwidlist
      when (reg_mwidlist =/= 0.U && (reg_mwidlist & (1.U << (wdata(widWidth, 0)))) === 0.U) {
        reg_mlwid := reg_mwidlist & (-reg_mwidlist).asUInt
      } .otherwise {
        reg_mlwid := wdata
      }
    }
    
    // Temporary mwid csr for debugging
    when (decoded_addr(WGCSRs.mwid)) {
      reg_mwid := wdata
    }
   
    when (decoded_addr(WGCSRs.slwid)) {
      // Once the mwiddeleg is not zero,
      // Illegal writes to the slwid sets slwid to the lowest legal value from the mwidlist
      when (reg_mwiddeleg =/= 0.U && (reg_mwiddeleg & (1.U << (wdata(widWidth, 0)))) === 0.U) {
        reg_slwid := reg_mwiddeleg & (-reg_mwiddeleg).asUInt
      } .otherwise {
        reg_slwid := wdata
      }
    }

    when (decoded_addr(WGCSRs.mstatus)) {
      val new_mstatus = wdata.asTypeOf(new MStatus())
      reg_mstatus.mie := new_mstatus.mie
      reg_mstatus.mpie := new_mstatus.mpie

      if (usingUser) {
        reg_mstatus.mprv := new_mstatus.mprv
        reg_mstatus.mpp := legalizePrivilege(new_mstatus.mpp)
        if (usingSupervisor) {
          reg_mstatus.spp := new_mstatus.spp
          reg_mstatus.spie := new_mstatus.spie
          reg_mstatus.sie := new_mstatus.sie
          reg_mstatus.tw := new_mstatus.tw
          reg_mstatus.tsr := new_mstatus.tsr
        }
        if (usingVM) {
          reg_mstatus.mxr := new_mstatus.mxr
          reg_mstatus.sum := new_mstatus.sum
          reg_mstatus.tvm := new_mstatus.tvm
        }
        if (usingHypervisor) {
          reg_mstatus.mpv := new_mstatus.mpv
          reg_mstatus.gva := new_mstatus.gva
        }
      }

      if (usingSupervisor || usingFPU) reg_mstatus.fs := formFS(new_mstatus.fs)
      reg_mstatus.vs := formVS(new_mstatus.vs)
    }
    when (decoded_addr(WGCSRs.misa)) {
      val mask = isaStringToMask(isaMaskString).U(xLen.W)
      val f = wdata('f' - 'a')
      // suppress write if it would cause the next fetch to be misaligned
      when (!usingCompressed.B || !io.pc(1) || wdata('c' - 'a')) {
        if (coreParams.misaWritable)
          reg_misa := ~(~wdata | (!f << ('d' - 'a'))) & mask | reg_misa & ~mask
      }
    }
    when (decoded_addr(WGCSRs.mip)) {
      // MIP should be modified based on the value in reg_mip, not the value
      // in read_mip, since read_mip.seip is the OR of reg_mip.seip and
      // io.interrupts.seip.  We don't want the value on the PLIC line to
      // inadvertently be OR'd into read_mip.seip.
      val new_mip = readModifyWriteCSR(io.rw.cmd, reg_mip.asUInt, io.rw.wdata).asTypeOf(new MIP)
      if (usingSupervisor) {
        reg_mip.ssip := new_mip.ssip
        reg_mip.stip := new_mip.stip
        reg_mip.seip := new_mip.seip
      }
      if (usingHypervisor) {
        reg_mip.vssip := new_mip.vssip
      }
    }
    when (decoded_addr(WGCSRs.mie))      { reg_mie := wdata & supported_interrupts }
    when (decoded_addr(WGCSRs.mepc))     { reg_mepc := formEPC(wdata) }
    when (decoded_addr(WGCSRs.mscratch)) { reg_mscratch := wdata }
    if (mtvecWritable)
      when (decoded_addr(WGCSRs.mtvec))  { reg_mtvec := wdata }
    when (decoded_addr(WGCSRs.mcause))   { reg_mcause := wdata & ((BigInt(1) << (xLen-1)) + (BigInt(1) << whichInterrupt.getWidth) - 1).U }
    when (decoded_addr(WGCSRs.mtval))    { reg_mtval := wdata }

    if (usingNMI) {
      val new_mnstatus = wdata.asTypeOf(new MNStatus())
      when (decoded_addr(CustomCSRs.mnscratch)) { reg_mnscratch := wdata }
      when (decoded_addr(CustomCSRs.mnepc))     { reg_mnepc := formEPC(wdata) }
      when (decoded_addr(CustomCSRs.mncause))   { reg_mncause := wdata & ((BigInt(1) << (xLen-1)) + BigInt(3)).U }
      when (decoded_addr(CustomCSRs.mnstatus))  {
        reg_mnstatus.mpp := legalizePrivilege(new_mnstatus.mpp)
        reg_mnstatus.mpv := usingHypervisor.B && new_mnstatus.mpv
        reg_rnmie := reg_rnmie | new_mnstatus.mie  // mnie bit settable but not clearable from software
      }
    }

    for (((e, c), i) <- (reg_hpmevent zip reg_hpmcounter).zipWithIndex) {
      writeCounter(i + CSR.firstMHPC, c, wdata)
      when (decoded_addr(i + CSR.firstHPE)) { e := perfEventSets.maskEventSelector(wdata) }
    }
    if (coreParams.haveBasicCounters) {
      when (decoded_addr(WGCSRs.mcountinhibit)) { reg_mcountinhibit := wdata & ~2.U(xLen.W) }  // mcountinhibit bit [1] is tied zero
      writeCounter(WGCSRs.mcycle, reg_cycle, wdata)
      writeCounter(WGCSRs.minstret, reg_instret, wdata)
    }

    if (usingFPU) {
      when (decoded_addr(WGCSRs.fflags)) { set_fs_dirty := true.B; reg_fflags := wdata }
      when (decoded_addr(WGCSRs.frm))    { set_fs_dirty := true.B; reg_frm := wdata }
      when (decoded_addr(WGCSRs.fcsr)) {
        set_fs_dirty := true.B
        reg_fflags := wdata
        reg_frm := wdata >> reg_fflags.getWidth
      }
    }
    if (usingDebug) {
      when (decoded_addr(WGCSRs.dcsr)) {
        val new_dcsr = wdata.asTypeOf(new DCSR())
        reg_dcsr.step := new_dcsr.step
        reg_dcsr.ebreakm := new_dcsr.ebreakm
        if (usingSupervisor) reg_dcsr.ebreaks := new_dcsr.ebreaks
        if (usingUser) reg_dcsr.ebreaku := new_dcsr.ebreaku
        if (usingUser) reg_dcsr.prv := legalizePrivilege(new_dcsr.prv)
        if (usingHypervisor) reg_dcsr.v := new_dcsr.v
      }
      when (decoded_addr(WGCSRs.dpc))      { reg_dpc := formEPC(wdata) }
      when (decoded_addr(WGCSRs.dscratch0)) { reg_dscratch0 := wdata }
      reg_dscratch1.foreach { r =>
        when (decoded_addr(WGCSRs.dscratch1)) { r := wdata }
      }
    }
    if (usingSupervisor) {
      when (decoded_addr(WGCSRs.sstatus)) {
        val new_sstatus = wdata.asTypeOf(new MStatus())
        reg_mstatus.sie := new_sstatus.sie
        reg_mstatus.spie := new_sstatus.spie
        reg_mstatus.spp := new_sstatus.spp
        reg_mstatus.fs := formFS(new_sstatus.fs)
        reg_mstatus.vs := formVS(new_sstatus.vs)
        if (usingVM) {
          reg_mstatus.mxr := new_sstatus.mxr
          reg_mstatus.sum := new_sstatus.sum
        }
      }
      when (decoded_addr(WGCSRs.sip)) {
        val new_sip = ((read_mip & ~read_mideleg) | (wdata & read_mideleg)).asTypeOf(new MIP())
        reg_mip.ssip := new_sip.ssip
      }
      when (decoded_addr(WGCSRs.satp)) {
        if (usingVM) {
          val new_satp = wdata.asTypeOf(new PTBR())
          when (new_satp.mode.isOneOf(satp_valid_modes.map(_.U))) {
            reg_satp.mode := new_satp.mode & satp_valid_modes.reduce(_|_).U
            reg_satp.ppn := new_satp.ppn(ppnBits-1,0)
            if (asIdBits > 0) reg_satp.asid := new_satp.asid(asIdBits-1,0)
          }
        }
      }
      when (decoded_addr(WGCSRs.sie))      { reg_mie := (reg_mie & ~sie_mask) | (wdata & sie_mask) }
      when (decoded_addr(WGCSRs.sscratch)) { reg_sscratch := wdata }
      when (decoded_addr(WGCSRs.sepc))     { reg_sepc := formEPC(wdata) }
      when (decoded_addr(WGCSRs.stvec))    { reg_stvec := wdata }
      when (decoded_addr(WGCSRs.scause))   { reg_scause := wdata & scause_mask }
      when (decoded_addr(WGCSRs.stval))    { reg_stval := wdata }
      when (decoded_addr(WGCSRs.mideleg))  { reg_mideleg := wdata }
      when (decoded_addr(WGCSRs.medeleg))  { reg_medeleg := wdata }
      when (decoded_addr(WGCSRs.scounteren)) { reg_scounteren := wdata }
      when (decoded_addr(WGCSRs.senvcfg))    { reg_senvcfg.write(wdata) }
    }

    if (usingHypervisor) {
      when (decoded_addr(WGCSRs.hstatus)) {
        val new_hstatus = wdata.asTypeOf(new HStatus())
        reg_hstatus.gva := new_hstatus.gva
        reg_hstatus.spv := new_hstatus.spv
        reg_hstatus.spvp := new_hstatus.spvp
        reg_hstatus.hu := new_hstatus.hu
        reg_hstatus.vtvm := new_hstatus.vtvm
        reg_hstatus.vtw := new_hstatus.vtw
        reg_hstatus.vtsr := new_hstatus.vtsr
        reg_hstatus.vsxl := new_hstatus.vsxl
      }
      when (decoded_addr(WGCSRs.hideleg))  { reg_hideleg := wdata }
      when (decoded_addr(WGCSRs.hedeleg))  { reg_hedeleg := wdata }
      when (decoded_addr(WGCSRs.hgatp)) {
        val new_hgatp = wdata.asTypeOf(new PTBR())
        val valid_modes = 0 +: (minPgLevels to pgLevels).map(new_hgatp.pgLevelsToMode(_))
        when (new_hgatp.mode.isOneOf(valid_modes.map(_.U))) {
          reg_hgatp.mode := new_hgatp.mode & valid_modes.reduce(_|_).U
        }
        reg_hgatp.ppn := Cat(new_hgatp.ppn(ppnBits-1,2), 0.U(2.W))
        if (vmIdBits > 0) reg_hgatp.asid := new_hgatp.asid(vmIdBits-1,0)
      }
      when (decoded_addr(WGCSRs.hip)) {
        val new_hip = ((read_mip & ~hs_delegable_interrupts) | (wdata & hs_delegable_interrupts)).asTypeOf(new MIP())
        reg_mip.vssip := new_hip.vssip
      }
      when (decoded_addr(WGCSRs.hie)) { reg_mie := (reg_mie & ~hs_delegable_interrupts) | (wdata & hs_delegable_interrupts) }
      when (decoded_addr(WGCSRs.hvip)) {
        val new_sip = ((read_mip & ~hs_delegable_interrupts) | (wdata & hs_delegable_interrupts)).asTypeOf(new MIP())
        reg_mip.vssip := new_sip.vssip
        reg_mip.vstip := new_sip.vstip
        reg_mip.vseip := new_sip.vseip
      }
      when (decoded_addr(WGCSRs.hcounteren)) { reg_hcounteren := wdata }
      when (decoded_addr(WGCSRs.htval))      { reg_htval := wdata }
      when (decoded_addr(WGCSRs.mtval2))     { reg_mtval2 := wdata }

      when (decoded_addr(WGCSRs.vsstatus)) {
        val new_vsstatus = wdata.asTypeOf(new MStatus())
        reg_vsstatus.sie := new_vsstatus.sie
        reg_vsstatus.spie := new_vsstatus.spie
        reg_vsstatus.spp := new_vsstatus.spp
        reg_vsstatus.mxr := new_vsstatus.mxr
        reg_vsstatus.sum := new_vsstatus.sum
        reg_vsstatus.fs := formFS(new_vsstatus.fs)
        reg_vsstatus.vs := formVS(new_vsstatus.vs)
      }
      when (decoded_addr(WGCSRs.vsip)) {
        val new_vsip = ((read_hip & ~read_hideleg) | ((wdata << 1) & read_hideleg)).asTypeOf(new MIP())
        reg_mip.vssip := new_vsip.vssip
      }
      when (decoded_addr(WGCSRs.vsatp)) {
        val new_vsatp = wdata.asTypeOf(new PTBR())
        val mode_ok = new_vsatp.mode.isOneOf(satp_valid_modes.map(_.U))
        when (mode_ok) {
          reg_vsatp.mode := new_vsatp.mode & satp_valid_modes.reduce(_|_).U
        }
        when (mode_ok || !reg_mstatus.v) {
          reg_vsatp.ppn := new_vsatp.ppn(vpnBits.min(new_vsatp.ppn.getWidth)-1,0)
          if (asIdBits > 0) reg_vsatp.asid := new_vsatp.asid(asIdBits-1,0)
        }
      }
      when (decoded_addr(WGCSRs.vsie))      { reg_mie := (reg_mie & ~read_hideleg) | ((wdata << 1) & read_hideleg) }
      when (decoded_addr(WGCSRs.vsscratch)) { reg_vsscratch := wdata }
      when (decoded_addr(WGCSRs.vsepc))     { reg_vsepc := formEPC(wdata) }
      when (decoded_addr(WGCSRs.vstvec))    { reg_vstvec := wdata }
      when (decoded_addr(WGCSRs.vscause))   { reg_vscause := wdata & scause_mask }
      when (decoded_addr(WGCSRs.vstval))    { reg_vstval := wdata }
      when (decoded_addr(WGCSRs.henvcfg))   { reg_henvcfg.write(wdata) }
    }
    if (usingUser) {
      when (decoded_addr(WGCSRs.mcounteren)) { reg_mcounteren := wdata }
      when (decoded_addr(WGCSRs.menvcfg))    { reg_menvcfg.write(wdata) }
    }
    if (nBreakpoints > 0) {
      when (decoded_addr(WGCSRs.tselect)) { reg_tselect := wdata }

      for ((bp, i) <- reg_bp.zipWithIndex) {
        when (i.U === reg_tselect && (!bp.control.dmode || reg_debug)) {
          when (decoded_addr(WGCSRs.tdata2)) { bp.address := wdata }
          when (decoded_addr(WGCSRs.tdata3)) {
            if (coreParams.mcontextWidth > 0) {
              bp.textra.mselect := wdata(bp.textra.mselectPos)
              bp.textra.mvalue  := wdata >> bp.textra.mvaluePos
            }
            if (coreParams.scontextWidth > 0) {
              bp.textra.sselect := wdata(bp.textra.sselectPos)
              bp.textra.svalue  := wdata >> bp.textra.svaluePos
            }
          }
          when (decoded_addr(WGCSRs.tdata1)) {
            bp.control := wdata.asTypeOf(bp.control)

            val prevChain = if (i == 0) false.B else reg_bp(i-1).control.chain
            val prevDMode = if (i == 0) false.B else reg_bp(i-1).control.dmode
            val nextChain = if (i >= nBreakpoints-1) true.B else reg_bp(i+1).control.chain
            val nextDMode = if (i >= nBreakpoints-1) true.B else reg_bp(i+1).control.dmode
            val newBPC = readModifyWriteCSR(io.rw.cmd, bp.control.asUInt, io.rw.wdata).asTypeOf(bp.control)
            val dMode = newBPC.dmode && reg_debug && (prevDMode || !prevChain)
            bp.control.dmode := dMode
            when (dMode || (newBPC.action > 1.U)) { bp.control.action := newBPC.action }.otherwise { bp.control.action := 0.U }
            bp.control.chain := newBPC.chain && !(prevChain || nextChain) && (dMode || !nextDMode)
          }
        }
      }
    }
    reg_mcontext.foreach { r => when (decoded_addr(WGCSRs.mcontext)) { r := wdata }}
    reg_scontext.foreach { r => when (decoded_addr(WGCSRs.scontext)) { r := wdata }}
    if (reg_pmp.nonEmpty) for (((pmp, next), i) <- (reg_pmp zip (reg_pmp.tail :+ reg_pmp.last)).zipWithIndex) {
      require(xLen % pmp.cfg.getWidth == 0)
      when (decoded_addr(WGCSRs.pmpcfg0 + pmpCfgIndex(i)) && !pmp.cfgLocked) {
        val newCfg = (wdata >> ((i * pmp.cfg.getWidth) % xLen)).asTypeOf(new PMPConfig())
        pmp.cfg := newCfg
        // disallow unreadable but writable PMPs
        pmp.cfg.w := newCfg.w && newCfg.r
        // can't select a=NA4 with coarse-grained PMPs
        if (pmpGranularity.log2 > PMP.lgAlign)
          pmp.cfg.a := Cat(newCfg.a(1), newCfg.a.orR)
      }
      when (decoded_addr(WGCSRs.pmpaddr0 + i) && !pmp.addrLocked(next)) {
        pmp.addr := wdata
      }
    }
    def writeCustomCSR(io: CustomCSRIO, csr: CustomCSR, reg: UInt) = {
      val mask = csr.mask.U(xLen.W)
      when (decoded_addr(csr.id)) {
        reg := (wdata & mask) | (reg & ~mask)
        io.wen := true.B
      }
    }
    for ((io, csr, reg) <- (io.customCSRs, customCSRs, reg_custom).zipped) {
      writeCustomCSR(io, csr, reg)
    }
    for ((io, csr, reg) <- (io.roccCSRs, roccCSRs, reg_rocc).zipped) {
      writeCustomCSR(io, csr, reg)
    }
    if (usingVector) {
      when (decoded_addr(WGCSRs.vstart)) { set_vs_dirty := true.B; reg_vstart.get := wdata }
      when (decoded_addr(WGCSRs.vxrm))   { set_vs_dirty := true.B; reg_vxrm.get := wdata }
      when (decoded_addr(WGCSRs.vxsat))  { set_vs_dirty := true.B; reg_vxsat.get := wdata }
      when (decoded_addr(WGCSRs.vcsr))   {
        set_vs_dirty := true.B
        reg_vxsat.get := wdata
        reg_vxrm.get := wdata >> 1
      }
    }
  }

  def setCustomCSR(io: CustomCSRIO, csr: CustomCSR, reg: UInt) = {
    val mask = csr.mask.U(xLen.W)
    when (io.set) {
      reg := (io.sdata & mask) | (reg & ~mask)
    }
  }
  for ((io, csr, reg) <- (io.customCSRs, customCSRs, reg_custom).zipped) {
    setCustomCSR(io, csr, reg)
  }
  for ((io, csr, reg) <- (io.roccCSRs, roccCSRs, reg_rocc).zipped) {
    setCustomCSR(io, csr, reg)
  }

  io.vector.map { vio =>
    when (vio.set_vconfig.valid) {
      // user of CSRFile is responsible for set_vs_dirty in this case
      assert(vio.set_vconfig.bits.vl <= vio.set_vconfig.bits.vtype.vlMax)
      reg_vconfig.get := vio.set_vconfig.bits
    }
    when (vio.set_vstart.valid) {
      set_vs_dirty := true.B
      reg_vstart.get := vio.set_vstart.bits
    }
    vio.vstart := reg_vstart.get
    vio.vconfig := reg_vconfig.get
    vio.vxrm := reg_vxrm.get

    when (reset.asBool) {
      reg_vconfig.get.vl := 0.U
      reg_vconfig.get.vtype := 0.U.asTypeOf(new VType)
      reg_vconfig.get.vtype.vill := true.B
    }
  }

  //when (reset.asBool) {
  //  when (reg_mwidlist =/= 0.U) {
  //    reg_mlwid := reg_mwidlist & (-reg_mwidlist).asUInt
  //  } .otherwise {
  //    reg_mlwid := 0.U
  //  }
  //}

  when(reset.asBool) {
    reg_satp.mode  := 0.U
    reg_vsatp.mode := 0.U
    reg_hgatp.mode := 0.U
  }
  if (!usingVM) {
    reg_satp.mode := 0.U
    reg_satp.ppn  := 0.U
    reg_satp.asid := 0.U
  }
  if (!usingHypervisor) {
    reg_vsatp.mode := 0.U
    reg_vsatp.ppn  := 0.U
    reg_vsatp.asid := 0.U
    reg_hgatp.mode := 0.U
    reg_hgatp.ppn  := 0.U
    reg_hgatp.asid := 0.U
  }
  if (!(asIdBits > 0)) {
    reg_satp.asid  := 0.U
    reg_vsatp.asid := 0.U
  }
  if (!(vmIdBits > 0)) {
    reg_hgatp.asid := 0.U
  }
  reg_vsstatus.xs := (if (usingRoCC) 3.U else 0.U)

  if (nBreakpoints <= 1) reg_tselect := 0.U
  for (bpc <- reg_bp map {_.control}) {
    bpc.ttype := bpc.tType.U
    bpc.maskmax := bpc.maskMax.U
    bpc.reserved := 0.U
    bpc.zero := 0.U
    bpc.h := false.B
    if (!usingSupervisor) bpc.s := false.B
    if (!usingUser) bpc.u := false.B
    if (!usingSupervisor && !usingUser) bpc.m := true.B
    when (reset.asBool) {
      bpc.action := 0.U
      bpc.dmode := false.B
      bpc.chain := false.B
      bpc.r := false.B
      bpc.w := false.B
      bpc.x := false.B
    }
  }
  for (bpx <- reg_bp map {_.textra}) {
    if (coreParams.mcontextWidth == 0) bpx.mselect := false.B
    if (coreParams.scontextWidth == 0) bpx.sselect := false.B
  }
  for (bp <- reg_bp drop nBreakpoints)
    bp := 0.U.asTypeOf(new BP())
  for (pmp <- reg_pmp) {
    pmp.cfg.res := 0.U
    when (reset.asBool) { pmp.reset() }
  }

  for (((t, insn), i) <- (io.trace zip io.inst).zipWithIndex) {
    t.exception := io.retire >= i.U && exception
    t.valid := io.retire > i.U || t.exception
    t.insn := insn
    t.iaddr := io.pc
    t.priv := Cat(reg_debug, reg_mstatus.prv)
    t.cause := cause
    t.interrupt := cause(xLen-1)
    t.tval := io.tval
    t.wdata.foreach(_ := DontCare)
  }

  def chooseInterrupt(masksIn: Seq[UInt]): (Bool, UInt) = {
    val nonstandard = supported_interrupts.getWidth-1 to 12 by -1
    // MEI, MSI, MTI,  SEI, SSI, STI, VSEI, VSSI, VSTI, UEI, USI, UTI
    val standard = Seq(11, 3, 7, 9, 1, 5, 10, 2, 6, 8, 0, 4)
    val priority = nonstandard ++ standard
    val masks = masksIn.reverse
    val any = masks.flatMap(m => priority.filter(_ < m.getWidth).map(i => m(i))).reduce(_||_)
    val which = PriorityMux(masks.flatMap(m => priority.filter(_ < m.getWidth).map(i => (m(i), i.U))))
    (any, which)
  }

  def readModifyWriteCSR(cmd: UInt, rdata: UInt, wdata: UInt) = {
    (Mux(cmd(1), rdata, 0.U) | wdata) & ~Mux(cmd(1,0).andR, wdata, 0.U)
  }

  def legalizePrivilege(priv: UInt): UInt =
    if (usingSupervisor) Mux(priv === PRV.H.U, PRV.U.U, priv)
    else if (usingUser) Fill(2, priv(0))
    else PRV.M.U

  def trimPrivilege(priv: UInt): UInt =
    if (usingSupervisor) priv
    else legalizePrivilege(priv)

  def writeCounter(lo: Int, ctr: WideCounter, wdata: UInt) = {
    if (xLen == 32) {
      val hi = lo + WGCSRs.mcycleh - WGCSRs.mcycle
      when (decoded_addr(lo)) { ctr := Cat(ctr(ctr.getWidth-1, 32), wdata) }
      when (decoded_addr(hi)) { ctr := Cat(wdata(ctr.getWidth-33, 0), ctr(31, 0)) }
    } else {
      when (decoded_addr(lo)) { ctr := wdata(ctr.getWidth-1, 0) }
    }
  }
  def formEPC(x: UInt) = ~(~x | (if (usingCompressed) 1.U else 3.U))
  def readEPC(x: UInt) = ~(~x | Mux(reg_misa('c' - 'a'), 1.U, 3.U))
  def formTVec(x: UInt) = x andNot Mux(x(0), ((((BigInt(1) << mtvecInterruptAlign) - 1) << mtvecBaseAlign) | 2).U, 2.U)
  def isaStringToMask(s: String) = s.map(x => 1 << (x - 'A')).foldLeft(0)(_|_)
  def formFS(fs: UInt) = if (coreParams.haveFSDirty) fs else Fill(2, fs.orR)
  def formVS(vs: UInt) = if (usingVector) vs else 0.U
}
