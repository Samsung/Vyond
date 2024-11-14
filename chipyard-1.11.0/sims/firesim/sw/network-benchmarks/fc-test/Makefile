CC=riscv64-unknown-elf-gcc
CFLAGS=-mcmodel=medany -Wall -O2 -fno-common -fno-builtin-printf -I../common/
CONFIG=-DNCOMPUTE=$(NCOMPUTE) -DNBYTES=$(NBYTES) -DPAUSE_MS=$(PAUSE_MS) -DNROUNDS=$(NROUNDS)
LDFLAGS=-T ../common/link.ld -static -nostdlib -nostartfiles -lgcc

all: fc-client.riscv fc-server.riscv

%.riscv: %.o crt.o syscalls.o
	$(CC) $^ $(LDFLAGS) -o $@

%.o: %.c
	$(CC) $(CFLAGS) $(CONFIG) -c $< -o $@

crt.o: ../common/crt.S
	$(CC) $(CFLAGS) -c $< -o $@

syscalls.o: ../common/syscalls.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f *.o *.riscv
