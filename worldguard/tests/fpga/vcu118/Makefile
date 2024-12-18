CC=$(RISCV)/bin/riscv64-unknown-elf-gcc
OBJCOPY=$(RISCV)/bin/riscv64-unknown-elf-objcopy
OBJDUMP=$(RISCV)/bin/riscv64-unknown-elf-objdump

CFLAGS = -march=rv64imfd -mabi=lp64d -fno-builtin -ffreestanding

CCFLAGS = $(CFLAGS) -DBARE_METAL
CCFLAGS += -mcmodel=medany -O0 -Wall
CCFLAGS += -fno-pic -fno-common -g -I../../include -I./

LFLAGS = -static -nostartfiles -T main.lds

SDDEVICE_PART1          = $(SD_DEVICE)1
SDDEVICE_PART2          = $(SD_DEVICE)2
PAYLOAD                 = baremetal.bin

SRC = ./head.S \
			./main.c \
			./kprintf.c \
			./uart.c \
			./plic.c \
			./syscalls.c \
			./interrupt.c \
			../../lib/init.c \
			../../lib/wgchecker.c \
			../../lib/wgcore.c \
			../../lib/wgmarker.c \
			../../lib/tests/read_unauthorized_cacheline1.c \
			../../lib/tests/read_unauthorized_cacheline2.c \
			../../lib/tests/multiple_permissions_on_cacheline.c \
			../../lib/tests/overwrite_stackframe.c \
			../../lib/tests/raw_cacheline.c

build: baremetal.bin

.PHONY: flash
flash: $(SD_DEVICE)
	$(info PAYLOAD INFORMATION)
	$(info $(SD_DEVICE))
	$(info $(SDDEVICE_PART1))
	$(info $(SDDEVICE_PART2))
	#sudo sgdisk --clear -g --set-alignment=34 --new=1:34:1048576 --new=2:1048594:0 --typecode=1:af0a --typecode=2:af00 $(SD_DEVICE)
	sudo dd if=$(PAYLOAD) of=$(SDDEVICE_PART1) status=progress oflag=sync bs=1M



baremetal.bin: baremetal.elf
	$(OBJCOPY) -S -O binary --change-addresses -0x80000000 $^ $@
	$(OBJDUMP) -DS  $^ > baremetal.asm

baremetal.elf: $(SRC) 
	$(CC) $(CCFLAGS) $(LFLAGS) -o $@ $(SRC)
	$(OBJDUMP) -h -p $@

clean:
	rm -f baremetal.elf baremetal.bin baremetal.asm
