// See LICENSE for license details

package aes

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.util.random.{LFSR}

import freechips.rocketchip.util.{DecoupledHelper}
import roccaccutils._

// Non-exhaustive list of resources used to integrate:
//   https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38a.pdf
//   https://opentitan.org/book/doc/introduction.html
//   https://github.com/lowRISC/opentitan/blob/fe702b60582f7c4e5549352a09e7992544d41bec/
//     hw/ip/aes/rtl/aes_core.sv
//     hw/ip/aes/rtl/aes_control_fsm.sv
//     hw/ip/aes/rtl/aes_reg_top.sv
//     sw/device/lib/crypto/drivers/aes.c
//     sw/device/lib/crypto/drivers/aes_test.c

trait AESConsts {
  val BLOCK_SZ_BYTES = 16
  val BLOCK_SZ_BITS = BLOCK_SZ_BYTES * 8
}

object AES256Consts extends AESConsts {
  val KEY_SZ_BYTES = 32
  val KEY_SZ_BITS = KEY_SZ_BYTES * 8
}
import AES256Consts._

// AES256 Encrypt/Decrypt Block (ECB-mode, no security masking)
class AesCipherCoreWrapper_AES256_ECB_NoMask
  //extends BlackBox with HasBlackBoxResource {
  // TODO: BlackBoxPath causes issues when there are duplicates in firtool
  extends BlackBox with HasBlackBoxPath {
  val io = IO(new Bundle {
    val clk_i = Input(Clock())
    val rst_ni = Input(Reset())

    val in_valid_i = Input(Bool())
    val in_ready_o = Output(Bool())

    val out_valid_o = Output(Bool())
    val out_ready_i = Input(Bool())

    val op_i = Input(UInt(2.W))
    val key_len_i = Input(UInt(3.W))
    val crypt_i = Input(Bool())
    val dec_key_gen_i = Input(Bool())
    val prng_reseed_i = Input(Bool())

    val prd_clearing_i_0 = Input(UInt(64.W))

    val data_in_mask_o = Output(UInt(BLOCK_SZ_BITS.W))
    val entropy_req_o = Output(Bool())
    val entropy_ack_i = Input(Bool())
    val entropy_i = Input(UInt(32.W))

    val state_init_i_0 = Input(UInt(BLOCK_SZ_BITS.W))
    val key_init_i_0 = Input(UInt(KEY_SZ_BITS.W))
    val state_o_0 = Output(UInt(BLOCK_SZ_BITS.W))

    val alert_o = Output(Bool())
  })

  def in_fire() = io.in_valid_i && io.in_ready_o
  def out_fire() = io.out_valid_o && io.out_ready_i

  val chipyardDir = System.getProperty("user.dir")
  val aesDir = s"$chipyardDir/generators/caliptra-aes-acc/src/main/resources/"
  val vsrcDirPostfix = "vsrc/aes/aes_cipher_core"

  val proc = s"make -C ${aesDir + vsrcDirPostfix} core"
  require(proc.! == 0, "Failed to run pre-processing step")

  addPath(s"${aesDir + vsrcDirPostfix}/core.sv")
  //addResource(s"$vsrcDirPostfix/core.sv")
}

class InCryptBundle extends Bundle {
  val encrypt = Input(Bool()) // if not then decrypt
  val data = Input(UInt(BLOCK_SZ_BITS.W))
  val key = Input(UInt(KEY_SZ_BITS.W))
}

class OutCryptBundle extends Bundle {
  val data = Output(UInt(BLOCK_SZ_BITS.W))
}

// ECB-mode AES-256 block driver
//   - Expects the key to stay the same throughout the entire time of {en,de}crypting
class AesCipherCoreDriver extends Module {
  val io = IO(new Bundle {
    val in = Flipped(DecoupledIO(new InCryptBundle))
    val out = DecoupledIO(new OutCryptBundle)
  })

  object AesCipherCoreConsts {
    val AES_256 = "b100".U

    val CIPH_FWD = "b01".U
    val CIPH_INV = "b10".U
  }
  import AesCipherCoreConsts._

  val acc = Module(new AesCipherCoreWrapper_AES256_ECB_NoMask)
  acc.io.clk_i := clock
  acc.io.rst_ni := !reset.asBool

  acc.io.key_len_i := AES_256
  acc.io.entropy_ack_i := true.B // entropy is always available
  val entropy_i = RegInit(0.U(32.W))
  when (acc.io.entropy_req_o) {
    entropy_i := LFSR(32, acc.io.entropy_req_o)
  }
  acc.io.entropy_i := entropy_i
  val prd_clearing_i_0 = RegInit(0.U(64.W))
  when (acc.io.out_valid_o) {
    prd_clearing_i_0 := LFSR(32, acc.io.out_valid_o)
  }
  acc.io.prd_clearing_i_0 := prd_clearing_i_0

  val s_initial_idle :: s_idle :: s_encrypt :: s_dec_key_gen :: s_decrypt :: Nil = Enum(5)
  val state = RegInit(s_initial_idle)
  val prev_state = RegInit(s_initial_idle)

  switch (state) {
    // wait for core to be ready
    is (s_initial_idle) {
      when (acc.io.in_ready_o) {
        state := s_idle
        prev_state := s_initial_idle
      }
    }

    // start here when switching from encrypt to decrypt
    is (s_idle) {
      when (io.in.valid) {
        // since we go back to idle after sending and encrypt/decrypt req, make sure to return to that state
        // if you started in that state (i.e. only go to dec_key_gen when there is a switch from encrypt -> decrypt)
        state := Mux(io.in.bits.encrypt, s_encrypt, Mux(prev_state === s_decrypt, s_decrypt, s_dec_key_gen))
        prev_state := s_idle
      }
    }

    is (s_encrypt) {
      when (io.in.fire) {
        state := s_idle
        prev_state := s_encrypt
      }
    }

    // create initial decryption key
    is (s_dec_key_gen) {
      when (acc.out_fire()) {
        state := s_decrypt
        prev_state := s_dec_key_gen
      }
    }

    is (s_decrypt) {
      when (io.in.fire) {
        state := s_idle
        prev_state := s_decrypt
      }
    }
  }

  val op = RegInit(CIPH_FWD)
  acc.io.key_init_i_0 := io.in.bits.key

  val encrypt_send_helper = DecoupledHelper(
    state === s_encrypt,
    io.in.valid,
    io.in.ready
  )

  val decrypt_send_helper = DecoupledHelper(
    state === s_decrypt,
    io.in.valid,
    io.in.ready
  )

  val dec_key_send_helper = DecoupledHelper(
    state === s_dec_key_gen,
    io.in.valid,
    io.in.ready
  )

  when (io.in.fire) {
    printf(":AES:CMDIN: Encrypt(0x%x) Data(0x%x) Key(0x%x)\n",
      io.in.bits.encrypt,
      io.in.bits.data,
      io.in.bits.key)
  }

  when (io.out.fire) {
    printf(":AES:RESPOUT: Data(0x%x)\n", io.out.bits.data)
  }

  val is_initial_state = (state === s_initial_idle)
  val is_non_state = (state === s_dec_key_gen)
  val is_all_non_state = is_initial_state || is_non_state

  acc.io.in_valid_i := encrypt_send_helper.fire(io.in.ready) ||
    dec_key_send_helper.fire(io.in.ready) ||
    decrypt_send_helper.fire(io.in.ready)
  io.in.ready := acc.io.in_ready_o && !is_all_non_state && (state =/= s_idle)

  io.out.valid := acc.io.out_valid_o && !is_all_non_state
  acc.io.out_ready_i := io.out.ready || is_all_non_state
  io.out.bits.data := acc.io.state_o_0

  acc.io.dec_key_gen_i := (state === s_dec_key_gen)
  acc.io.crypt_i := true.B
  acc.io.prng_reseed_i := true.B

  acc.io.state_init_i_0 := io.in.bits.data

  acc.io.op_i := Mux(
    (state === s_decrypt) || ((state === s_idle) && (prev_state === s_decrypt)),
    CIPH_INV,
    CIPH_FWD)
}
