// See LICENSE for license details.

/**
 * This file defines a custom field "wid" to Tilelink Messages.
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field}
import freechips.rocketchip.util._

case object UseWGTLCustomField extends Field[Boolean](false)

class WGTLCustomFieldBundle(width: Int) extends Bundle {
  val wid = UInt(width.W)
}
case object WGTLCustomFieldKey extends ControlKey[WGTLCustomFieldBundle]("wgtlcustomfield")
case class WGTLCustomField(width: Int) extends BundleField[WGTLCustomFieldBundle](
  WGTLCustomFieldKey, Output((new WGTLCustomFieldBundle(width))), x => {
  // FIXME: Now it is not secure becuat WID 0 can be used for the current design.
  // WID 0 must not be used in order to identify that worldguard is not enabled or it has an expected case if wid 0.
  x.wid := 0x0.U
})
