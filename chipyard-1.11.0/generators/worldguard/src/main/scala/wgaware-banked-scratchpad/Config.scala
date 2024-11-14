// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink.{TLBusWrapperTopology}
import freechips.rocketchip.diplomacy.{BufferParams, AddressSet}

class WithWGScratchpad(
  base: BigInt = 0x80000000L,
  size: BigInt = (4 << 20),
  banks: Int = 1,
  partitions: Int = 1,
  busWhere: TLBusWrapperLocation = SBUS,
  subBanks: Int = 1,
  buffer: BufferParams = BufferParams.none,
  outerBuffer: BufferParams = BufferParams.none
) extends Config((site, here, up) => {
  case WGBankedScratchpadKey => up(WGBankedScratchpadKey) ++ (0 until partitions).map { pa => WGBankedScratchpadParams(
    base + pa * (size / partitions),
    size / partitions,
    busWhere = busWhere,
    name = s"${busWhere.name}-scratchpad",
    banks = banks,
    buffer = buffer,
    outerBuffer = outerBuffer,
    subBanks = subBanks
  )}
})

class WithMbusWGScratchpad(base: BigInt = 0x80000000L, size: BigInt = (4 << 20), banks: Int = 1, partitions: Int = 1, subBanks: Int = 1) extends
    WithWGScratchpad(base, size, banks, partitions, MBUS, subBanks)
