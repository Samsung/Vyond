// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard.examples

import chisel3.util._

import org.chipsalliance.cde.config._

import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

import worldguard._

case class WGMRocketTileAttachParams(
  tileParams: RocketTileParams,
  crossingParams: RocketCrossingParams,
  wgmarkerAttachParams: WGMarkerAttachParams
) extends CanAttachTile
{ //this: BaseSubsystem =>
  type TileType = RocketTile
  /** Connect the port where the tile is the master to Worldguard Marker
   *  and connect the marker to a TileLink interconnect. The marker has control register router connected to CBUS
   **/
  override def connectMasterPorts(domain: TilePRCIDomain[TileType], context: Attachable): Unit = {
    implicit val p = context.p
    val dataBus = context.locateTLBusWrapper(crossingParams.master.where)
    
    val wgm = wgmarkerAttachParams.attachTo(context)
    dataBus.coupleFrom(tileParams.baseName) { bus =>
        bus :=* wgm.wgm_node :=* crossingParams.master.injectNode(context) :=* domain.crossMasterPort(crossingParams.crossingType)
    }
  }
}
