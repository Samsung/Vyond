//******************************************************************************
// Copyright (c) 2021 - 2021, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Ibex Tile Wrapper
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package ibex

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam, RawParam}

import scala.collection.mutable.{ListBuffer}

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci.ClockSinkParameters

case class IbexCoreParams(
  //Defaults based on Ibex "small" configuration
  //See https://github.com/lowRISC/ibex for more information
  val bootFreqHz: BigInt = BigInt(1700000000),
  val pmpEnable: Int = 0,
  val pmpGranularity: Int = 0,
  val pmpNumRegions: Int = 4,
  val mhpmCounterNum: Int = 0,
  val mhpmCounterWidth: Int = 0,
  val rv32e: Int = 0,
  val rv32m: String = "RV32MFast",
  val rv32b: String = "RV32BNone",
  val regFile: String = "RegFileFF",
  val branchTargetALU: Int = 0,
  val wbStage: Int = 0,
  val branchPredictor: Int = 0,
  val dbgHwBreakNum: Int = 1,
  val dmHaltAddr: Int = 0x1A110800,
  val dmExceptionAddr: Int = 0x1A110808
) extends CoreParams {
  val useVM: Boolean = false
  val useHypervisor: Boolean = false
  val useUser: Boolean = true
  val useSupervisor: Boolean = false
  val useDebug: Boolean = true
  val useAtomics: Boolean = false
  val useAtomicsOnlyForIO: Boolean = false
  val useCompressed: Boolean = false
  override val useVector: Boolean = false
  val useSCIE: Boolean = false
  val useRVE: Boolean = true
  val mulDiv: Option[MulDivParams] = Some(MulDivParams()) // copied from Rocket
  val fpu: Option[FPUParams] = None //floating point not supported
  val fetchWidth: Int = 1
  val decodeWidth: Int = 1
  val retireWidth: Int = 2
  val instBits: Int = if (useCompressed) 16 else 32
  val nLocalInterrupts: Int = 15
  val nPMPs: Int = 0
  val nBreakpoints: Int = 0
  val useBPWatch: Boolean = false
  val nPerfCounters: Int = 29
  val haveBasicCounters: Boolean = true
  val haveFSDirty: Boolean = false
  val misaWritable: Boolean = false
  val haveCFlush: Boolean = false
  val nL2TLBEntries: Int = 0
  val mtvecInit: Option[BigInt] = Some(BigInt(0))
  val mtvecWritable: Boolean = true
  val nL2TLBWays: Int = 1
  val lrscCycles: Int = 80
  val mcontextWidth: Int = 0
  val scontextWidth: Int = 0
  val useNMI: Boolean = true
  val nPTECacheEntries: Int = 0
  val useBitManip: Boolean = false
  val useBitManipCrypto: Boolean = false
  val useCryptoNIST: Boolean = false
  val useCryptoSM: Boolean = false
  val traceHasWdata: Boolean = false
  val useConditionalZero: Boolean = false
}

case class IbexTileAttachParams(
  tileParams: IbexTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = IbexTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
}

case class IbexTileParams(
  name: Option[String] = Some("ibex_tile"),
  tileId: Int = 0,
  val core: IbexCoreParams = IbexCoreParams()
) extends InstantiableTileParams[IbexTile]
{
  val beuAddr: Option[BigInt] = None
  val blockerCtrlAddr: Option[BigInt] = None
  val btb: Option[BTBParams] = None
  val boundaryBuffers: Boolean = false
  val dcache: Option[DCacheParams] = None //no dcache
  val icache: Option[ICacheParams] = None //optional icache, currently in draft so turning option off
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  def instantiate(crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): IbexTile = {
    new IbexTile(this, crossing, lookup)
  }
  val baseName = name.getOrElse("ibex_tile")
  val uniqueName = s"${baseName}_$tileId"
}

class IbexTile private(
  val ibexParams: IbexTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters)
  extends BaseTile(ibexParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{

  def this(params: IbexTileParams, crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  //TileLink nodes
  val intOutwardNode = None
  val masterNode = visibilityNode
  val slaveNode = TLIdentityNode()

  tlOtherMastersNode := tlMasterXbar.node
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  override lazy val module = new IbexTileModuleImp(this)

  val portName = "ibex-mem-port"
  val node = TLIdentityNode()

  val dmemNode = TLClientNode(
    Seq(TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = portName,
        sourceId = IdRange(0, 1))))))

  val imemNode = TLClientNode(
    Seq(TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = portName,
        sourceId = IdRange(0, 1))))))

  tlMasterXbar.node := node := TLBuffer() := dmemNode
  tlMasterXbar.node := node := TLBuffer() := imemNode

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("lowRISC,ibex", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
                        cpuProperties ++
                        nextLevelCacheProperty ++
                        tileProperties)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(tileId))
  }

  def connectIbexInterrupts(debug: Bool, msip: Bool, mtip: Bool, meip: Bool) {
    val (interrupts, _) = intSinkNode.in(0)
    debug := interrupts(0)
    msip := interrupts(1)
    mtip := interrupts(2)
    meip := interrupts(3)
  }
}

class IbexTileModuleImp(outer: IbexTile) extends BaseTileModuleImp(outer){
  // annotate the parameters
  Annotated.params(this, outer.ibexParams)

  // convert string params into int params (since chisel rawparams are converted to strings
  // which prevents casting from string to enum in SV)
  // following matches ibex_pkg
  val rv32mInt: Option[Int] = outer.ibexParams.core.rv32m match {
    case "RV32MNone" => Some(0)
    case "RV32MSlow" => Some(1)
    case "RV32MFast" => Some(2)
    case "RV32MSingleCycle" => Some(3)
    case _ => None
  }
  require(rv32mInt.isDefined, "Invalid RV32M argument")

  val rv32bInt: Option[Int] = outer.ibexParams.core.rv32b match {
    case "RV32BNone" => Some(0)
    case "RV32BBalanced" => Some(1)
    case "RV32BFull" => Some(2)
    case _ => None
  }
  require(rv32bInt.isDefined, "Invalid RV32B argument")

  val regFileInt: Option[Int] = outer.ibexParams.core.regFile match {
    case "RegFileFF" => Some(0)
    case "RegFileFPGA" => Some(1)
    case "RegFileLatch" => Some(2)
    case _ => None
  }
  require(regFileInt.isDefined, "Invalid RegFile argument")

  val core = Module(new IbexCoreBlackbox(
    pmpEnable = outer.ibexParams.core.pmpEnable,
    pmpGranularity = outer.ibexParams.core.pmpGranularity,
    pmpNumRegions = outer.ibexParams.core.pmpNumRegions,
    mhpmCounterNum = outer.ibexParams.core.mhpmCounterNum,
    mhpmCounterWidth = outer.ibexParams.core.mhpmCounterWidth,
    rv32e = outer.ibexParams.core.rv32e,
    rv32m = rv32mInt.get,
    rv32b = rv32bInt.get,
    regfile = regFileInt.get,
    branchTargetALU = outer.ibexParams.core.branchTargetALU,
    wbStage = outer.ibexParams.core.wbStage,
    branchPredictor = outer.ibexParams.core.branchPredictor,
    dbgHwBreakNum = outer.ibexParams.core.dbgHwBreakNum,
    dmHaltAddr = outer.ibexParams.core.dmHaltAddr,
    dmExceptionAddr = outer.ibexParams.core.dmExceptionAddr
  ))

  //connect signals
  core.io.clk_i := clock
  core.io.rst_ni := ~reset.asBool
  core.io.test_en_i := false.B
  core.io.boot_addr_i := outer.resetVectorSinkNode.bundle
  core.io.hart_id_i := outer.hartIdSinkNode.bundle

  outer.connectIbexInterrupts(core.io.debug_req_i, core.io.irq_software_i, core.io.irq_timer_i, core.io.irq_external_i)
  core.io.irq_nm_i := 0.U //recoverable nmi, tying off
  core.io.irq_fast_i := 0.U //local interrupts, tying off

  // MEMORY
  // DMEM
  val (dmem, dmem_edge) = outer.dmemNode.out(0)

  val s_ready :: s_active :: s_inflight :: Nil = Enum(3)
  val dmem_state = RegInit(s_ready)

  val dmem_addr = Reg(UInt(32.W))
  val dmem_data = Reg(UInt(32.W))
  val dmem_mask = Reg(UInt(8.W))
  val byte_en = Reg(UInt(4.W))
  val num_bytes = Reg(UInt(3.W))
  val r_size = Reg(UInt(2.W))
  val w_size = Reg(UInt(2.W))
  r_size := 2.U

  when (dmem_state === s_ready && core.io.data_req_o) {
    dmem_state := s_active
    dmem_addr := core.io.data_addr_o + (PriorityEncoder(core.io.data_be_o) * core.io.data_we_o) //if write, shift address based on mask
    dmem_data := core.io.data_wdata_o
    byte_en := core.io.data_be_o
    dmem_mask := core.io.data_be_o
    w_size := PriorityEncoder(PopCount(core.io.data_be_o)) //log2Ceil
  }
  when (dmem_state === s_active && dmem.a.fire()) {
    dmem_state := s_inflight
  }
  when (dmem_state === s_inflight && dmem.d.fire()) {
    dmem_state := s_ready
  }
  dmem.a.valid := dmem_state === s_active
  core.io.data_gnt_i := dmem_state === s_ready && core.io.data_req_o
  dmem.d.ready := true.B
  core.io.data_rvalid_i := dmem.d.valid

  val dmem_get = dmem_edge.Get(0.U, dmem_addr, r_size)._2
  val dmem_put = dmem_edge.Put(0.U, dmem_addr, w_size, dmem_data, dmem_mask)._2

  dmem.a.bits := Mux(core.io.data_we_o, dmem_put, dmem_get)             //write or read depending on write enable
  core.io.data_rdata_i := dmem.d.bits.data                              //read data
  core.io.data_err_i := dmem.d.bits.corrupt | dmem.d.bits.denied        //set error

  //unused
  dmem.b.valid := false.B
  dmem.c.ready := true.B
  dmem.e.ready := true.B

  //IMEM
  val (imem, imem_edge) = outer.imemNode.out(0)
  val imem_state = RegInit(s_ready)

  val imem_addr = Reg(UInt(32.W))

  when (imem_state === s_ready && core.io.instr_req_o) {
    imem_state := s_active
    imem_addr := core.io.instr_addr_o
  }
  when (imem_state === s_active && imem.a.fire()) {
    imem_state := s_inflight
  }
  when (imem_state === s_inflight && imem.d.fire()) {
    imem_state := s_ready
  }

  imem.a.valid := imem_state === s_active
  core.io.instr_gnt_i := imem_state === s_ready
  imem.d.ready := true.B
  core.io.instr_rvalid_i := imem.d.valid

  val imem_get = imem_edge.Get(0.U, imem_addr, r_size)._2

  imem.a.bits := imem_get
  core.io.instr_rdata_i := imem.d.bits.data
  core.io.instr_err_i := imem.d.bits.corrupt | imem.d.bits.denied

  //unused
  imem.b.valid := false.B
  imem.c.ready := true.B
  imem.e.ready := true.B

  //used for icache, tie off
  core.io.ram_cfg_i_ram_cfg_en := 0.U
  core.io.ram_cfg_i_ram_cfg := 0.U
  core.io.ram_cfg_i_rf_cfg_en := 0.U
  core.io.ram_cfg_i_rf_cfg := 0.U

  //continuously fetch instructions
  core.io.fetch_enable_i := 1.U

  //DFT not used
  core.io.scan_rst_ni := 1.U
}
