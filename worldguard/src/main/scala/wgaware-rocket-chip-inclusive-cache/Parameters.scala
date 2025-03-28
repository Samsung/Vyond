/*
 * Copyright 2019 SiFive, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You should have received a copy of LICENSE.Apache2 along with
 * this software. If not, you may obtain a copy at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard 

import chisel3._
import chisel3.util._
import chisel3.internal.sourceinfo.SourceInfo
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property.cover
import scala.math.{min,max}

import sifive.blocks.inclusivecache._

object WGMetaData
{
  val stateBits = 2
  def INVALID: UInt = 0.U(stateBits.W) // way is empty
  def BRANCH:  UInt = 1.U(stateBits.W) // outer slave cache is trunk
  def TRUNK:   UInt = 2.U(stateBits.W) // unique inner master cache is trunk
  def TIP:     UInt = 3.U(stateBits.W) // we are trunk, inner masters are branch

  // Does a request need trunk?
  def needT(opcode: UInt, param: UInt): Bool = {
    !opcode(2) ||
    (opcode === TLMessages.Hint && param === TLHints.PREFETCH_WRITE) ||
    ((opcode === TLMessages.AcquireBlock || opcode === TLMessages.AcquirePerm) && param =/= TLPermissions.NtoB)
  }
  // Does a request prove the client need not be probed?
  def skipProbeN(opcode: UInt, hintsSkipProbe: Boolean): Bool = {
    // Acquire(toB) and Get => is N, so no probe
    // Acquire(*toT) => is N or B, but need T, so no probe
    // Hint => could be anything, so probe IS needed, if hintsSkipProbe is enabled, skip probe the same client
    // Put* => is N or B, so probe IS needed
    opcode === TLMessages.AcquireBlock || opcode === TLMessages.AcquirePerm || opcode === TLMessages.Get || (opcode === TLMessages.Hint && hintsSkipProbe.B)
  }
  def isToN(param: UInt): Bool = {
    param === TLPermissions.TtoN || param === TLPermissions.BtoN || param === TLPermissions.NtoN
  }
  def isToB(param: UInt): Bool = {
    param === TLPermissions.TtoB || param === TLPermissions.BtoB
  }
}
