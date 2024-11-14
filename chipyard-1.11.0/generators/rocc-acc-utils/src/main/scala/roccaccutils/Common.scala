// See LICENSE for license details

package roccaccutils

import chisel3._
import chisel3.util._

class PtrInfo extends Bundle {
  val ptr = UInt(64.W)
}
class DecompressPtrInfo extends Bundle {
  val ip = UInt(64.W)
}

class StreamInfo extends Bundle {
  val ip = UInt(64.W)
  val isize = UInt(64.W)
}

class DstInfo extends Bundle {
  val op = UInt(64.W)
  val cmpflag = UInt(64.W)
}

class DecompressDstInfo extends Bundle {
  val op = UInt(64.W)
  val cmpflag = UInt(64.W)
}

class DstWithValInfo extends Bundle {
  val op = UInt(64.W)
  val cmpflag = UInt(64.W)
  val cmpval = UInt(64.W)
}

class WriterBundle(implicit val hp: L2MemHelperParams) extends Bundle with HasL2MemHelperParams {
  val data = UInt(BUS_SZ_BITS.W)
  val validbytes = UInt((BUS_SZ_BYTES_LG2UP + 1).W)
  val end_of_message = Bool()
}

class LiteralChunk(implicit val hp: L2MemHelperParams) extends Bundle with HasL2MemHelperParams {
  val chunk_data = UInt(BUS_SZ_BITS.W)
  val chunk_size_bytes = UInt((BUS_SZ_BYTES_LG2UP + 1).W)
  val is_final_chunk = Bool()
}

class LoadInfoBundle(implicit val hp: L2MemHelperParams) extends Bundle with HasL2MemHelperParams {
  val start_byte = UInt(BUS_SZ_BYTES_LG2UP.W)
  val end_byte = UInt(BUS_SZ_BYTES_LG2UP.W)
}

class MemLoaderConsumerBundle(implicit val hp: L2MemHelperParams) extends Bundle with HasL2MemHelperParams {
  val user_consumed_bytes = Input(UInt((BUS_SZ_BYTES_LG2UP + 1).W))
  val available_output_bytes = Output(UInt((BUS_SZ_BYTES_LG2UP + 1).W))
  val output_valid = Output(Bool())
  val output_ready = Input(Bool())
  val output_data = Output(UInt(BUS_SZ_BITS.W))
  val output_last_chunk = Output(Bool())
}

class BufInfoBundle extends Bundle {
  val len_bytes = UInt(64.W)
}
