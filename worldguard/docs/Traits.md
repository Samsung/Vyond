
This repository provides parameters and traits for `WGM` and `WGC` so that users can write configs and traits for their own source and target.
### WGRocketTileAttachParams
`WGRocketTileAttachParams` extends `CanAttachTile` traits to create `WGM` and attach `AdapterNode` in between RocketTile and the system bus.

```scala
case class WGRocketTileAttachParams(
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
```

### CanHaveWGPLICOrPLIC
`CanHaveWGPLICOrPLIC` trait create `WGC` attached PLIC if `WGPLICKey` exists. Otherwise, the default PLIC is created with `WGC`.
```scala

trait CanHaveWGPPLICOrPlic { this: BaseSubsystem =>
  val (plicOpt, plicDomainOpt) = p(WGPLICKey) match {
    case Some((wgpParams, wgcParams)) => {
      val wgc = WGCheckerAttachParams(wgcParams).attachTo(this)
      val tlbus = locateTLBusWrapper(p(PLICAttachKey).slaveWhere)
      val plicDomainWrapper = tlbus.generateSynchronousDomain

      val plic = plicDomainWrapper { LazyModule(new TLPLIC(wgpParams, tlbus.beatBytes)) }
      plicDomainWrapper { plic.node := wgc.wgc_node := tlbus.coupleTo("plic") { TLFragmenter(tlbus.beatBytes, tlbus.blockBytes, holdFirstDeny = true) := _ } }
      plicDomainWrapper { plic.intnode :=* ibus.toPLIC }
      (Some(plic), Some(plicDomainWrapper))
    }
    case None => {
      p(PLICKey).map { params =>
        val tlbus = locateTLBusWrapper(p(PLICAttachKey).slaveWhere)
        val plicDomainWrapper = tlbus.generateSynchronousDomain

        val plic = plicDomainWrapper { LazyModule(new TLPLIC(params, tlbus.beatBytes)) }
        plicDomainWrapper { plic.node := tlbus.coupleTo("plic") { TLFragmenter(tlbus) := _ } }
        plicDomainWrapper { plic.intnode :=* ibus.toPLIC }

        (plic, plicDomainWrapper)
      }.unzip
    }
  }
}
```

## 5. Example Configs
The following configs demonstrait that sources and targets are created with `WGM` and `WGC` with parameters.
```scala

class WithWorldGuard(mwid: Int, widWidth: Int, nSlots: Int) extends Config((site, here, up) => {
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
        mwid      = mwid,
        widWidth  = widWidth,
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
        mwid      = mwid,
        widWidth  = widWidth,
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
        mwid      = mwid,
        widWidth  = widWidth,
        nSlots    = nSlots,
        address   = 0x2050000,
        size      = 4096)
    )
  }
})

```
