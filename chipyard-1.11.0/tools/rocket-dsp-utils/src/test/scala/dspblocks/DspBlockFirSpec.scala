// SPDX-License-Identifier: Apache-2.0

package dspblocks

import chisel3._
import chiseltest._
import chiseltest.iotesters._
import chisel3.util.Cat
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import org.scalatest.freespec.AnyFreeSpec

class MyManyDynamicElementVecFir(length: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val valid = Input(Bool())
    val out = Output(UInt(8.W))
    val consts = Input(Vec(length, UInt(8.W)))
  })

  // Such concision! You'll learn what all this means later.
  val taps = Seq(io.in) ++ Seq.fill(io.consts.length - 1)(RegInit(0.U(8.W)))
  taps.zip(taps.tail).foreach { case (a, b) => when(io.valid) { b := a } }

  io.out := taps.zip(io.consts).map { case (a, b) => a * b }.reduce(_ + _)
}
//
// Base class for all FIRBlocks.
// This can be extended to make TileLink, AXI4, APB, AHB, etc. flavors of the FIR filter
//
abstract class FIRBlock[D, U, EO, EI, B <: Data](val nFilters: Int, val nTaps: Int)(implicit p: Parameters)
// HasCSR means that the memory interface will be using the RegMapper API to define status and control registers
    extends DspBlock[D, U, EO, EI, B]
    with HasCSR {
  // diplomatic node for the streaming interface
  // identity node means the output and input are parameterized to be the same
  val streamNode = AXI4StreamIdentityNode()

  // define the what hardware will be elaborated
  lazy val module = new LazyModuleImp(this) {
    // get streaming input and output wires from diplomatic node
    val (in, _) = streamNode.in.head
    val (out, _) = streamNode.out.head

    require(
      in.params.n >= nFilters,
      s"""AXI-4 Stream port must be big enough for all
         |the filters (need $nFilters,, only have ${in.params.n})""".stripMargin
    )

    // make registers to store taps
    val taps = Reg(Vec(nFilters, Vec(nTaps, UInt(8.W))))

    // memory map the taps, plus the first address is a read-only field that says how many filter lanes there are
    val mmap = Seq(
      RegField.r(64, nFilters.U, RegFieldDesc("nFilters", "Number of filter lanes"))
    ) ++ taps.flatMap(_.map(t => RegField(8, t, RegFieldDesc("tap", "Tap"))))

    // generate the hardware for the memory interface
    // in this class, regmap is abstract (unimplemented). mixing in something like AXI4HasCSR or TLHasCSR
    // will define regmap for the particular memory interface
    regmap(mmap.zipWithIndex.map({ case (r, i) => i * 8 -> Seq(r) }): _*)

    // make the FIR lanes and connect inputs and taps
    val outs = for (i <- 0 until nFilters) yield {
      val fir = Module(new MyManyDynamicElementVecFir(nTaps))

      fir.io.in := in.bits.data((i + 1) * 8, i * 8)
      fir.io.valid := in.valid && out.ready
      fir.io.consts := taps(i)
      fir.io.out
    }

    val output = if (outs.length == 1) {
      outs.head
    } else {
      outs.reduce((x: UInt, y: UInt) => Cat(y, x))
    }

    out.bits.data := output
    in.ready := out.ready
    out.valid := in.valid
  }
}

// make AXI4 flavor of FIRBlock
abstract class AXI4FIRBlock(nFilters: Int, nTaps: Int)(implicit p: Parameters)
    extends FIRBlock[
      AXI4MasterPortParameters,
      AXI4SlavePortParameters,
      AXI4EdgeParameters,
      AXI4EdgeParameters,
      AXI4Bundle
    ](nFilters, nTaps)
    with AXI4DspBlock
    with AXI4HasCSR {
  override val mem = Some(
    AXI4RegisterNode(
      AddressSet(0x0, 0xffffL),
      beatBytes = 8
    )
  )
}

// running the code below will show what firrtl is generated
// note that LazyModules aren't really chisel modules- you need to call ".module" on them when invoking the chisel driver
// also note that AXI4StandaloneBlock is mixed in- if you forget it, you will get weird diplomacy errors because the memory
// interface expects a master and the streaming interface expects to be connected. AXI4StandaloneBlock will add top level IOs
// println(chisel3.Driver.emit(() => LazyModule(new AXI4FIRBlock(1, 8)(Parameters.empty) with AXI4StandaloneBlock).module))

import dspblocks.MemMasterModel

abstract class FIRBlockTester[D, U, EO, EI, B <: Data](c: FIRBlock[D, U, EO, EI, B])
    extends PeekPokeTester(c.module)
    with MemMasterModel {
  // check that address 0 is the number of filters
  require(memReadWord(0) == c.nFilters)
  // write 1 to all the taps
  for (i <- 0 until c.nFilters * c.nTaps) {
    memWriteWord(8 + i * 8, 1)
  }
}

// specialize the generic tester for axi4
class AXI4FIRBlockTester(c: AXI4FIRBlock with AXI4StandaloneBlock) extends FIRBlockTester(c) with AXI4MasterModel {
  def memAXI = c.ioMem.get
}

class DspBlockFirSpec extends AnyFreeSpec with ChiselScalatestTester {
//  "should run" in {
//    // invoking testers on lazymodules is a little strange.
//    // note that the firblocktester takes a lazymodule, not a module (it calls .module in "extends PeekPokeTester()").
//    val lm = LazyModule(new AXI4FIRBlock(1, 8)(Parameters.empty) with AXI4StandaloneBlock)
//    test(lm.module).runPeekPoke(_ => new AXI4FIRBlockTester(lm))
//  }
}
