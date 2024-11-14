// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._

import freechips.rocketchip.subsystem._

///** Specifies the size and width of external memory ports */
//case class MasterPortParams(
//  base: BigInt,
//  size: BigInt,
//  beatBytes: Int,
//  idBits: Int,
//  maxXferBytes: Int = 256,
//  executable: Boolean = true)
//
///** Specifies the width of external slave ports */
//case class SlavePortParams(beatBytes: Int, idBits: Int, sourceBits: Int)
//// if incohBase is set, creates an incoherent alias for the region that hangs off the sbus
//case class MemoryPortParams(master: MasterPortParams, nMemoryChannels: Int, incohBase: Option[BigInt] = None)
//
//case object ExtMem extends Field[Option[MemoryPortParams]](None)
//case object ExtBus extends Field[Option[MasterPortParams]](None)
//case object ExtIn extends Field[Option[SlavePortParams]](None)
//
/////// The following traits add ports to the sytem, in some cases converting to different interconnect standards

/** Adds a port to the system intended to master an AXI4 DRAM controller. */
trait CanHaveWGMasterAXI4MemPort { this: BaseSubsystem =>
  private val memPortParamsOpt = p(ExtMem)
  private val portName = "axi4"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val memAXI4Node = AXI4SlaveNode(memPortParamsOpt.map({ case MemoryPortParams(memPortParams, nMemoryChannels, _) =>
    Seq.tabulate(nMemoryChannels) { channel =>
      val base = AddressSet.misaligned(memPortParams.base, memPortParams.size)
      val filter = AddressSet(channel * mbus.blockBytes, ~((nMemoryChannels-1) * mbus.blockBytes))

      AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = base.flatMap(_.intersect(filter)),
          resources     = device.reg,
          regionType    = RegionType.UNCACHED, // cacheable
          executable    = true,
          supportsWrite = TransferSizes(1, mbus.blockBytes),
          supportsRead  = TransferSizes(1, mbus.blockBytes),
          interleavedId = Some(0))), // slave does not interleave read responses
        beatBytes = memPortParams.beatBytes)
    }
  }).toList.flatten)

  for (i <- 0 until memAXI4Node.portParams.size) {

    val mem_bypass_xbar = mbus { TLXbar() }

    // Create an incoherent alias for the AXI4 memory
    memPortParamsOpt.foreach(memPortParams => {
      memPortParams.incohBase.foreach(incohBase => {
        val cohRegion = AddressSet(0, incohBase-1)
        val incohRegion = AddressSet(incohBase, incohBase-1)
        val replicator = sbus {
          val replicator = LazyModule(new RegionReplicator(ReplicatedRegion(cohRegion, cohRegion.widen(incohBase))))
          val prefixSource = BundleBridgeSource[UInt](() => UInt(1.W))
          replicator.prefix := prefixSource
          // prefix is unused for TL uncached, so this is ok
          InModuleBody { prefixSource.bundle := 0.U(1.W) }
          replicator
        }
        sbus.coupleTo(s"memory_controller_bypass_port_named_$portName") {
          (mbus.crossIn(mem_bypass_xbar)(ValName("bus_xing"))(p(SbusToMbusXTypeKey))
            := TLWidthWidget(sbus.beatBytes)
            := replicator.node
            := TLFilter(TLFilter.mSubtract(cohRegion))
            := TLFilter(TLFilter.mResourceRemover)
            := _
          )
        }
      })
    })

    if (p(UseWGTLCustomField)) {
      val wgcParams = WGCheckerParams(
        postfix = s"wgp_memport_${i}",
        mwid      = p(NWorlds) - 1,
        widWidth  = log2Ceil(p(NWorlds)),
        nSlots    = 8,
        address   = 0x2060000 /*base*/ + 0x10000 * i,
        size      = 4096,
        lgMaxSize = 6,
        granularity = 64,
        lgAlign = 6)
      val wgc = WGCheckerAttachParams(wgcParams).attachTo(this)

      mbus.coupleTo(s"memory_controller_port_named_$portName") {
        (memAXI4Node
          := AXI4UserYanker()
          := AXI4IdIndexer(idBits)
          := TLToAXI4()
          := TLWidthWidget(mbus.beatBytes)
          := mem_bypass_xbar
          := wgc.wgc_node
          := _
          )
      }
    } else {
      mbus.coupleTo(s"memory_controller_port_named_$portName") {
        (memAXI4Node
          := AXI4UserYanker()
          := AXI4IdIndexer(idBits)
          := TLToAXI4()
          := TLWidthWidget(mbus.beatBytes)
          := mem_bypass_xbar
          := _
          )
      }
    }
  }

  val mem_axi4 = InModuleBody { memAXI4Node.makeIOs() }
}
