// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.jtag2mm

import chiseltest.iotesters.PeekPokeTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chiseltest.{ChiselScalatestTester, VerilatorBackendAnnotation}
import chiseltest.iotesters.PeekPokeTester

class JtagFuzzerTester(dut: JtagFuzzer) extends PeekPokeTester(dut) {

  step(10)
  step(5)
  step(2500)
}

class JtagFuzzerSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  def dut(irLength: Int, beatBytes: Int, numOfTransfers: Int): JtagFuzzer = {
    new JtagFuzzer(irLength, beatBytes, numOfTransfers)
  }

  val beatBytes = 4
  val irLength = 4
  val numOfTransfers = 10

  it should "Test JTAG Fuzzer" in {

    test(dut(irLength, beatBytes, numOfTransfers))
      .withAnnotations(Seq(VerilatorBackendAnnotation))
      .runPeekPoke(new JtagFuzzerTester(_))
  }
}
