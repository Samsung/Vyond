
RISCV_PFX=riscv64-unknown-elf-
RISCV_CC=$(RISCV_PFX)gcc
RISCV_OBJDUMP=$(RISCV_PFX)objdump

BINARY_OPT=-static -specs=htif_nano.specs
OBJECT_DUMP_OPT=--disassemble-all

TARGET=mempress-rocc
TARGET_RISCV=$(TARGET).riscv
TARGET_OBJDUMP=$(TARGET).riscv.dump
JUNK += $(TARGET_RISCV) $(TARGET_OBJDUMP)

all: $(TARGET_RISCV) $(TARGET_OBJDUMP)

$(TARGET_RISCV): mempress-rocc.c
	$(RISCV_CC) $(BINARY_OPT) -o $@ $^

$(TARGET_OBJDUMP): $(TARGET_RISCV)
	$(RISCV_OBJDUMP) $(OBJECT_DUMP_OPT) $< > $@

.PHONY: clean
clean:
	rm -f $(JUNK)
