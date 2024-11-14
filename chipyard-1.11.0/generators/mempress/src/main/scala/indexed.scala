//see LICENSE for license
//authors: Joonho Whangbo
// Ported from Valid interface
//
package mempress

import chisel3._

class Indexed[+T <: Data](gen: T, w: Int) extends Bundle {
  /** The data to be transferred
   */
  val data = Output(gen)

  /** The index of the data
   */
  val idx = Output(UInt(w.W))
}

object Indexed {
  /** Wrap some [[Data]] in a indexed interface
   *  @tparam T the type of data to wrap
   *  @param gen the data to wrap
   *  @param w the bitwidth of the index
   *  @return the wrapped input data
   */
  def apply[T <: Data](gen: T, w: Int): Indexed[T] = new Indexed(gen, w)
}
