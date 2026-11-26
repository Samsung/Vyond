// See LICENSE.SiFive for license details.
// See LICENSE for license details.

/**
 * WG-aware control register node: identical to freechips.rocketchip.tilelink.TLRegisterNode
 * except its port negotiates WGTLCustomFieldKey as a request key, so a WGChecker/WGMarker's
 * regmap can see the requester's WID on the control port (stock TLRegisterNode never
 * declares that key, so it never reaches a.bits.user there).
 *
 * concurrency defaults to 0, same as stock RegisterRouterParams, which is what makes
 * this safe: with no queueing between the "a" channel and the RegField write, the WID
 * read out of a.bits.user is guaranteed to belong to the exact request being serviced
 * this cycle, not some later one that has since arrived.
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}
// chipyard 1.11 / rocket-chip keeps the node classes and the resource/device
// types together in freechips.rocketchip.diplomacy. Newer trees split them into
// org.chipsalliance.diplomacy.nodes and freechips.rocketchip.resources.
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomacy.{AddressSet, TransferSizes, ValName,
  Device, Resource, ResourceBindings, SinkNode}
import freechips.rocketchip.regmapper.{RegField, RegMapper, RegMapperParams, RegMapperInput, RegisterRouter}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{ElaborationArtefacts, GenRegDescsAnno}

import scala.math.min

case class WGTLRegisterNode(
    address:     Seq[AddressSet],
    device:      Device,
    deviceKey:   String  = "reg/control",
    concurrency: Int     = 0,
    beatBytes:   Int     = 4,
    undefZero:   Boolean = true,
    executable:  Boolean = false)(
    implicit valName: ValName, p: Parameters)
  extends SinkNode(TLImp)(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address            = address,
      resources          = Seq(Resource(device, deviceKey)),
      executable         = executable,
      supportsGet        = TransferSizes(1, beatBytes),
      supportsPutPartial = TransferSizes(1, beatBytes),
      supportsPutFull    = TransferSizes(1, beatBytes),
      fifoId             = Some(0))), // requests are handled in order
    beatBytes  = beatBytes,
    minLatency = min(concurrency, 1),
    requestKeys = if (p(UseWGTLCustomField)) Seq(WGTLCustomFieldKey) else Seq()))) with TLFormatNode
{
  val size = 1 << log2Ceil(1 + address.map(_.max).max - address.map(_.base).min)
  require (size >= beatBytes)
  address.foreach { case a =>
    require (a.widen(size-1).base == address.head.widen(size-1).base,
      s"WGTLRegisterNode addresses (${address}) must be aligned to its size ${size}")
  }

  // The WID of the request currently being serviced by regmap(), i.e. the same
  // request whose data/mask/index feed the RegField this cycle. None if
  // UseWGTLCustomField is off, in which case callers should treat every write
  // as trusted (matches pre-WGC behavior).
  def reqWid: Option[UInt] = {
    val (bundleIn, _) = this.in(0)
    bundleIn.a.bits.user.lift(WGTLCustomFieldKey).map(_.wid)
  }

  // Calling this method causes the matching TL2 bundle to be
  // configured to route all requests to the listed RegFields.
  def regmap(mapping: RegField.Map*) = {
    val (bundleIn, edge) = this.in(0)
    val a = bundleIn.a
    val d = bundleIn.d

    val fields = TLRegisterRouterExtraField(edge.bundle.sourceBits, edge.bundle.sizeBits) +: a.bits.params.echoFields
    val params = RegMapperParams(log2Up(size/beatBytes), beatBytes, fields)
    val in = Wire(Decoupled(new RegMapperInput(params)))
    in.bits.read  := a.bits.opcode === TLMessages.Get
    in.bits.index := edge.addr_hi(a.bits)
    in.bits.data  := a.bits.data
    in.bits.mask  := a.bits.mask
    Connectable.waiveUnmatched(in.bits.extra, a.bits.echo) match {
      case (lhs, rhs) => lhs :<= rhs
    }

    val a_extra = in.bits.extra(TLRegisterRouterExtra)
    a_extra.source := a.bits.source
    a_extra.size   := a.bits.size

    // Invoke the register map builder
    val out = RegMapper(beatBytes, concurrency, undefZero, in, mapping:_*)

    // No flow control needed
    in.valid  := a.valid
    a.ready   := in.ready
    d.valid   := out.valid
    out.ready := d.ready

    // We must restore the size to enable width adapters to work
    val d_extra = out.bits.extra(TLRegisterRouterExtra)
    d.bits := edge.AccessAck(toSource = d_extra.source, lgSize = d_extra.size)

    // avoid a Mux on the data bus by manually overriding two fields
    d.bits.data := out.bits.data
    Connectable.waiveUnmatched(d.bits.echo, out.bits.extra) match {
      case (lhs, rhs) => lhs :<= rhs
    }

    d.bits.opcode := Mux(out.bits.read, TLMessages.AccessAckData, TLMessages.AccessAck)

    // Tie off unused channels
    bundleIn.b.valid := false.B
    bundleIn.c.ready := true.B
    bundleIn.e.ready := true.B

    genRegDescsJson(mapping:_*)
  }

  def genRegDescsJson(mapping: RegField.Map*): Unit = {
    // Dump out the register map for documentation purposes.
    val base = address.head.base
    val baseHex = s"0x${base.toInt.toHexString}"
    val name = s"${device.describe(ResourceBindings()).name}.At${baseHex}"
    val json = GenRegDescsAnno.serialize(base, name, mapping:_*)
    var suffix = 0
    while( ElaborationArtefacts.contains(s"${baseHex}.${suffix}.regmap.json")) {
      suffix = suffix + 1
    }
    ElaborationArtefacts.add(s"${baseHex}.${suffix}.regmap.json", json)

    val module = Module.currentModule.get.asInstanceOf[RawModule]
    GenRegDescsAnno.anno(
      module,
      base,
      mapping:_*)
  }
}

/** Like freechips.rocketchip.tilelink.HasTLControlRegMap, but backed by WGTLRegisterNode
  * so the control regmap can see the requester's WID. Mix into a WGChecker/WGMarker
  * (any RegisterRouter subclass) in place of HasTLControlRegMap. */
trait HasTLWGControlRegMap { this: RegisterRouter =>
  protected val controlNode = WGTLRegisterNode(
    address = address,
    device = device,
    deviceKey = "reg/control",
    concurrency = concurrency,
    beatBytes = beatBytes,
    undefZero = undefZero,
    executable = executable)

  // Externally, this helper should be used to connect the register control port to a bus
  val controlXing: TLInwardClockCrossingHelper = this.crossIn(controlNode)

  // Backwards-compatibility default node accessor with no clock crossing
  lazy val node: TLInwardNode = controlXing(NoCrossing)

  // Internally, this function should be used to populate the control port with registers
  protected def regmap(mapping: RegField.Map*): Unit = { controlNode.regmap(mapping:_*) }
}
