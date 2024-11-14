//see LICENSE for license
//authors: Joonho Whangbo

package mempress

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.{TLBConfig, HellaCacheReq}

case object MemPressMaxStreams extends Field[Int]
case object MemPressArbQueDepth extends Field[Int]
case object MemPressTLDepth extends Field[Int]
case object MemPressMaxOutstandingReqs extends Field[Int]
case object MemPressPrintfEnable extends Field[Boolean](false)
case object MemPressFiboLFSRBits extends Field[Int]
case object MemPressSingleL2TL extends Field[Boolean]

class MemPress(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(
    opcodes = opcodes, nPTWPorts = if (p(MemPressTLB).isDefined) p(MemPressMaxStreams) else 0) {

  override lazy val module = new MemPressImp(this)

  val max_streams = p(MemPressMaxStreams)
  val max_outstand_reqs = p(MemPressMaxOutstandingReqs)
  val single_l2tl = p(MemPressSingleL2TL)

  val l2helper_cnt = if (single_l2tl) 1 else max_streams

  val l2helper = (0 until l2helper_cnt).map{ x =>
                  val y = LazyModule(new L2MemHelper(s"stream[${x}]", 
                                     numOutstandingReqs=max_outstand_reqs, 
                                     queueResponses=true, 
                                     queueRequests=true)) 
                  tlNode := TLWidthWidget(16) := y.masterNode
                  y
                }
}

class MemPressImp(outer: MemPress)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  chisel3.dontTouch(io)

  val max_streams = p(MemPressMaxStreams)
  val idx_w = log2Ceil(max_streams)
  val single_l2tl = p(MemPressSingleL2TL)
  val l2helper_cnt = if (single_l2tl) 1 else max_streams

  // tie up some wires
  io.mem.req.valid := false.B
  io.mem.s1_kill := false.B
  io.mem.s2_kill := false.B
  io.mem.keep_clock_enabled := true.B
  io.interrupt := false.B

  val ctrl = Module(new CtrlModule(max_streams, idx_w, single_l2tl))
  ctrl.io.rocc_in <> io.cmd
  io.resp <> ctrl.io.rocc_out
  io.cmd.ready := ctrl.io.rocc_in.ready
  io.busy := ctrl.io.busy

  val reqgen = Module(new ReqGen(max_streams, idx_w))
  reqgen.io.send_reqs := ctrl.io.send_reqs
  ctrl.io.sent_done := reqgen.io.sent_done
  ctrl.io.req_fire := reqgen.io.req_fire
  reqgen.io.global_stream_info <> ctrl.io.global_stream_info
  reqgen.io.local_stream_info <> ctrl.io.local_stream_info

  val arb = Module(new MemArbiter(max_streams, idx_w))
  arb.io.req_in <> reqgen.io.req

  val status = RegEnable(io.cmd.bits.status, io.cmd.fire)

  val chosen = arb.io.req_out.bits.idx
  arb.io.req_out.ready := false.B

  for (i <- 0 until l2helper_cnt) {
    if (single_l2tl) {
      outer.l2helper(i).module.io.userif.req.valid := arb.io.req_out.valid
      arb.io.req_out.ready := outer.l2helper(i).module.io.userif.req.ready
    } else {
      when (i.U === chosen) {
        outer.l2helper(i).module.io.userif.req.valid := arb.io.req_out.valid
        arb.io.req_out.ready := outer.l2helper(i).module.io.userif.req.ready
      }.otherwise {
        outer.l2helper(i).module.io.userif.req.valid := false.B
      }
    }
    ctrl.io.dmem_resp(i) <> outer.l2helper(i).module.io.userif.resp
    outer.l2helper(i).module.io.userif.req.bits := arb.io.req_out.bits.data
    outer.l2helper(i).module.io.sfence <> ctrl.io.sfence_out
    outer.l2helper(i).module.io.status.valid := ctrl.io.dmem_status_out.valid
    outer.l2helper(i).module.io.status.bits := ctrl.io.dmem_status_out.bits.status
    io.ptw(i) <> outer.l2helper(i).module.io.ptw
  }
}

class WithMemPress(singleL2: Boolean = true, maxStreams: Int = 16) extends Config ((site, here, up) => {
  case MemPressTLB => Some(TLBConfig(nSets = 4, nWays = 4, nSectors = 1, nSuperpageEntries = 1))
  case MemPressMaxStreams => maxStreams
  case MemPressArbQueDepth => 8
  case MemPressMaxOutstandingReqs => 8
  case MemPressPrintfEnable => false
  case MemPressFiboLFSRBits => 30
  case MemPressSingleL2TL => singleL2
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val mempress = LazyModule.apply(new MemPress(OpcodeSet.custom2)(p))
      mempress
    }
  )
})
