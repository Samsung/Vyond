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

import sifive.blocks.inclusivecache._

class WGQueuedRequest(params: InclusiveCacheParameters, widBits: Int) extends InclusiveCacheBundle(params)
{
  val prio   = Vec(3, Bool()) // A=001, B=010, C=100
  val control= Bool() // control command
  val opcode = UInt(3.W)
  val param  = UInt(3.W)
  val size   = UInt(params.inner.bundle.sizeBits.W)
  val source = UInt(params.inner.bundle.sourceBits.W)
  val tag    = UInt(params.tagBits.W)
  val offset = UInt(params.offsetBits.W)
  val put    = UInt(params.putBits.W)
  val wid    = UInt(widBits.W)
}

class WGFullRequest(params: InclusiveCacheParameters, widBits: Int) extends WGQueuedRequest(params, widBits)
{
  val set = UInt(params.setBits.W)
}

class WGAllocateRequest(params: InclusiveCacheParameters, widBits: Int) extends WGFullRequest(params, widBits)
{
  val repeat = Bool() // set is the same
}
