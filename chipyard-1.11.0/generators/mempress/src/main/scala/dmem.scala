//see LICENSE for license
//authors: Albert Ou, Joonho Whangbo
//ported from sha3
package mempress

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.tile.HasCoreParameters
import freechips.rocketchip.rocket.{HellaCacheReq, TLB, TLBPTWIO, TLBConfig, MStatus, PRV}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

case object MemPressTLB extends Field[Option[TLBConfig]](None)

class DmemModule(implicit p: Parameters) extends LazyModule {
  lazy val module = new DmemModuleImp(this)

  val max_outstanding_reqs = p(MemPressTLDepth)
  // FIXME: Unused Diplomacy node needed for conveying the physical address map to the TLB
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name="MEMPRESS", sourceId=IdRange(0, max_outstanding_reqs))))))
}

class DmemModuleImp(outer: DmemModule)(implicit p: Parameters) extends LazyModuleImp(outer)
  with HasCoreParameters {

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new HellaCacheReq))
    val mem = Decoupled(new HellaCacheReq)
    val ptw = new TLBPTWIO
    val status = Input(new MStatus())
    val sfence = Input(Bool())
  })

  val (tl, edge) = outer.node.out.head
  // Tie off unused channels
  tl.a.valid := false.B
  tl.b.ready := true.B
  tl.c.valid := false.B
  tl.d.ready := true.B
  tl.e.valid := false.B

  val tlb = Module(new TLB(false, log2Ceil(coreDataBytes), p(MemPressTLB).get)(edge, p))
  tlb.io.req.valid := io.req.valid
  tlb.io.req.bits.vaddr := io.req.bits.addr
  tlb.io.req.bits.size := io.req.bits.size
  tlb.io.req.bits.cmd := io.req.bits.cmd
  tlb.io.req.bits.prv := io.req.bits.dprv
  tlb.io.req.bits.v := io.req.bits.dv
  tlb.io.req.bits.passthrough := false.B
  val tlb_ready = tlb.io.req.ready && !tlb.io.resp.miss

  io.ptw <> tlb.io.ptw
  tlb.io.ptw.status := io.status
  tlb.io.sfence.valid := io.sfence
  tlb.io.sfence.bits.rs1 := false.B
  tlb.io.sfence.bits.rs2 := false.B
  tlb.io.sfence.bits.addr := 0.U
  tlb.io.sfence.bits.asid := 0.U
  tlb.io.sfence.bits.hv := false.B // TODO : Check if this is correct
  tlb.io.sfence.bits.hg := false.B // TODO : Check if this is correct
  tlb.io.kill := false.B

  io.req.ready := io.mem.ready && tlb_ready

  io.mem.valid := io.req.valid && tlb_ready
  io.mem.bits := io.req.bits
  io.mem.bits.addr := tlb.io.resp.paddr
  /*
   * FIXME: Asserting phys sets io.req.bits.passthrough to true in the
   * DCache TLB, which causes it to assume that the request originates
   * from the PTW and treat it as a supervisor access.  This may lead to
   * spurious PMP exceptions (io.resp.ae.ld) even when the actual
   * privilege level is M-mode.
   */
  io.mem.bits.phys := (io.req.bits.dprv =/= PRV.M.U)

  // FIXME: Check TLB exceptions
}
