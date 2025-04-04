//See LICENSE for license details
package firesim.bridges

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.DecoupledHelper

import testchipip.cosim.{SerializableTileTraceIO, SpikeCosimConfig, TileTraceIO, TraceBundleWidths}

import midas.widgets._

case class CospikeBridgeParams(
  widths: TraceBundleWidths,
  hartid: Int,
  cfg:    SpikeCosimConfig,
)

class CospikeTargetIO(widths: TraceBundleWidths) extends Bundle {
  val trace = Input(new SerializableTileTraceIO(widths))
}

/** Blackbox that is instantiated in the target
  */
class CospikeBridge(params: CospikeBridgeParams)
    extends BlackBox
    with Bridge[HostPortIO[CospikeTargetIO], CospikeBridgeModule] {
  val io       = IO(new CospikeTargetIO(params.widths))
  val bridgeIO = HostPort(io)

  // give the Cospike params to the GG module
  val constructorArg = Some(params)

  // generate annotations to pass to GG
  generateAnnotations()
}

/** Helper function to connect blackbox
  */
object CospikeBridge {
  def apply(trace: TileTraceIO, hartid: Int, cfg: SpikeCosimConfig) = {
    val params = new CospikeBridgeParams(trace.traceBundleWidths, hartid, cfg)
    val cosim  = withClockAndReset(trace.clock, trace.reset) {
      Module(new CospikeBridge(params))
    }
    cosim.io.trace.trace.insns.map(t => {
      t       := DontCare
      t.valid := false.B
    })
    cosim.io.trace := trace.asSerializableTileTrace
    cosim
  }
}

//*************************************************
//* GOLDEN GATE MODULE
//* This lives in the host (still runs on the FPGA)
//*************************************************

class CospikeBridgeModule(params: CospikeBridgeParams)(implicit p: Parameters)
    extends BridgeModule[HostPortIO[CospikeTargetIO]]()(p)
    with StreamToHostCPU {
  // CONSTANTS: DMA Parameters
  val toHostCPUQueueDepth = 6144

  lazy val module = new BridgeModuleImp(this) {

    // setup io
    val io    = IO(new WidgetIO)
    val hPort = IO(HostPort(new CospikeTargetIO(params.widths)))

    // helper to get number to round up to nearest multiple
    def roundUp(num: Int, mult: Int): Int = { (num.toFloat / mult).ceil.toInt * mult }

    // get the traces
    val traces             = hPort.hBits.trace.trace.insns.map({ unmasked =>
      val masked = WireDefault(unmasked)
      masked.valid := unmasked.valid && !hPort.hBits.trace.reset
      masked
    })
    private val iaddrWidth = roundUp(traces.map(_.iaddr.getWidth).max, 8)
    private val insnWidth  = roundUp(traces.map(_.insn.getWidth).max, 8)
    private val causeWidth = roundUp(traces.map(_.cause.getWidth).max, 8)
    private val wdataWidth = roundUp(traces.map(t => if (t.wdata.isDefined) t.wdata.get.getWidth else 0).max, 8)

    // hack since for some reason padding a bool doesn't work...
    def boolPad(in: Bool, size: Int): UInt = {
      val temp = Wire(UInt(size.W))
      temp := in.asUInt
      temp
    }

    // matches order of TracedInstruction in CSR.scala
    val paddedTraces = traces.map { trace =>
      val pre_cat = Cat(
        trace.cause.pad(causeWidth),
        boolPad(trace.interrupt, 8),
        boolPad(trace.exception, 8),
        trace.priv.asUInt.pad(8),
        trace.insn.pad(insnWidth),
        trace.iaddr.pad(iaddrWidth),
        boolPad(trace.valid, 8),
      )

      if (wdataWidth == 0) {
        pre_cat
      } else {
        Cat(trace.wdata.get.pad(wdataWidth), pre_cat)
      }
    }

    val maxTraceSize        = paddedTraces.map(t => t.getWidth).max
    val outDataSzBits       = streamEnq.bits.getWidth
    val totalTracesPerToken = (outDataSzBits / maxTraceSize).toInt
    val bitsPerTrace        = roundUp(outDataSzBits / totalTracesPerToken, 8)

    require(
      maxTraceSize < bitsPerTrace,
      f"All instruction trace bits (i.e. valid, pc, instBits...) (${maxTraceSize}b) must fit in ${bitsPerTrace}b",
    )
    require(
      bitsPerTrace * totalTracesPerToken <= outDataSzBits,
      f"All traces must fit in single token (${bitsPerTrace * totalTracesPerToken} > ${outDataSzBits})",
    )

    val armCount = (traces.length + totalTracesPerToken - 1) / totalTracesPerToken

    // Literally each arm of the mux, these are directly the bits that get put into the bump
    val allStreamBits =
      paddedTraces.grouped(totalTracesPerToken).toSeq.map(grp => Cat(grp.map(t => t.asUInt.pad(bitsPerTrace)).reverse))

    // Number of bits to use for the counter, the +1 is required because the counter will count 1 past the number of arms
    val counterBits = log2Ceil(armCount + 1)

    // This counter acts to select the mux arm
    val counter = RegInit(0.U(counterBits.W))

    // The main mux where the input arms are different possible valid traces, and the output goes to streamEnq
    val streamMux = MuxLookup(counter, allStreamBits(0), Seq.tabulate(armCount)(x => x.U -> allStreamBits(x)))

    // a parallel set of arms to a parallel mux, true if any instructions in the arm are valid (OR reduction)
    val anyValid =
      traces
        .grouped(totalTracesPerToken)
        .toSeq
        .map(arm => arm.map(trace => trace.valid | trace.exception | (trace.cause =/= 0.U)).reduce((a, b) => (a | b)))

    // all of the valids of the larger indexed arms are OR reduced
    val anyValidRemain    =
      Seq.tabulate(armCount)(idx => (idx until armCount).map(x => anyValid(x)).reduce((a, b) => (a | b)))
    val anyValidRemainMux = MuxLookup(counter, false.B, Seq.tabulate(armCount)(x => x.U -> anyValidRemain(x)))

    streamEnq.bits := streamMux

    val maybeFire = !anyValidRemainMux || (counter === (armCount - 1).U)
    val maybeEnq  = anyValidRemainMux

    val commonPredicates = Seq(hPort.toHost.hValid, streamEnq.ready)
    val do_enq_helper    = DecoupledHelper((maybeEnq +: commonPredicates): _*)
    val do_fire_helper   = DecoupledHelper((maybeFire +: commonPredicates): _*)

    // Note, if we dequeue a token that wins out over the increment below
    when(do_fire_helper.fire()) {
      counter := 0.U
    }.elsewhen(do_enq_helper.fire()) {
      counter := counter + 1.U
    }

    streamEnq.valid       := do_enq_helper.fire(streamEnq.ready)
    hPort.toHost.hReady   := do_fire_helper.fire(hPort.toHost.hValid)
    hPort.fromHost.hValid := true.B // this is uni-directional. we don't drive tokens back to target

    genCRFile()

    // modify the output header file
    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(
        base,
        sb,
        "cospike_t",
        "cospike",
        Seq(
          UInt32(iaddrWidth),
          UInt32(insnWidth),
          UInt32(causeWidth),
          UInt32(wdataWidth),
          UInt32(traces.length),
          UInt32(bitsPerTrace),
          CStrLit(params.cfg.isa),
          UInt32(params.cfg.vlen),
          CStrLit(params.cfg.priv),
          UInt32(params.cfg.pmpregions),
          UInt64(params.cfg.mem0_base),
          UInt64(params.cfg.mem0_size),
          UInt32(params.cfg.nharts),
          CStrLit(params.cfg.bootrom),
          UInt32(params.hartid),
          UInt32(toHostStreamIdx),
          UInt32(toHostCPUQueueDepth),
        ),
        hasStreams = true,
      )
    }

    // general information printout
    println(s"Cospike Bridge Information")
    println(s"  Total Inst. Traces (i.e. Commit Width): ${traces.length}")
    println(s"  Total Traces Per Token: ${totalTracesPerToken}")
  }
}
