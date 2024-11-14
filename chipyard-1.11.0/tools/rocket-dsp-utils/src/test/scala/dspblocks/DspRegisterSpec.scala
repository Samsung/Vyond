package dspblocks

import breeze.stats.distributions.Uniform
import chiseltest.iotesters.PeekPokeTester
import chisel3.{Bundle, Flipped, Module}
import chiseltest.ChiselScalatestTester
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait DRTMModuleImp {
  val sinkBundle: AXI4StreamBundle
  val edge:       AXI4StreamEdgeParameters
  val memBundle:  AXI4Bundle
  val memEdge:    AXI4EdgeParameters
  val out:        AXI4StreamBundle
  val mem:        AXI4Bundle
}

class DspRegisterTestModule(
  val inP:  AXI4StreamBundleParameters,
  val outP: AXI4StreamSlaveParameters,
  val len:  Int,
//TODO: CHIPYARD, this should not be empty, I'm not sure what the default distribution should be
  val transactions: Seq[AXI4StreamTransaction] = Seq.empty
//                    AXI4StreamTransaction.defaultSeq(100).map(_.randData(Uniform(0.0, 65535.0)))
) extends Module {
  implicit val p: Parameters = Parameters.empty

  val lazyMod = LazyModule(new LazyModule() {
    override lazy val moduleName: String = "SomeModuleName"
    val fuzzer = AXI4StreamFuzzer.bundleParams(transactions, inP)
    val reg = LazyModule(new AXI4DspRegister(len))
    val outNode = AXI4StreamSlaveNode(outP)

    val memMaster = AXI4MasterNode(
      Seq(
        AXI4MasterPortParameters(
          Seq(
            AXI4MasterParameters(
              "testModule"
            )
          )
        )
      )
    )

    reg.streamNode := fuzzer
    outNode := reg.streamNode
    // memMaster      := reg.mem.get
    reg.mem.get := memMaster

    lazy val module = new LazyModuleImp(this) with DRTMModuleImp {
      override val (sinkBundle, edge) = outNode.in.head
      override val (memBundle, memEdge) = memMaster.out.head

      override val out = IO(AXI4StreamBundle(sinkBundle.params))
      override val mem = IO(Flipped(AXI4Bundle(memBundle.params)))

      out <> sinkBundle
      memBundle <> mem
    }
  })

  val mod = Module(lazyMod.module)

  val io = IO(new Bundle {
    val out = new AXI4StreamBundle(mod.edge.bundle)
    val mem = Flipped(new AXI4Bundle(mod.memEdge.bundle))
  })

  io.out <> mod.out
  mod.mem <> io.mem
}

class DspRegisterTestModuleTester(
  c: DspRegisterTestModule,
  expectTranslator: Seq[AXI4StreamTransaction] => Seq[AXI4StreamTransactionExpect] = {
    _.map(t => AXI4StreamTransactionExpect(data = Some(t.data)))
  }
) extends PeekPokeTester(c)
    with AXI4StreamSlaveModel[DspRegisterTestModule]
    with AXI4MasterModel {

  override val memAXI: AXI4Bundle = c.io.mem
  axiReset()
  reset(10)

  bindSlave(c.io.out).addExpects(
    expectTranslator(c.transactions)
  )

  println(s"${axiReadWord(0)} is the veclen")
}

class DspRegisterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior.of("AXI4DspRegister")

  it should "be able to read and write" ignore {
    val inP = AXI4StreamBundleParameters(n = 128)
    val outP = AXI4StreamSlaveParameters()
    val transactions = AXI4StreamTransaction.defaultSeq(64).zipWithIndex.map({ case (t, i) => t.copy(data = i) })

    test(new DspRegisterTestModule(inP, outP, 64, transactions))
      .runPeekPoke(new DspRegisterTestModuleTester(_) {
        axiWriteWord(0, 64)
        axiWriteWord(0x10, 15)
        axiWriteWord(0x8, 0xff00)
        step(64)
        axiWriteWord(0x8, 0x00ff)
        stepToCompletion()

        for (i <- 0 until 64) {
          require(axiReadWord(24 + i * 8) == BigInt(i), s"Addr $i is wrong")
        }
      })
  }

  it should "work with streams narrower than memory width" in {}

  it should "work with streams wider than memory width"

  it should "be able to store after load" in {}
}
