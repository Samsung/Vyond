# Compiler flags
platform-cppflags-y =
platform-cflags-y = -I../src
platform-asflags-y =
platform-ldflags-y = -L../../monitor/target/riscv64gc-unknown-linux-gnu/debug -lvyond

# Command for platform specific "make run"
platform-runcmd = qemu-system-riscv$(PLATFORM_RISCV_XLEN) -M virt -m 256M \
  -nographic -bios $(build_dir)/platform/generic/firmware/fw_payload.elf

# Blobs to build
FW_TEXT_START=0x80000000
FW_ENC_SIZE = 0x0 #0x8000000
FW_DYNAMIC=y
FW_JUMP=y
ifeq ($(PLATFORM_RISCV_XLEN), 32)
  # This needs to be 4MB aligned for 32-bit system
  FW_JUMP_ADDR=$(shell printf "0x%X" $$(($(FW_TEXT_START) + 0x400000)))
else
  # This needs to be 2MB aligned for 64-bit system
  FW_JUMP_ADDR=$(shell printf "0x%X" $$(($(FW_TEXT_START) + 0x200000 + $(FW_ENC_SIZE))))
endif
FW_JUMP_FDT_ADDR=$(shell printf "0x%X" $$(($(FW_TEXT_START) + 0x2200000 + $(FW_ENC_SIZE))))
FW_PAYLOAD=y
ifeq ($(PLATFORM_RISCV_XLEN), 32)
  # This needs to be 4MB aligned for 32-bit system
  FW_PAYLOAD_OFFSET=0x400000
else
  # This needs to be 2MB aligned for 64-bit system
  FW_PAYLOAD_OFFSET=0x200000 # TODO: ADD FW_ENC_SIZE
endif
FW_PAYLOAD_FDT_ADDR=$(FW_JUMP_FDT_ADDR) # TODO: ADD FW_ENC_SIZE
ifdef PLATFORM
  platform-genflags-y += "-DTARGET_PLATFORM_HEADER=\"platform/$(PLATFORM)/platform.h\""
else
	PLATFORM = "generic"
  platform-genflags-y += "-DTARGET_PLATFORM_HEADER=\"platform/generic/platform.h\""
endif

#platform-objs-y += ../../src/attest.o
#platform-objs-y += ../../src/cpu.o
#platform-objs-y += ../../src/crypto.o
#platform-objs-y += ../../src/enclave.o
#platform-objs-y += ../../src/pmp.o
#platform-objs-y += ../../src/sm.o
#platform-objs-y += ../../src/sm-sbi.o
#platform-objs-y += ../../src/sm-sbi-opensbi.o
#platform-objs-y += ../../src/thread.o
#platform-objs-y += ../../src/mprv.o
#platform-objs-y += ../../src/sbi_trap_hack.o
#platform-objs-y += ../../src/trap.o
#platform-objs-y += ../../src/ipi.o

#platform-objs-y += ../../src/sha3/sha3.o
#platform-objs-y += ../../src/ed25519/fe.o
#platform-objs-y += ../../src/ed25519/ge.o
#platform-objs-y += ../../src/ed25519/keypair.o
#platform-objs-y += ../../src/ed25519/sc.o
#platform-objs-y += ../../src/ed25519/sign.o

#platform-objs-y += ../../src/hkdf_sha3_512/hkdf_sha3_512.o
#platform-objs-y += ../../src/hmac_sha3/hmac_sha3.o

#platform-objs-y += ../../src/platform/$(PLATFORM)/platform.o

#platform-objs-y += ../../src/plugins/plugins.o

platform-objs-y += platform.o
platform-objs-y += platform_override_modules.o
ifeq ($(PLATFORM), wgrocket)
	carray-platform_override_modules-y += wgrocket
	platform-objs-y += wgrocket.o
else
	carray-platform_override_modules-y += sifive_fu540
	platform-objs-y += sifive_fu540.o
endif
platform-objs-y += vyond.o
platform-objs-y += mprv.o

$(info FW_JUMP_ADDR $(FW_JUMP_ADDR))
$(info FW_JUMP_FDT_ADDR $(FW_JUMP_FDT_ADDR))
