// See LICENSE.SiFive for license details.
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard.examples


import chisel3._
import org.chipsalliance.cde.config.{Field, Parameters}

import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._

import worldguard._

case object WGPLICKey extends Field[Option[(PLICParams, WGCheckerParams)]](None)

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
