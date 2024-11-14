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
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._

/**
 * Create multiple copies of an Ibex tile (and thus a core).
 * Override with the default mixins to control all params of the tiles.
 *
 * @param n amount of tiles to duplicate
 */
class WithNIbexCores(n: Int = 1) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = up(NumTiles)
    (0 until n).map { i =>
      IbexTileAttachParams(
        tileParams = IbexTileParams(tileId = i + idOffset),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  case SystemBusKey => up(SystemBusKey, site).copy(beatBytes = 4)
  case XLen => 32
  case NumTiles => up(NumTiles) + n
})
