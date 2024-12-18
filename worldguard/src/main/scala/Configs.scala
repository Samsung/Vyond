// See LICENSE for license details.

/**
 * WorldGuard configuration file
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */


package worldguard

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

import worldguard.examples._
import worldguard.rocket._

case object NWorlds extends Field[Int](4)

class WithWorldGuard(nWorlds: Int, nSlots: Int) extends Config((site, here, up) => {
  case NWorlds => nWorlds
  case UseWGTLCustomField => true
  case WGMarkerBaseAddressKey => BigInt(0x2100000)
  case WGPeripheryKey=> {
    Some(
      (
      WGPeripheryParams(
        address   = 0x2020000,
        size      = 4096,
        width     = 32),
      WGCheckerParams(
        postfix = "wgpexample",
        mwid      = nWorlds - 1,
        widWidth  = log2Ceil(nWorlds),
        nSlots    = nSlots,
        address   = 0x2030000,
        size      = 4096)
      )
    )
  }

  case WGPLICKey => {
    Some(
      (
      PLICParams(),
      WGCheckerParams(
        postfix = "wgpplic",
        mwid      = nWorlds - 1,
        widWidth  = log2Ceil(nWorlds),
        nSlots    = nSlots,
        address   = 0x2040000,
        size      = 4096)
      )
    )
  }

  case WGBootROMKey => {
    Some(
      WGCheckerParams(
        postfix = "wgpbootrom",
        mwid      = nWorlds - 1,
        widWidth  = log2Ceil(nWorlds),
        nSlots    = nSlots,
        address   = 0x2050000,
        size      = 4096)
    )
  }
})

class WithNBigRocketCoresWithWGM(
  n: Int,
  location: HierarchicalLocation,
  crossing: RocketCrossingParams,
) extends Config((site, here, up) => {
  case TilesLocated(`location`) => {
    require (n <= 4)
    val nMaxWids = 4
    val prev = up(TilesLocated(`location`), site)
    val idOffset = up(NumTiles)
    val big = RocketTileParams(
      core   = RocketCoreParams(mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => WGMRocketTileAttachParams(
      big.copy(tileId = i + idOffset),
      crossing,
      WGMarkerAttachParams(
        WGMarkerParams(
          postfix = s"rockettile_${i+idOffset}",
          wid = nMaxWids-i-1,
          widWidth = log2Ceil(site(NWorlds)),
          address = site(WGMarkerBaseAddressKey) + BigInt(0x100000 * i),
          size = 4096
        )
      )
    )) ++ prev
  }
  case NumTiles => up(NumTiles) + n
}) {
  def this(n: Int, location: HierarchicalLocation = InSubsystem) = this(n, location, RocketCrossingParams(
    master = HierarchicalElementMasterPortParams.locationDefault(location),
    slave = HierarchicalElementSlavePortParams.locationDefault(location),
    mmioBaseAddressPrefixWhere = location match {
      case InSubsystem => CBUS
      case InCluster(clusterId) => CCBUS(clusterId)
    }
  ))
}

class WithWGRocketNBigCores(
  n: Int,
  location: HierarchicalLocation,
  crossing: RocketCrossingParams,
) extends Config((site, here, up) => {
  case TilesLocated(`location`) => {
    val prev = up(TilesLocated(`location`), site)
    val idOffset = up(NumTiles)
    val big = WGRocketTileParams(
      core   = RocketCoreParams(mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))))
    List.tabulate(n)(i => WGAwareRocketTileAttachParams(
      big.copy(tileId = i + idOffset),
      crossing
    )) ++ prev
  }
  case NumTiles => up(NumTiles) + n
}) {
  def this(n: Int, location: HierarchicalLocation = InSubsystem) = this(n, location, RocketCrossingParams(
    master = HierarchicalElementMasterPortParams.locationDefault(location),
    slave = HierarchicalElementSlavePortParams.locationDefault(location),
    mmioBaseAddressPrefixWhere = location match {
      case InSubsystem => CBUS
      case InCluster(clusterId) => CCBUS(clusterId)
    }
  ))
}

class WithOneWGAwareRocketThreeRocketWithWGMarker(
  location: HierarchicalLocation,
  crossing: RocketCrossingParams,
) extends Config((site, here, up) => {
  case TilesLocated(`location`) => {
    val nMaxWids = 4
    val prev = up(TilesLocated(`location`), site)
    val idOffset = up(NumTiles)

    // Need a config for nWorld
    val wgaware = WGRocketTileParams(
      core   = RocketCoreParams(mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))))
   
    val rocketWithWGM = RocketTileParams(
      core   = RocketCoreParams(mulDiv = Some(MulDivParams(
        mulUnroll = 8,
        mulEarlyOut = true,
        divEarlyOut = true))),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))))

    List.tabulate(4)(i => {
      i match {
        case 3 => {
          WGAwareRocketTileAttachParams(wgaware.copy(tileId = i + idOffset), crossing)
        }
        case _ => {
          WGMRocketTileAttachParams(
            rocketWithWGM.copy(tileId = i + idOffset),
            crossing,
            WGMarkerAttachParams(
              WGMarkerParams(
                postfix = s"rockettile_${i+idOffset}",
                wid = i,
                widWidth = log2Ceil(site(NWorlds)),
                address = site(WGMarkerBaseAddressKey) + BigInt(0x100000 * i),
                size = 4096
              )
            )
          )
        }
      }
    }) ++ prev
  }
  case NumTiles => up(NumTiles) + 4
}) {
  def this(location: HierarchicalLocation = InSubsystem) = this(location, RocketCrossingParams(
    master = HierarchicalElementMasterPortParams.locationDefault(location),
    slave = HierarchicalElementSlavePortParams.locationDefault(location),
    mmioBaseAddressPrefixWhere = location match {
      case InSubsystem => CBUS
      case InCluster(clusterId) => CCBUS(clusterId)
    }
  ))
}
