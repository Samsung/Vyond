// SPDX-License-Identifier: Apache-2.0

package dspblocks

trait MemMasterModel {
  def memReadWord(addr:  BigInt): BigInt
  def memWriteWord(addr: BigInt, value: BigInt): Unit

  def memReadWord(addr:  Long): BigInt = memReadWord(BigInt(addr))
  def memWriteWord(addr: Long, value: BigInt): Unit = memWriteWord(BigInt(addr), value)
}
