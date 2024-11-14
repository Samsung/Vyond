// See LICENSE for license details

package aes

import sys.process._

import chisel3._
import chisel3.util.{HasBlackBoxResource, HasBlackBoxPath}

import freechips.rocketchip.system.{BaseConfig}
import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.unittest.{UnitTests, UnitTest}

class aes_cipher_core_tb extends BlackBox with HasBlackBoxPath {
  val io = IO(new Bundle {
    val clk_i = Input(Clock())
    val rst_ni = Input(Reset())

    val test_done_o = Output(Bool())
    val test_passed_o = Output(Bool())
  })

  val chipyardDir = System.getProperty("user.dir")
  val aesDir = s"$chipyardDir/generators/aes-acc/src/main/resources/vsrc/aes/aes_cipher_core"

  val proc = s"make -C $aesDir tb"
  require(proc.! == 0, "Failed to run pre-processing step")

  addPath(s"$aesDir/tb.sv")
}

class AesCipherCoreTest extends UnitTest(100000) {
  val acct = Module(new aes_cipher_core_tb)
  acct.io.clk_i := clock
  val r = RegInit(false.B)
  when (io.start) {
    r := !r
  }
  acct.io.rst_ni := r
  io.finished := acct.io.test_done_o
  when (io.finished) {
    assert(acct.io.test_passed_o, "ERROR: TB failed")
  }
}

object AesCipherCoreUnitTests {
  def apply(): Seq[UnitTest] =
    Seq(Module(new AesCipherCoreTest))
}

class AesCipherCoreTestConfig extends Config(
  new Config((site, here, up) => {
    case UnitTests => (q: Parameters) => AesCipherCoreUnitTests()
  }) ++
  new BaseConfig)
