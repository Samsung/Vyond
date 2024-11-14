// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard.rocket

import chisel3._
import chisel3.util._
import chisel3.util.HasBlackBoxResource
import chisel3.experimental.IntParam
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.InOrderArbiter

import worldguard._

case object BuildWGRoCC extends Field[Seq[Parameters => WGLazyRoCC]](Nil)

class WGRoCCCommand(implicit p: Parameters) extends CoreBundle()(p) {
  val inst = new RoCCInstruction
  val rs1 = Bits(xLen.W)
  val rs2 = Bits(xLen.W)
  val status = new MStatus
  val wid = UInt(log2Ceil(p(NWorlds)).W)
}

class WGRoCCCoreIO(val nRoCCCSRs: Int = 0)(implicit p: Parameters) extends CoreBundle()(p) {
  val cmd = Flipped(Decoupled(new WGRoCCCommand))
  val resp = Decoupled(new RoCCResponse)
  val mem = new WGHellaCacheIO
  val busy = Output(Bool())
  val interrupt = Output(Bool())
  val exception = Input(Bool())
  val csrs = Flipped(Vec(nRoCCCSRs, new CustomCSRIO))
}

class WGRoCCIO(val nPTWPorts: Int, nRoCCCSRs: Int)(implicit p: Parameters) extends WGRoCCCoreIO(nRoCCCSRs)(p) {
  val ptw = Vec(nPTWPorts, new WGTLBPTWIO)
  val fpu_req = Decoupled(new FPInput)
  val fpu_resp = Flipped(Decoupled(new FPResult))
}

/** Base classes for Diplomatic TL2 RoCC units **/
abstract class WGLazyRoCC(
  val opcodes: OpcodeSet,
  val nPTWPorts: Int = 0,
  val usesFPU: Boolean = false,
  val roccCSRs: Seq[CustomCSR] = Nil
)(implicit p: Parameters) extends LazyModule {
  val module: WGLazyRoCCModuleImp
  require(roccCSRs.map(_.id).toSet.size == roccCSRs.size)
  val atlNode: TLNode = TLIdentityNode()
  val tlNode: TLNode = TLIdentityNode()
}

class WGLazyRoCCModuleImp(outer: WGLazyRoCC) extends LazyModuleImp(outer) {
  val io = IO(new WGRoCCIO(outer.nPTWPorts, outer.roccCSRs.size))
  io := DontCare
}

/** Mixins for including RoCC **/

trait HasWGLazyRoCC extends CanHaveWGPTW { this: BaseTile =>
  val roccs = p(BuildWGRoCC).map(_(p))
  val roccCSRs = roccs.map(_.roccCSRs) // the set of custom CSRs requested by all roccs
  require(roccCSRs.flatten.map(_.id).toSet.size == roccCSRs.flatten.size,
    "LazyRoCC instantiations require overlapping CSRs")
  roccs.map(_.atlNode).foreach { atl => tlMasterXbar.node :=* atl }
  roccs.map(_.tlNode).foreach { tl => tlOtherMastersNode :=* tl }

  nPTWPorts += roccs.map(_.nPTWPorts).sum
  nDCachePorts += roccs.size
}

trait HasWGLazyRoCCModule extends CanHaveWGPTWModule
    with HasCoreParameters { this: WGRocketTileModuleImp with HasWGFpuOpt =>

  val (respArb, cmdRouter) = if(outer.roccs.nonEmpty) {
    val respArb = Module(new RRArbiter(new RoCCResponse()(outer.p), outer.roccs.size))
    val cmdRouter = Module(new RoccCommandRouter(outer.roccs.map(_.opcodes))(outer.p))
    outer.roccs.zipWithIndex.foreach { case (rocc, i) =>
      rocc.module.io.ptw ++=: ptwPorts
      rocc.module.io.cmd <> cmdRouter.io.out(i)
      val dcIF = Module(new WGSimpleHellaCacheIF()(outer.p))
      dcIF.io.requestor <> rocc.module.io.mem
      dcachePorts += dcIF.io.cache
      respArb.io.in(i) <> Queue(rocc.module.io.resp)
    }

    fpuOpt foreach { fpu =>
      val nFPUPorts = outer.roccs.count(_.usesFPU)
      if (usingFPU && nFPUPorts > 0) {
        val fpArb = Module(new InOrderArbiter(new FPInput()(outer.p), new FPResult()(outer.p), nFPUPorts))
        val fp_rocc_ios = outer.roccs.filter(_.usesFPU).map(_.module.io)
        fpArb.io.in_req <> fp_rocc_ios.map(_.fpu_req)
        fp_rocc_ios.zip(fpArb.io.in_resp).foreach {
          case (rocc, arb) => rocc.fpu_resp <> arb
        }
        fpu.io.cp_req <> fpArb.io.out_req
        fpArb.io.out_resp <> fpu.io.cp_resp
      } else {
        fpu.io.cp_req.valid := false.B
        fpu.io.cp_resp.ready := false.B
      }
    }
    (Some(respArb), Some(cmdRouter))
  } else {
    (None, None)
  }
  val roccCSRIOs = outer.roccs.map(_.module.io.csrs)
}
