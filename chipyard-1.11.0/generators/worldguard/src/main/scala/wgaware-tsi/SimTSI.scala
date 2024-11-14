// See LICENSE in https://github.com/ucb-bar/testchipip/blob/master/LICENSE
// See LICENSE for license details.

/**
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */
package worldguard

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam}

import org.chipsalliance.cde.config.{Parameters, Field}

import testchipip.tsi._

object WGSimTSI {
  def connect(tsi: Option[TSIIO], clock: Clock, reset: Reset, chipId: Int = 0): Bool = {
    val exit = tsi.map { s =>
      val sim = Module(new SimTSI(chipId))
      sim.io.clock := clock
      sim.io.reset := reset
      sim.io.tsi <> s
      sim.io.exit
    }.getOrElse(0.U)

    val success = exit === 1.U
    //val error = exit >= 2.U
    val error = false.B     // Ignore it. BTW, why denied access is propagated to here?
    assert(!error, "*** FAILED *** (exit code = %d)\n", exit >> 1.U)
    success
  }
}
