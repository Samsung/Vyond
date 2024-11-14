// See LICENSE for license details

package roccaccutils

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.util.{DecoupledHelper}
import freechips.rocketchip.rocket.constants.{MemoryOpConstants}
import roccaccutils.logger._

class MemLoader(memLoaderQueDepth: Int = 16*4, logger: Logger = DefaultLogger)(implicit p: Parameters, val hp: L2MemHelperParams) extends Module
     with MemoryOpConstants
     with HasL2MemHelperParams {

  val io = IO(new Bundle {
    val l2helperUser = new L2MemHelperBundle

    val src_info = Flipped(Decoupled(new StreamInfo))

    val consumer = new MemLoaderConsumerBundle
  })

  val buf_info_queue = Module(new Queue(new BufInfoBundle, 16))

  val load_info_queue = Module(new Queue(new LoadInfoBundle, 256))

  val base_addr_bytes = io.src_info.bits.ip
  val base_len = io.src_info.bits.isize
  val base_addr_start_index = io.src_info.bits.ip & BUS_BIT_MASK.U
  val aligned_loadlen =  base_len + base_addr_start_index
  val base_addr_end_index = (base_len + base_addr_start_index) & BUS_BIT_MASK.U
  val base_addr_end_index_inclusive = (base_len + base_addr_start_index - 1.U) & BUS_BIT_MASK.U
  val extra_word = ((aligned_loadlen & BUS_BIT_MASK.U) =/= 0.U).asUInt

  val base_addr_bytes_aligned = (base_addr_bytes >> BUS_SZ_BYTES_LG2UP.U) << BUS_SZ_BYTES_LG2UP.U
  val words_to_load = (aligned_loadlen >> BUS_SZ_BYTES_LG2UP.U) + extra_word
  val words_to_load_minus_one = words_to_load - 1.U


  val print_not_done = RegInit(true.B)

  when (io.src_info.valid && print_not_done) {
    logger.logInfo("base_addr_bytes: %x\n", base_addr_bytes)
    logger.logInfo("base_len: %x\n", base_len)
    logger.logInfo("base_addr_start_index: %x\n", base_addr_start_index)
    logger.logInfo("aligned_loadlen: %x\n", aligned_loadlen)
    logger.logInfo("base_addr_end_index: %x\n", base_addr_end_index)
    logger.logInfo("base_addr_end_index_inclusive: %x\n", base_addr_end_index_inclusive)
    logger.logInfo("extra_word: %x\n", extra_word)
    logger.logInfo("base_addr_bytes_aligned: %x\n", base_addr_bytes_aligned)
    logger.logInfo("words_to_load: %x\n", words_to_load)
    logger.logInfo("words_to_load_minus_one: %x\n", words_to_load_minus_one)
    when (io.src_info.ready) {
      print_not_done := true.B
    } .otherwise {
      print_not_done := false.B
    }
  }

  val request_fire = DecoupledHelper(
    io.l2helperUser.req.ready,
    io.src_info.valid,
    buf_info_queue.io.enq.ready,
    load_info_queue.io.enq.ready
  )

  io.l2helperUser.req.bits.cmd := M_XRD
  io.l2helperUser.req.bits.size := BUS_SZ_BYTES_LG2UP.U
  io.l2helperUser.req.bits.data := 0.U

  val addrinc = RegInit(0.U(64.W))

  load_info_queue.io.enq.bits.start_byte := Mux(addrinc === 0.U, base_addr_start_index, 0.U)
  load_info_queue.io.enq.bits.end_byte := Mux(addrinc === words_to_load_minus_one, base_addr_end_index_inclusive, 31.U)


  when (request_fire.fire() && (addrinc === words_to_load_minus_one)) {
    addrinc := 0.U
  } .elsewhen (request_fire.fire()) {
    addrinc := addrinc + 1.U
  }

  when (io.src_info.fire) {
    logger.logInfo("COMPLETED INPUT LOAD FOR DECOMPRESSION\n")
  }


  io.src_info.ready := request_fire.fire(io.src_info.valid,
                                            addrinc === words_to_load_minus_one)

  buf_info_queue.io.enq.valid := request_fire.fire(buf_info_queue.io.enq.ready,
                                            addrinc === 0.U)
  load_info_queue.io.enq.valid := request_fire.fire(load_info_queue.io.enq.ready)

  buf_info_queue.io.enq.bits.len_bytes := base_len

  io.l2helperUser.req.bits.addr := (base_addr_bytes_aligned) + (addrinc << BUS_SZ_BYTES_LG2UP)
  io.l2helperUser.req.valid := request_fire.fire(io.l2helperUser.req.ready)

  val NUM_QUEUES = 32
  val QUEUE_DEPTHS = memLoaderQueDepth
  val write_start_index = RegInit(0.U(log2Up(NUM_QUEUES+1).W))
  val mem_resp_queues = VecInit.fill(NUM_QUEUES)(Module(new Queue(UInt(8.W), QUEUE_DEPTHS)).io)
  // overridden below
  for (queueno <- 0 until NUM_QUEUES) {
    val q = mem_resp_queues(queueno)
    q.enq.valid := false.B
    q.enq.bits := 0.U
    q.deq.ready := false.B
  }

  val align_shamt = (load_info_queue.io.deq.bits.start_byte << 3)
  val memresp_bits_shifted = io.l2helperUser.resp.bits.data >> align_shamt

  for ( queueno <- 0 until NUM_QUEUES ) {
    mem_resp_queues((write_start_index +& queueno.U) % NUM_QUEUES.U).enq.bits := memresp_bits_shifted >> (queueno * 8)
  }

  val len_to_write = (load_info_queue.io.deq.bits.end_byte - load_info_queue.io.deq.bits.start_byte) +& 1.U

  val wrap_len_index_wide = write_start_index +& len_to_write
  val wrap_len_index_end = wrap_len_index_wide % NUM_QUEUES.U
  val wrapped = wrap_len_index_wide >= NUM_QUEUES.U

  when (load_info_queue.io.deq.valid) {
    logger.logInfo("memloader start %x, end %x\n", load_info_queue.io.deq.bits.start_byte,
      load_info_queue.io.deq.bits.end_byte)
  }

  val resp_fire_noqueues = DecoupledHelper(
    io.l2helperUser.resp.valid,
    load_info_queue.io.deq.valid
  )
  val all_queues_ready = mem_resp_queues.map(_.enq.ready).reduce(_ && _)

  load_info_queue.io.deq.ready := resp_fire_noqueues.fire(load_info_queue.io.deq.valid, all_queues_ready)
  io.l2helperUser.resp.ready := resp_fire_noqueues.fire(io.l2helperUser.resp.valid, all_queues_ready)

  val resp_fire_allqueues = resp_fire_noqueues.fire() && all_queues_ready
  when (resp_fire_allqueues) {
    write_start_index := wrap_len_index_end
  }

  for ( queueno <- 0 until NUM_QUEUES ) {
    val use_this_queue = Mux(wrapped,
                             (queueno.U >= write_start_index) || (queueno.U < wrap_len_index_end),
                             (queueno.U >= write_start_index) && (queueno.U < wrap_len_index_end)
                            )
    mem_resp_queues(queueno).enq.valid := resp_fire_noqueues.fire() && use_this_queue && all_queues_ready
  }

  for ( queueno <- 0 until NUM_QUEUES ) {
    when (mem_resp_queues(queueno).deq.valid) {
      logger.logInfo("queueind %d, val %x\n", queueno.U, mem_resp_queues(queueno).deq.bits)
    }
  }

  val read_start_index = RegInit(0.U(log2Up(NUM_QUEUES+1).W))

  val len_already_consumed = RegInit(0.U(64.W))

  val remapVecData = Wire(Vec(NUM_QUEUES, UInt(8.W)))
  val remapVecValids = Wire(Vec(NUM_QUEUES, Bool()))
  val remapVecReadys = Wire(Vec(NUM_QUEUES, Bool()))

  for (queueno <- 0 until NUM_QUEUES) {
    val remapindex = (queueno.U +& read_start_index) % NUM_QUEUES.U
    remapVecData(queueno) := mem_resp_queues(remapindex).deq.bits
    remapVecValids(queueno) := mem_resp_queues(remapindex).deq.valid
    mem_resp_queues(remapindex).deq.ready := remapVecReadys(queueno)
  }
  io.consumer.output_data := Cat(remapVecData.reverse)


  val buf_last = (len_already_consumed + io.consumer.user_consumed_bytes) === buf_info_queue.io.deq.bits.len_bytes
  val count_valids = remapVecValids.map(_.asUInt).reduce(_ +& _)
  val unconsumed_bytes_so_far = buf_info_queue.io.deq.bits.len_bytes - len_already_consumed

  val enough_data = Mux(unconsumed_bytes_so_far >= NUM_QUEUES.U,
                        count_valids === NUM_QUEUES.U,
                        count_valids >= unconsumed_bytes_so_far)

  io.consumer.available_output_bytes := Mux(unconsumed_bytes_so_far >= NUM_QUEUES.U,
                                    NUM_QUEUES.U,
                                    unconsumed_bytes_so_far)

  io.consumer.output_last_chunk := (unconsumed_bytes_so_far <= NUM_QUEUES.U)

  val read_fire = DecoupledHelper(
    io.consumer.output_ready,
    buf_info_queue.io.deq.valid,
    enough_data
  )

  when (read_fire.fire()) {
    logger.logInfo("MEMLOADER READ: bytesread %d\n", io.consumer.user_consumed_bytes)

  }

  io.consumer.output_valid := read_fire.fire(io.consumer.output_ready)

  for (queueno <- 0 until NUM_QUEUES) {
    remapVecReadys(queueno) := (queueno.U < io.consumer.user_consumed_bytes) && read_fire.fire()
  }

  when (read_fire.fire()) {
    read_start_index := (read_start_index +& io.consumer.user_consumed_bytes) % NUM_QUEUES.U
  }

  buf_info_queue.io.deq.ready := read_fire.fire(buf_info_queue.io.deq.valid) && buf_last

  when (read_fire.fire()) {
    when (buf_last) {
      len_already_consumed := 0.U
    } .otherwise {
      len_already_consumed := len_already_consumed + io.consumer.user_consumed_bytes
    }
  }

}
