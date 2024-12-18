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

class WGSinkEResponse(params: InclusiveCacheParameters, widBits: Int) extends InclusiveCacheBundle(params)
{
  val sink = UInt(params.inner.bundle.sinkBits.W)
  val wid = UInt(widBits.W)
}

class WGSinkE(params: InclusiveCacheParameters, widBits: Int) extends Module
{
  val io = IO(new Bundle {
    val resp = Valid(new WGSinkEResponse(params, widBits))
    val e = Flipped(Decoupled(new TLBundleE(params.inner.bundle)))
  })

  if (params.firstLevel) {
    // Tie off unused ports
    io.resp.valid := false.B
    io.resp.bits := DontCare
    io.resp.bits.wid := DontCare
    io.e.ready := true.B
  } else {
    // No restrictions on buffer
    val e = params.micro.innerBuf.e(io.e)

    e.ready := true.B
    io.resp.valid := e.valid
    io.resp.bits.sink := e.bits.sink
    io.resp.bits.wid := DontCare
  }
}
