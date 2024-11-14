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
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import chisel3.experimental.dataview.BundleUpcastable
import freechips.rocketchip.util.DescribedSRAM
import WGMetaData._
import TLMessages._

import sifive.blocks.inclusivecache._

class WGDirectoryEntry(params: InclusiveCacheParameters, widBits: Int) extends InclusiveCacheBundle(params)
{
  val dirty   = Bool() // true => TRUNK or TIP
  val state   = UInt(params.stateBits.W)
  val clients = UInt(params.clientBits.W)
  val tag     = UInt(params.tagBits.W)
  val wid     = UInt(widBits.W)
}

class WGDirectoryRead(params: InclusiveCacheParameters, widBits: Int) extends InclusiveCacheBundle(params)
{
  val set = UInt(params.setBits.W)
  val tag = UInt(params.tagBits.W)
  val wid = UInt(widBits.W)
  val prio = Vec(3, Bool()) // A=001, B=010, C=100
  val opcode = UInt(3.W)
}

class WGDirectoryWrite(params: InclusiveCacheParameters, widBits: Int) extends InclusiveCacheBundle(params)
{
  val set  = UInt(params.setBits.W)
  val way  = UInt(params.wayBits.W)
  val data = new WGDirectoryEntry(params, widBits)
  val wid = UInt(widBits.W)
}

class WGDirectoryResult(params: InclusiveCacheParameters, widBits: Int) extends WGDirectoryEntry(params, widBits)
{
  val hit = Bool()
  val onlyTagHit = Bool()
  val way = UInt(params.wayBits.W)
}

class WGDirectory(params: InclusiveCacheParameters, widBits: Int) extends Module
{
  val io = IO(new Bundle {
    val write  = Flipped(Decoupled(new WGDirectoryWrite(params, widBits)))
    val read   = Flipped(Valid(new WGDirectoryRead(params, widBits))) // sees same-cycle write
    val result = Valid(new WGDirectoryResult(params, widBits))
    val ready  = Bool() // reset complete; can enable access
  })

  val codeBits = new WGDirectoryEntry(params, widBits).getWidth

  val cc_dir =  DescribedSRAM(
    name = "cc_dir",
    desc = "Directory RAM",
    size = params.cache.sets,
    data = Vec(params.cache.ways, UInt(codeBits.W))
  )

  val write = Queue(io.write, 1) // must inspect contents => max size 1
  // a flow Q creates a WaR hazard... this MIGHT not cause a problem
  // a pipe Q causes combinational loop through the scheduler

  // Wiping the Directory with 0s on reset has ultimate priority
  val wipeCount = RegInit(0.U((params.setBits + 1).W))
  val wipeOff = RegNext(false.B, true.B) // don't wipe tags during reset
  val wipeDone = wipeCount(params.setBits)
  val wipeSet = wipeCount(params.setBits - 1,0)

  io.ready := wipeDone
  when (!wipeDone && !wipeOff) { wipeCount := wipeCount + 1.U }
  assert (wipeDone || !io.read.valid)

  // Be explicit for dumb 1-port inference
  val ren = io.read.valid
  val wen = (!wipeDone && !wipeOff) || write.valid
  assert (!io.read.valid || wipeDone)

  require (codeBits <= 256)

  write.ready := !io.read.valid
  when (!ren && wen) {
    cc_dir.write(
      Mux(wipeDone, write.bits.set, wipeSet),
      VecInit.fill(params.cache.ways) { Mux(wipeDone, write.bits.data.asUInt, 0.U) },
      UIntToOH(write.bits.way, params.cache.ways).asBools.map(_ || !wipeDone))
  }

  val ren1 = RegInit(false.B)
  val ren2 = if (params.micro.dirReg) RegInit(false.B) else ren1
  ren2 := ren1
  ren1 := ren

  val bypass_valid = params.dirReg(write.valid)
  val bypass = params.dirReg(write.bits, ren1 && write.valid)
  val regout = params.dirReg(cc_dir.read(io.read.bits.set, ren), ren1)
  val tag = params.dirReg(RegEnable(io.read.bits.tag, ren), ren1)
  val set = params.dirReg(RegEnable(io.read.bits.set, ren), ren1)
  val wid = params.dirReg(RegEnable(io.read.bits.wid, ren), ren1)
  val prio = params.dirReg(RegEnable(io.read.bits.prio, ren), ren1)
  val opcode = params.dirReg(RegEnable(io.read.bits.opcode, ren), ren1)

  // Compute the victim way in case of an evicition
  val victimLFSR = random.LFSR(width = 16, params.dirReg(ren))(InclusiveCacheParameters.lfsrBits-1, 0)
  val victimSums = Seq.tabulate(params.cache.ways) { i => ((1 << InclusiveCacheParameters.lfsrBits)*i / params.cache.ways).U }
  val victimLTE  = Cat(victimSums.map { _ <= victimLFSR }.reverse)
  val victimSimp = Cat(0.U(1.W), victimLTE(params.cache.ways-1, 1), 1.U(1.W))
  val victimWayOH = victimSimp(params.cache.ways-1,0) & ~(victimSimp >> 1)
  val victimWay = OHToUInt(victimWayOH)
  dontTouch(victimWay)
  assert (!ren2 || victimLTE(0) === 1.U)
  assert (!ren2 || ((victimSimp >> 1) & ~victimSimp) === 0.U) // monotone
  assert (!ren2 || PopCount(victimWayOH) === 1.U)

  val setQuash = bypass_valid && bypass.set === set
  val tagMatch = bypass.data.tag === tag
  val wayMatch = bypass.way === victimWay

  val bypassHit = setQuash && tagMatch && bypass.wid === wid
  val bypassOnlyTagHit = setQuash && tagMatch

  val ways = regout.map(d => d.asTypeOf(new WGDirectoryEntry(params, widBits)))
  // replacement of mismatched wid
  //val hits = Cat(ways.zipWithIndex.map { case (w, i) =>
  //  w.tag === tag && w.state =/= INVALID && (!setQuash || i.U =/= bypass.way)
  //}.reverse)
  val wid_valid = prio(0) | prio(2)
  val hits = Cat(ways.zipWithIndex.map { case (w, i) =>
    w.tag === tag && Mux(wid_valid, w.wid === wid, true.B) && w.state =/= INVALID && (!setQuash || i.U =/= bypass.way)
  }.reverse)
  dontTouch(hits) 
  
  val onlyTagHits = Cat(ways.zipWithIndex.map { case (w, i) =>
    w.tag === tag && w.state =/= INVALID && (!setQuash || i.U =/= bypass.way)
  }.reverse)
  dontTouch(onlyTagHits) 
  val onlyTagHit = onlyTagHits.orR
  dontTouch(onlyTagHit) 
  val hit = hits.orR 
  dontTouch(hit) 
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  // hit | onlyTagHit | bypassHit  | bypassOnlyTagHit | wayMatch | result.bits               | result.bits.hit | result.bits.onlyTagHit | result.bits.wid          | result.bits.way
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  //  X  |     X      |     0      |        1         |    0     | ways(victimWay)           |      0          | 0                      |       wid                | victimWay 
  //  X  |     X      |     1      |        1         |    1     | bypass.data               |      0          | 0                      |       wid                | victimWay
  //  X  |     X      |     0      |        1         |    X     | bypass.data               |      bypassHit  | bypassOnlyTagHit       |       bypass.data.wid    | bypass.way
  //  X  |     X      |     1      |        1         |    X     | bypass.data               |      bypassHit  | bypassOnlyTagHit       |       wid                | bypass.way
  //  0  |     1      |     X      |        0         |    X     | Mux1H (onlyTagHits, ways) |      hit        | onlyTagHit             | Mux1H(onlyTagHits, ways) | OHToUInt(onlyTagHits)
  //  1  |     1      |     X      |        0         |    X     | Mux1H (hits, ways)        |      hit        | onlyTagHit             |       wid                | OHToUInt(hits)
  //-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  io.result.valid := ren2
  // replacement of mismatched wid
  //io.result.bits.viewAsSupertype(chiselTypeOf(bypass.data)) := Mux(hit, Mux1H(hits, ways), Mux(setQuash && (tagMatch || wayMatch), bypass.data, Mux1H(victimWayOH, ways)))
  //io.result.bits.viewAsSupertype(chiselTypeOf(bypass.data)) := Mux(hit, Mux1H(hits, ways), Mux(onlyTagHit, Mux1H(onlyTagHits, ways), Mux(setQuash && (tagMatch || wayMatch), bypass.data, Mux1H(victimWayOH, ways))))
  io.result.bits.viewAsSupertype(chiselTypeOf(bypass.data)) := Mux(hit, Mux1H(hits, ways), Mux(onlyTagHit, Mux1H(onlyTagHits, ways), Mux( bypassHit || bypassOnlyTagHit || wayMatch, bypass.data, Mux1H(victimWayOH, ways))))


  //io.result.bits.hit := hit || (setQuash && tagMatch && bypass.data.state =/= INVALID)
  io.result.bits.hit := hit || (bypassHit && bypass.data.state =/= INVALID)
  io.result.bits.onlyTagHit := onlyTagHit || (bypassOnlyTagHit && bypass.data.state =/= INVALID)

  //io.result.bits.way := Mux(hit, OHToUInt(hits), Mux(setQuash && tagMatch, bypass.way, victimWay))
  //val way = Mux(hit, OHToUInt(hits), Mux(onlyTagHit, OHToUInt(onlyTagHits), Mux(setQuash && tagMatch, bypass.way, victimWay)))
  io.result.bits.way := Mux(hit, OHToUInt(hits), Mux(onlyTagHit, OHToUInt(onlyTagHits), Mux(setQuash && tagMatch, bypass.way, victimWay)))
  
  //io.result.bits.wid := Mux(hit, wid, Mux(onlyTagHit, Mux1H(onlyTagHits, ways).wid, wid))
  io.result.bits.wid := Mux(onlyTagHit, Mux1H(onlyTagHits, ways).wid, Mux(bypassOnlyTagHit, bypass.data.wid, wid))

  // When the request comes from SinkC (prio 2) and tag matched, wid must be matched
  when (io.result.valid && prio(2) && (opcode === Release|| opcode === ReleaseData) && onlyTagHit) {
    assert (hit)
  }

  params.ccover(ren2 && setQuash && tagMatch, "DIRECTORY_HIT_BYPASS", "Bypassing write to a directory hit")
  params.ccover(ren2 && setQuash && !tagMatch && wayMatch, "DIRECTORY_EVICT_BYPASS", "Bypassing a write to a directory eviction")

  def json: String = s"""{"clients":${params.clientBits},"mem":"${cc_dir.pathName}","clean":"${wipeDone.pathName}"}"""
}
