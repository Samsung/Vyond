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
import freechips.rocketchip.tilelink._

import sifive.blocks.inclusivecache._

class WGSourceERequest(params: InclusiveCacheParameters, widBits: Int) extends InclusiveCacheBundle(params)
{
  val sink = UInt(params.outer.bundle.sinkBits.W)
  val wid    = UInt(widBits.W)
}

class WGSourceE(params: InclusiveCacheParameters, widBits: Int) extends Module
{
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new WGSourceERequest(params, widBits)))
    val e = Decoupled(new TLBundleE(params.outer.bundle))
  })

  // ready must be a register, because we derive valid from ready
  require (!params.micro.outerBuf.e.pipe && params.micro.outerBuf.e.isDefined)

  val e = Wire(chiselTypeOf(io.e))
  io.e <> params.micro.outerBuf.e(e)

  io.req.ready := e.ready
  e.valid := io.req.valid

  e.bits.sink := io.req.bits.sink

  // we can't cover valid+!ready, because no backpressure on E is common
}
