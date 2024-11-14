// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard.rocket

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config._

import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.prci.{ClockSinkParameters}

import worldguard.rocket._

case class WGAwareRocketTileAttachParams(
  tileParams: WGRocketTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile { type TileType = WGRocketTile }


case class WGRocketTileParams(
    core: RocketCoreParams = RocketCoreParams(),
    icache: Option[ICacheParams] = Some(ICacheParams()),
    dcache: Option[DCacheParams] = Some(DCacheParams()),
    btb: Option[BTBParams] = Some(BTBParams()),
    dataScratchpadBytes: Int = 0,
    tileId: Int = 0,
    beuAddr: Option[BigInt] = None,
    blockerCtrlAddr: Option[BigInt] = None,
    clockSinkParams: ClockSinkParameters = ClockSinkParameters(),
    boundaryBuffers: Option[RocketTileBoundaryBufferParams] = None
    ) extends InstantiableTileParams[WGRocketTile] {
  require(icache.isDefined)
  require(dcache.isDefined)
  val baseName = "wgaware_rockettile"
  val uniqueName = s"${baseName}_$tileId"
  def instantiate(crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): WGRocketTile = {
    new WGRocketTile(this, crossing, lookup)
  }
}

class WGRocketTile private(
      val rocketParams: WGRocketTileParams,
      crossing: ClockCrossingType,
      lookup: LookupByHartIdImpl,
      q: Parameters)
    extends BaseTile(rocketParams, crossing, lookup, q)
    with SinksExternalInterrupts
    with SourcesExternalNotifications
    with HasWGLazyRoCC  // implies CanHaveSharedFPU with CanHavePTW with HasHellaCache
    with HasWGHellaCache
    with HasWGICacheFrontend
{
  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(params: WGRocketTileParams, crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  val intOutwardNode = rocketParams.beuAddr map { _ => IntIdentityNode() }
  val slaveNode = TLIdentityNode()
  val masterNode = visibilityNode

  val dtim_adapter = tileParams.dcache.flatMap { d => d.scratch.map { s =>
    LazyModule(new WGScratchpadSlavePort(AddressSet.misaligned(s, d.dataScratchpadBytes), lazyCoreParamsView.coreDataBytes, tileParams.core.useAtomics && !tileParams.core.useAtomicsOnlyForIO))
  }}
  dtim_adapter.foreach(lm => connectTLSlave(lm.node, lm.node.portParams.head.beatBytes))

  val bus_error_unit = rocketParams.beuAddr map { a =>
    val beu = LazyModule(new BusErrorUnit(new L1BusErrors, BusErrorUnitParams(a)))
    intOutwardNode.get := beu.intNode
    connectTLSlave(beu.node, xBytes)
    beu
  }

  val tile_master_blocker =
    tileParams.blockerCtrlAddr
      .map(BasicBusBlockerParams(_, xBytes, masterPortBeatBytes, deadlock = true))
      .map(bp => LazyModule(new BasicBusBlocker(bp)))

  tile_master_blocker.foreach(lm => connectTLSlave(lm.controlNode, xBytes))

  // TODO: this doesn't block other masters, e.g. RoCCs
  tlOtherMastersNode := tile_master_blocker.map { _.node := tlMasterXbar.node } getOrElse { tlMasterXbar.node }
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  nDCachePorts += 1 /*core */ + (dtim_adapter.isDefined).toInt

  val dtimProperty = dtim_adapter.map(d => Map(
    "sifive,dtim" -> d.device.asProperty)).getOrElse(Nil)

  val itimProperty = frontend.icache.itimProperty.toSeq.flatMap(p => Map("sifive,itim" -> p))

  val beuProperty = bus_error_unit.map(d => Map(
          "sifive,buserror" -> d.device.asProperty)).getOrElse(Nil)

  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("sifive,rocket0", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++ cpuProperties ++ nextLevelCacheProperty
                  ++ tileProperties ++ dtimProperty ++ itimProperty ++ beuProperty)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(tileId))
  }

  override lazy val module = new WGRocketTileModuleImp(this)

  override def makeMasterBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) = (rocketParams.boundaryBuffers, crossing) match {
    case (Some(RocketTileBoundaryBufferParams(true )), _)                   => TLBuffer()
    case (Some(RocketTileBoundaryBufferParams(false)), _: RationalCrossing) => TLBuffer(BufferParams.none, BufferParams.flow, BufferParams.none, BufferParams.flow, BufferParams(1))
    case _ => TLBuffer(BufferParams.none)
  }

  override def makeSlaveBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) = (rocketParams.boundaryBuffers, crossing) match {
    case (Some(RocketTileBoundaryBufferParams(true )), _)                   => TLBuffer()
    case (Some(RocketTileBoundaryBufferParams(false)), _: RationalCrossing) => TLBuffer(BufferParams.flow, BufferParams.none, BufferParams.none, BufferParams.none, BufferParams.none)
    case _ => TLBuffer(BufferParams.none)
  }
}

class WGRocketTileModuleImp(outer: WGRocketTile) extends BaseTileModuleImp(outer)
    with HasWGFpuOpt
    with HasWGLazyRoCCModule
    with HasWGICacheFrontendModule {
  Annotated.params(this, outer.rocketParams)

  val core = Module(new WGRocket(outer)(outer.p))

  // reset vector is connected in the Frontend to s2_pc
  core.io.reset_vector := DontCare

  // Report unrecoverable error conditions; for now the only cause is cache ECC errors
  outer.reportHalt(List(outer.dcache.module.io.errors))

  // Report when the tile has ceased to retire instructions; for now the only cause is clock gating
  outer.reportCease(outer.rocketParams.core.clockGate.option(
    !outer.dcache.module.io.cpu.clock_enabled &&
    !outer.frontend.module.io.cpu.clock_enabled &&
    !ptw.io.dpath.clock_enabled &&
    core.io.cease))

  outer.reportWFI(Some(core.io.wfi))

  outer.decodeCoreInterrupts(core.io.interrupts) // Decode the interrupt vector

  outer.bus_error_unit.foreach { beu =>
    core.io.interrupts.buserror.get := beu.module.io.interrupt
    beu.module.io.errors.dcache := outer.dcache.module.io.errors
    beu.module.io.errors.icache := outer.frontend.module.io.errors
  }

  core.io.interrupts.nmi.foreach { nmi => nmi := outer.nmiSinkNode.get.bundle }

  // Pass through various external constants and reports that were bundle-bridged into the tile
  outer.traceSourceNode.bundle <> core.io.trace
  core.io.traceStall := outer.traceAuxSinkNode.bundle.stall
  outer.bpwatchSourceNode.bundle <> core.io.bpwatch
  core.io.hartid := outer.hartIdSinkNode.bundle
  require(core.io.hartid.getWidth >= outer.hartIdSinkNode.bundle.getWidth,
    s"core hartid wire (${core.io.hartid.getWidth}b) truncates external hartid wire (${outer.hartIdSinkNode.bundle.getWidth}b)")

  // Connect the core pipeline to other intra-tile modules
  outer.frontend.module.io.cpu <> core.io.imem
  dcachePorts += core.io.dmem // TODO outer.dcachePorts += () => module.core.io.dmem ??
  fpuOpt foreach { fpu =>
    core.io.fpu :<>= fpu.io.waiveAs[FPUCoreIO](_.cp_req, _.cp_resp)
    fpu.io.cp_req := DontCare
    fpu.io.cp_resp := DontCare
  }
  if (fpuOpt.isEmpty) {
    core.io.fpu := DontCare
  }
  core.io.ptw <> ptw.io.dpath

  // Connect the coprocessor interfaces
  if (outer.roccs.size > 0) {
    cmdRouter.get.io.in <> core.io.rocc.cmd
    outer.roccs.foreach{ lm =>
      lm.module.io.exception := core.io.rocc.exception
      lm.module.io.fpu_req.ready := DontCare
      lm.module.io.fpu_resp.valid := DontCare
      lm.module.io.fpu_resp.bits.data := DontCare
      lm.module.io.fpu_resp.bits.exc := DontCare
    }
    core.io.rocc.resp <> respArb.get.io.out
    core.io.rocc.busy <> (cmdRouter.get.io.busy || outer.roccs.map(_.module.io.busy).reduce(_ || _))
    core.io.rocc.interrupt := outer.roccs.map(_.module.io.interrupt).reduce(_ || _)
    (core.io.rocc.csrs zip roccCSRIOs.flatten).foreach { t => t._2 <> t._1 }
  } else {
    // tie off
    core.io.rocc.cmd.ready := false.B
    core.io.rocc.resp.valid := false.B
    core.io.rocc.resp.bits := DontCare
    core.io.rocc.busy := DontCare
    core.io.rocc.interrupt := DontCare
  }
  // Dont care mem since not all RoCC need accessing memory
  core.io.rocc.mem := DontCare

  // Rocket has higher priority to DTIM than other TileLink clients
  outer.dtim_adapter.foreach { lm => dcachePorts += lm.module.io.dmem }

  // TODO eliminate this redundancy
  val h = dcachePorts.size
  val c = core.dcacheArbPorts
  val o = outer.nDCachePorts
  require(h == c, s"port list size was $h, core expected $c")
  require(h == o, s"port list size was $h, outer counted $o")
  // TODO figure out how to move the below into their respective mix-ins
  dcacheArb.io.requestor <> dcachePorts.toSeq
  ptw.io.requestor <> ptwPorts.toSeq
}

trait HasWGFpuOpt { this: WGRocketTileModuleImp =>
  val fpuOpt = outer.tileParams.core.fpu.map(params => Module(new FPU(params)(outer.p)))
}
