// SPDX-License-Identifier: Apache-2.0

package craft

import chisel3._
import chisel3.experimental.requireIsHardware
import chisel3.util._

object ShiftRegisterMem {

  def apply[T <: Data](in: T, n: Int, en: Bool = true.B, use_sp_mem: Boolean = false, name: String = null): T = {
    requireIsHardware(in)
    if (n == 0) {
      in
    } else if (n == 1) {
      val out = RegEnable(in, en)
      out
    } else if (use_sp_mem) {
      require(n % 2 == 0, "Odd shift register length with single-port SRAMs is not supported")

      val out_sp0 = Wire(in.cloneType)
      out_sp0 := DontCare

      val out_sp1 = Wire(in.cloneType)
      out_sp1 := DontCare

      val mem_sp0 = SyncReadMem(n / 2, in.cloneType)
      val mem_sp1 = SyncReadMem(n / 2, in.cloneType)

      val index_counter = Counter(en, n)._1
      val raddr_sp0 = index_counter >> 1.U
      val raddr_sp1 = RegEnable(raddr_sp0, (n / 2 - 1).U, en)

      val wen_sp0 = index_counter(0)
      val wen_sp1 = WireDefault(false.B)
      wen_sp1 := ~wen_sp0

      when(en) {
        val rdwrPort = mem_sp0(raddr_sp0)
        when(wen_sp0) { rdwrPort := in }.otherwise { out_sp0 := rdwrPort }
      }

      when(en) {
        val rdwrPort = mem_sp1(raddr_sp1)
        when(wen_sp1) { rdwrPort := in }.otherwise { out_sp1 := rdwrPort }
      }
      val out = Mux(~wen_sp1, out_sp0, out_sp1)
      out
    } else {
      val mem = SyncReadMem(n, in.cloneType)
      if (name != null) {
        mem.suggestName(name)
      }
      val raddr = Counter(en, n)._1
      val out = mem.read(raddr, en)

      val waddr = RegEnable(raddr, (n - 1).U, en)
      when(en) {
        mem.write(waddr, in)
      }
      out
    }
  }
}
