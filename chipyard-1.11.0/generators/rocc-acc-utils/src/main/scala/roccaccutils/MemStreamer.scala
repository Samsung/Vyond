// See LICENSE for license details

package roccaccutils

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.util.{DecoupledHelper}
import freechips.rocketchip.diplomacy.{ValName}
import roccaccutils.logger._

class MemStreamerBundle(implicit hp: L2MemHelperParams) extends Bundle {
  val mem_stream = Flipped(new MemLoaderConsumerBundle) //from MemLoader
  val memwrites_in = Decoupled(new WriterBundle) //to MemWriter
}

trait MemStreamer
  extends Module
  with HasL2MemHelperParams {

  // --------------------------
  // MUST BE DEFINED BY CHILD
  // --------------------------

  val io: MemStreamerBundle
  implicit val p: Parameters
  implicit val valName: ValName = ValName("<MemStreamer>")
  val logger: Logger

  // --------------------------

  // 1. Receive data from the memloader to load_data_queue
  /* Slice data by the L2 bandwidth (assuming 32 bytes).
  ** Different L2 bandwidth will require to change
  ** the memwriter module in Top.scala
  ** and the LiteralChunk bundle in Common.scala. */

  val load_data_queue = Module(new Queue(new LiteralChunk, 5))
  dontTouch(load_data_queue.io.count)
  load_data_queue.io.enq.bits.chunk_data := io.mem_stream.output_data
  load_data_queue.io.enq.bits.chunk_size_bytes := io.mem_stream.available_output_bytes
  load_data_queue.io.enq.bits.is_final_chunk := io.mem_stream.output_last_chunk
  val fire_read = DecoupledHelper(
    io.mem_stream.output_valid,
    load_data_queue.io.enq.ready,
  )
  load_data_queue.io.enq.valid := fire_read.fire(load_data_queue.io.enq.ready)
  io.mem_stream.output_ready := fire_read.fire(io.mem_stream.output_valid)
  io.mem_stream.user_consumed_bytes := io.mem_stream.available_output_bytes

  // ----------------------------
  // API: connect load_data_queue
  // ----------------------------

  when (load_data_queue.io.enq.fire) {
    logger.logInfo("load_data_q:enq: sz:%x final:%x data:%x\n",
      load_data_queue.io.enq.bits.chunk_size_bytes,
      load_data_queue.io.enq.bits.is_final_chunk,
      load_data_queue.io.enq.bits.chunk_data,
    )
  }

  when (load_data_queue.io.deq.fire) {
    logger.logInfo("load_data_q:deq: sz:%x final:%x data:%x\n",
      load_data_queue.io.deq.bits.chunk_size_bytes,
      load_data_queue.io.deq.bits.is_final_chunk,
      load_data_queue.io.deq.bits.chunk_data,
    )
  }

  // 3. Write data to through the memwriter

  val store_data_queue = Module(new Queue(new LiteralChunk, 5))
  dontTouch(store_data_queue.io.count)

  val sdq_chunk_size = store_data_queue.io.deq.bits.chunk_size_bytes
  val sdq_chunk_data = store_data_queue.io.deq.bits.chunk_data
  val sdq_chunk_data_vec = VecInit(Seq.fill(BUS_SZ_BYTES)(0.U(8.W)))
  for (i <- 0 to (BUS_SZ_BYTES - 1)) {
    sdq_chunk_data_vec(sdq_chunk_size - 1.U - i.U) := sdq_chunk_data((8*(i+1))-1, 8*i)
  }
  io.memwrites_in.bits.data := sdq_chunk_data_vec.asUInt
  io.memwrites_in.bits.validbytes := sdq_chunk_size
  io.memwrites_in.bits.end_of_message := store_data_queue.io.deq.bits.is_final_chunk
  io.memwrites_in.valid := store_data_queue.io.deq.valid
  store_data_queue.io.deq.ready := io.memwrites_in.ready

  // -----------------------------
  // API: connect store_data_queue
  // -----------------------------

  when (store_data_queue.io.enq.fire) {
    logger.logInfo("store_data_q:enq: sz:%x final:%x data:%x\n",
      store_data_queue.io.enq.bits.chunk_size_bytes,
      store_data_queue.io.enq.bits.is_final_chunk,
      store_data_queue.io.enq.bits.chunk_data,
    )
  }

  when (store_data_queue.io.deq.fire) {
    logger.logInfo("store_data_q:deq: sz:%x final:%x data:%x\n",
      store_data_queue.io.deq.bits.chunk_size_bytes,
      store_data_queue.io.deq.bits.is_final_chunk,
      store_data_queue.io.deq.bits.chunk_data,
    )
  }
}
