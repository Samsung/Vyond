//see LICENSE for license
//authors: Joonho Whangbo

package mempress

import chisel3._
import chisel3.util._
import chisel3.util.random.FibonacciLFSR
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import org.chipsalliance.cde.config._
import freechips.rocketchip.util.DecoupledHelper

class ReqGenIO(val max_streams: Int, val idx_w: Int)(implicit val p: Parameters) extends Bundle {
  val send_reqs = Input(Bool())
  val sent_done = Output(Bool())
  val req_fire  = Output(Bool())

  val global_stream_info = Flipped(Valid(new GlobalStreamInfo(max_streams)))
  val local_stream_info  = Flipped(Valid(Indexed(new LocalStreamInfo, idx_w)))

  val req = Decoupled(Indexed(new L2ReqInternal, idx_w))
}

class ReqGen(val max_streams: Int, val idx_w: Int)(implicit val p: Parameters) extends Module {
  val io = IO(new ReqGenIO(max_streams, idx_w))

  val stride_rd :: stride_wr :: burst_rd :: burst_wr :: rand_rd :: rand_wr :: Nil = Enum(6)
  val fibo_bits = p(MemPressFiboLFSRBits)
  val rand_val = FibonacciLFSR.maxPeriod(fibo_bits)

  val stream_cnt     = RegInit(0.U(log2Ceil(max_streams + 1).W))
  val addr_range     = RegInit(0.U(64.W))
  val max_reqs       = RegInit(0.U(64.W))

  val start_addr     = RegInit(VecInit(Seq.fill(max_streams)(0.U(64.W))))
  val stride         = RegInit(VecInit(Seq.fill(max_streams)(0.U(64.W)))) // bytes to skip
  val stream_type    = RegInit(VecInit(Seq.fill(max_streams)(stride_rd.asUInt)))
  val end_addr       = RegInit(VecInit(Seq.fill(max_streams)(0.U(64.W))))
  val cur_addr       = RegInit(VecInit(Seq.fill(max_streams)(0.U(64.W))))

  io.req_fire := io.req.fire
  io.sent_done := false.B

  io.req.valid     := false.B
  io.req.bits.data.addr := 0.U
  io.req.bits.data.cmd  := 0.U
  io.req.bits.data.size := log2Ceil(16).U
  io.req.bits.data.data := 0.U
  io.req.bits.idx := 0.U

  when (io.global_stream_info.valid) {
    val gbits = io.global_stream_info.bits
    stream_cnt := gbits.stream_cnt
    addr_range := gbits.addr_range
    max_reqs := gbits.max_reqs

    MemPressLogger.logInfo("Global Stream Info, stream_cnt: %d, addr_range: 0x%x, max_reqs: %d\n", 
      gbits.stream_cnt, gbits.addr_range, gbits.max_reqs)
  }

  when (io.local_stream_info.valid) {
    val lbits = io.local_stream_info.bits.data
    val lidx = io.local_stream_info.bits.idx

    val cur_start_addr = lbits.start_addr
    start_addr(lidx) := cur_start_addr
    stride(lidx) := lbits.stride
    stream_type(lidx) := lbits.stream_type

    end_addr(lidx) := cur_start_addr + addr_range
    cur_addr(lidx) := cur_start_addr

    MemPressLogger.logInfo("Local Stream Info[%d], start_addr: 0x%x, stride: 0x%x, stream_type: %d\n", 
      lidx, lbits.start_addr, lbits.stride, lbits.stream_type)
  }

  val s_idx       = RegInit(0.U(log2Ceil(max_streams + 1).W))
  val s_sent      = RegInit(VecInit(Seq.fill(max_streams)(0.U(64.W))))
  val s_sent_done = RegInit(VecInit(Seq.fill(max_streams)(false.B)))

  when (io.send_reqs) {
    val cur_stream = stream_type(s_idx)
    val addr = WireInit(0.U(64.W))
    val data = ((1.U << 128.U) - 1.U) ^ (1.U << s_idx)

    when (cur_stream === rand_rd.asUInt || cur_stream === rand_wr.asUInt) {
      val msb = 64.U - PriorityEncoder(Reverse(addr_range))
      addr := start_addr(s_idx) + ((rand_val << 4.U) & ((1.U << msb) - 1.U))
    }.elsewhen (cur_stream === stride_rd.asUInt || cur_stream === stride_wr.asUInt) {
      val nxt_addr = cur_addr(s_idx) + stride(s_idx)
      when (nxt_addr >= end_addr(s_idx)) {
        addr := start_addr(s_idx)
      } .otherwise {
        addr := nxt_addr
      }
      cur_addr(s_idx) := addr
    }.elsewhen (cur_stream === burst_rd.asUInt || cur_stream === burst_wr.asUInt) {
      // TODO : add burst stream implementation
      // - What is the difference btw burst vs sending requests that has consec addr?
      // - Just leave it as is for now
      addr := start_addr(s_idx)
    }

    val cur_cmd = WireInit(0.U)
    when (cur_stream === stride_rd.asUInt || 
          cur_stream === burst_rd.asUInt || 
          cur_stream === rand_rd.asUInt) {
      cur_cmd := M_XRD
    } .otherwise {
      cur_cmd := M_XWR
    }

    io.req.valid     := !s_sent_done(s_idx)
    io.req.bits.data.addr := addr
    io.req.bits.data.cmd  := cur_cmd
    io.req.bits.data.size := log2Ceil(16).U
    io.req.bits.data.data := data
    io.req.bits.idx := s_idx

    when (io.req.fire) {
      MemPressLogger.logInfo("MemReq -> s_idx: %d, s_sent: %d, addr: 0x%x, data: 0x%x streamtype: %d\n",
        s_idx, s_sent(s_idx), addr, data, cur_stream)
    }

    when (s_sent_done(s_idx)) {
      s_idx := Mux(s_idx === stream_cnt - 1.U, 0.U, s_idx + 1.U)
    }.elsewhen (io.req.fire) {
      s_idx := Mux(s_idx === stream_cnt - 1.U, 0.U, s_idx + 1.U)
      s_sent(s_idx) := s_sent(s_idx) + 1.U
      when (s_sent(s_idx) === max_reqs - 1.U) {
        s_sent_done(s_idx) := true.B
      }
    }

    val sent_done = check_done(s_sent_done)
    when (sent_done.reduce(_&&_)) {
      io.sent_done := true.B
    }
  }.otherwise {
    io.req.valid := false.B
  }

  // FIXME : same code in ctrl.scala
  def check_done(regs : Vec[Bool]) = {
    val done = regs.zipWithIndex.map { case(r, idx) =>
      val x = WireInit(true.B)
      when (idx.U < stream_cnt) {
        x := r
      }
      x
    }
    done
  }
}
