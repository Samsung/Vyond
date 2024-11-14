// See LICENSE.SiFive for license details.
// See LICENSE for license details.

/**
 * WorldGuard Checker (WGC) implementation is based on the PMP in Rocketchip.
 * WGC adds extra registers "perm" for each entry 
 * and modifies a logic to check the permission.
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

package worldguard

import chisel3._
import chisel3.util.{Cat, log2Ceil}

import org.chipsalliance.cde.config._

import freechips.rocketchip.tile._
import freechips.rocketchip.util._

/**
 * Need for considerations:
 * - alignment and granularity (see PMP)
 */
class WGCRequestIO(beatBytes: Int) extends Bundle {
  val address = UInt(64.W) // TODO: parameterize it
  val size = UInt(64.W)    // TODO: check the exact width
  val wid = UInt(2.W)     // TODO: can it be parametersized?
  val r = Bool()
  val w = Bool()
}

class WGCResultIO(beatBytes: Int) extends Bundle {
  val allowed = Bool()
  val be = Bool()      // 1 indicates to send bus error signal by setting denied flag in BundleD
}

class WGCConfig extends Bundle {
  val l         = Bool()
  val res30_12  = UInt(19.W)
  val iw        = Bool()
  val ir        = Bool()
  val ew        = Bool()
  val er        = Bool()
  val res7_2    = UInt(6.W) 
  val a         = UInt(2.W)
}

class WGCPerm extends Bundle {
  val w = Bool()
  val r = Bool()
}

class WGCErrorCauseSlotWidRW extends Bundle {
  val res15_10 = UInt(6.W)
  val at = UInt(2.W)
  val res7_2 = UInt(6.W)
  val wid = UInt(2.W)     // TODO: should be redesign to have more than 4 wids
}

class WGCErrorCauseSlotBeIp extends Bundle {
  val i = Bool()
  val b = Bool()
  val res29_0 = UInt(30.W)
}

object WGCCtrlReg {
  val vendor                  = 0x00
  val impid                   = 0x04
  val nslots                  = 0x08
  val errorCauseSlotWidRW     = 0x10
  val errorCauseSlotBeIp      = 0x14
  val errorAddr               = 0x18
  val slotBase                = 0x20
}

object WGC {

  def apply(reg: WGCReg, paddrBits: Int, wgcGranularity: Int, lgAlign: Int): WGC = {
    val wgc = Wire(new WGC(paddrBits, wgcGranularity, lgAlign))
    wgc.addr := reg.addr
    wgc.perm := reg.perm
    wgc.cfg := reg.cfg
    wgc.mask := wgc.computeMask
    wgc
  }
}

class WGCReg(val paddrBits: Int = 32, val wgcGranularity: Int = 4, val lgAlign: Int = 2) extends Bundle {
  val addr = UInt((paddrBits - lgAlign).W) // TODO: Consider to apply alignment
  val perm = UInt(64.W)
  val cfg = new WGCConfig

  def reset(): Unit = {
    // TODO: what about other fields?
    cfg.a := 0.U
    cfg.l := 0.U
  }

  def readAddr = if (wgcGranularity.log2 == lgAlign) addr else {
    val mask = ((BigInt(1) << (wgcGranularity.log2 - lgAlign)) -1).U
    Mux(napot, addr | (mask >> 1), ~(~addr | mask))
  }

  def napot = cfg.a(1)
  def torNotNAPOT = cfg.a(0)
  def tor = !napot && torNotNAPOT
  def cfgLocked = cfg.l
  def addrLocked(next: WGCReg) = cfgLocked || next.cfgLocked && next.tor
}

class WGC(paddrBits: Int, wgcGranularity: Int, lgAlign: Int) extends WGCReg(paddrBits, wgcGranularity, lgAlign) {
  val mask = UInt(paddrBits.W)

  def computeMask = {
    val base = Cat(addr, cfg.a(0)) | ((wgcGranularity - 1).U >> lgAlign)
    Cat(base & ~(base + 1.U), ((1 << lgAlign) - 1).U)
  }

  private def comparand = ~(~(addr << lgAlign) | (wgcGranularity - 1).U)

  private def pow2Match(x: UInt, lgSize: UInt, lgMaxSize: Int) = {
    //printf(p"[danguria][wgc] pow2Match addr: ${x}, lgSize: ${lgSize} lgMaxSize: ${lgMaxSize}, granularity: ${wgcGranularity}\n")
    def eval(a: UInt, b: UInt, m: UInt) = {
      //printf(p"[danguria][wgc] eval a: ${a} b: ${b} m: ${m}  result: ${((a ^ b) & ~m) === 0.U}\n")
      ((a ^ b) & ~m) === 0.U
    }
    if (lgMaxSize <= wgcGranularity.log2) {
      eval(x, comparand, mask)
    } else {
      // break up the circuit; the MSB part will be CSE'd
      val lsbMask = mask | UIntToOH1(lgSize, lgMaxSize)
      val msbMatch = eval(x >> lgMaxSize, comparand >> lgMaxSize, mask >> lgMaxSize)
      val lsbMatch = eval(x(lgMaxSize-1, 0), comparand(lgMaxSize-1, 0), lsbMask(lgMaxSize-1, 0))
      //printf(p"  hit - mask 0x${Hexadecimal(mask)} comparand: 0x${Hexadecimal(comparand)} lsbMatch: ${lsbMatch} msbMatch: ${msbMatch}\n")
      msbMatch && lsbMatch
    }
  }

  private def boundMatch(x: UInt, lsbMask: UInt, lgMaxSize: Int) = {
    if (lgMaxSize <= wgcGranularity.log2) {
      x < comparand
    } else {
      // break up the circuit; the MSB part will be CSE'd
      val msbsLess = (x >> lgMaxSize) < (comparand >> lgMaxSize)
      val msbsEqual = ((x >> lgMaxSize) ^ (comparand >> lgMaxSize)) === 0.U
      val lsbsLess =  (x(lgMaxSize-1, 0) | lsbMask) < comparand(lgMaxSize-1, 0)
      msbsLess || (msbsEqual && lsbsLess)
    }
  }

  private def lowerBoundMatch(x: UInt, lgSize: UInt, lgMaxSize: Int) =
    !boundMatch(x, UIntToOH1(lgSize, lgMaxSize), lgMaxSize)

  private def upperBoundMatch(x: UInt, lgMaxSize: Int) =
    boundMatch(x, 0.U, lgMaxSize)

  private def rangeMatch(x: UInt, lgSize: UInt, lgMaxSize: Int, prev: WGC) =
    prev.lowerBoundMatch(x, lgSize, lgMaxSize) && upperBoundMatch(x, lgMaxSize)
  
  def aligned(x: UInt, lgSize: UInt, lgMaxSize: Int, prev: WGC): Bool = if (lgMaxSize <= wgcGranularity.log2) true.B else {
    val lsbMask = UIntToOH1(lgSize, lgMaxSize)
    val straddlesLowerBound = ((x >> lgMaxSize) ^ (prev.comparand >> lgMaxSize)) === 0.U && (prev.comparand(lgMaxSize-1, 0) & ~x(lgMaxSize-1, 0)) =/= 0.U
    val straddlesUpperBound = ((x >> lgMaxSize) ^ (comparand >> lgMaxSize)) === 0.U && (comparand(lgMaxSize-1, 0) & (x(lgMaxSize-1, 0) | lsbMask)) =/= 0.U
    val rangeAligned = !(straddlesLowerBound || straddlesUpperBound)
    val pow2Aligned = (lsbMask & ~mask(lgMaxSize-1, 0)) === 0.U
    //printf(p"  aligned - lsbMask: 0x${Hexadecimal(lsbMask)}, mask: 0x${Hexadecimal(mask)},  0x${Hexadecimal(~mask(lgMaxSize-1, 0))}\n")
    Mux(napot, pow2Aligned, rangeAligned)
  }

  // returns whether this WGC matches at least one byte of the access
  def hit(x: UInt, lgSize: UInt, lgMaxSize: Int, prev: WGC): Bool =
    Mux(napot, pow2Match(x, lgSize, lgMaxSize), torNotNAPOT && rangeMatch(x, lgSize, lgMaxSize, prev))
}

class Checker(nSlots: Int, paddrBits: Int, lgMaxSize: Int, wgcGranularity: Int, lgAlign: Int, widWidth: Int) extends Module {
  val io = IO(new Bundle {
    val mwid = Input(UInt(widWidth.W))
    val wid = Input(UInt(widWidth.W))
    val wgc = Input(Vec(nSlots, new WGC(paddrBits, wgcGranularity, lgAlign)))
    val addr = Input(UInt(paddrBits.W))
    val size = Input(UInt(log2Ceil(lgMaxSize + 1).W))
    val r = Output(Bool())
    val w = Output(Bool())
    val cfg = Output(new WGCConfig)
    val perm = Output(UInt(64.W))
  })

  val default = if (io.wgc.isEmpty) true.B else io.wid === io.mwid
  val wgc0 = WireInit(1.U.asTypeOf(new WGC(paddrBits, wgcGranularity, lgAlign)))
  wgc0.perm := (default << (io.wid * 2.U + 1.U)) | (default << (io.wid * 2.U))

  //printf(p"check request - io.wid: ${io.wid}, io.addr: 0x${Hexadecimal(io.addr)}, size: ${io.size}\n")

  val res = (io.wgc zip (wgc0 +: io.wgc)).reverse.foldLeft(wgc0) { case (prev, (wgc, prevWGC)) =>
    val hit = wgc.hit(io.addr, io.size, lgMaxSize, prevWGC)
    val ignore = default && !wgc.cfg.l
    val aligned = wgc.aligned(io.addr, io.size, lgMaxSize, prevWGC)
    dontTouch(hit)

    val wgc_r = wgc.perm(io.wid*2.U)
    val wgc_w = wgc.perm(io.wid*2.U+1.U)

    val cur = WireInit(wgc)
    val r =  aligned && (wgc_r || ignore)
    val w =  aligned && (wgc_w || ignore)
    cur.perm := (w << (io.wid * 2.U + 1.U)) | (r << (io.wid * 2.U))

    //printf(p"  prev: 0x${Hexadecimal(prev.addr)}, 0x${Hexadecimal(prev.perm)}, 0x${Hexadecimal(prev.cfg.a)}\n")
    //printf(p"  prevWGC: 0x${Hexadecimal(prevWGC.addr)}, 0x${Hexadecimal(prevWGC.perm)}, 0x${Hexadecimal(prevWGC.cfg.a)}\n")
    //printf(p"  wgc: 0x${Hexadecimal(wgc.addr)}, 0x${Hexadecimal(wgc.perm)}, 0x${Hexadecimal(wgc.cfg.a)}\n")
    //printf(p"  hit: $hit, ignore: $ignore, aligned: $aligned, wgc_r: $wgc_r wgc_w: $wgc_w\n")

    Mux(hit, cur, prev)
  }

  io.r    := res.perm(io.wid*2.U)
  io.w    := res.perm(io.wid*2.U+1.U)
  io.cfg  := res.cfg
  io.perm := io.wgc(0).perm
}
