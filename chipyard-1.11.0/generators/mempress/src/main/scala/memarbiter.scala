//see LICENSE for license
//authors: Joonho Whangbo

package mempress

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import org.chipsalliance.cde.config._

class MemArbiterIO(val max_streams: Int, val idx_w: Int)(implicit val p: Parameters) extends Bundle {
  val req_in = Flipped(Decoupled(Indexed(new L2ReqInternal, idx_w)))
  val req_out = Decoupled(Indexed(new L2ReqInternal, idx_w))
}

class MemArbiter(val max_streams: Int, val idx_w: Int)(implicit val p: Parameters) extends Module {
  val io = IO(new MemArbiterIO(max_streams, idx_w))

  val que_depth = p(MemPressArbQueDepth)
  val que = Seq.fill(max_streams)(Module(new Queue(new L2ReqInternal, que_depth)))

  val cur_idx = io.req_in.bits.idx

  io.req_in.ready := false.B
  que.zipWithIndex.foreach { case(q, idx) =>
    q.io.enq.bits := io.req_in.bits.data
    when (idx.U =/= cur_idx) {
      q.io.enq.valid := false.B
    }.otherwise {
      q.io.enq.valid := io.req_in.valid
      io.req_in.ready := q.io.enq.ready
    }
  }

  val arbiter = Module(new RRArbiter(new L2ReqInternal, max_streams))
  for (i <- 0 until max_streams) {
    arbiter.io.in(i) <> que(i).io.deq
  }

  arbiter.io.out.ready := io.req_out.ready
  io.req_out.valid := arbiter.io.out.valid
  io.req_out.bits.data <> arbiter.io.out.bits
  io.req_out.bits.idx := arbiter.io.chosen
}
