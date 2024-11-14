// See LICENSE.SiFive for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard.examples

import chisel3._
import chisel3.util.log2Ceil
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._

import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}

import worldguard._

case object WGBootROMKey extends Field[Option[WGCheckerParams]](None)

object WGBootROM {
  /** BootROM.attach not only instantiates a TLROM and attaches it to the tilelink interconnect
    *    at a configurable location, but also drives the tiles' reset vectors to point
    *    at its 'hang' address parameter value.
    */
  def attach(
    params: BootROMParams,
    subsystem: BaseSubsystem with HasHierarchicalElements with HasTileInputConstants,
    where: TLBusWrapperLocation,
    wgc_node: TLAdapterNode)(implicit p: Parameters): TLROM = {
    val tlbus = subsystem.locateTLBusWrapper(where)
    val bootROMDomainWrapper = tlbus.generateSynchronousDomain.suggestName("bootrom_domain")

    val bootROMResetVectorSourceNode = BundleBridgeSource[UInt]()
    lazy val contents = {
      val romdata = Files.readAllBytes(Paths.get(params.contentFileName))
      val rom = ByteBuffer.wrap(romdata)
      rom.array() ++ subsystem.dtb.contents
    }

    val bootrom = bootROMDomainWrapper {
      LazyModule(new TLROM(params.address, params.size, contents, true, tlbus.beatBytes))
    }

    bootrom.node := wgc_node := tlbus.coupleTo("bootrom"){ TLFragmenter(tlbus.beatBytes, tlbus.blockBytes, holdFirstDeny = true) := _ }
    // Drive the `subsystem` reset vector to the `hang` address of this Boot ROM.
    subsystem.tileResetVectorNexusNode := bootROMResetVectorSourceNode
    InModuleBody {
      val reset_vector_source = bootROMResetVectorSourceNode.bundle
      require(reset_vector_source.getWidth >= params.hang.bitLength,
        s"BootROM defined with a reset vector (${params.hang})too large for physical address space (${reset_vector_source.getWidth})")
      bootROMResetVectorSourceNode.bundle := params.hang.U
    }
    bootrom
  }
}
