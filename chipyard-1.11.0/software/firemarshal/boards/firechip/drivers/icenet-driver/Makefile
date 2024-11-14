ifneq ($(KERNELRELEASE),)

obj-m += icenet.o

else

# The default assumes you cloned this as part of firesim-software (FireMarshal)
LINUXSRC=../../../../riscv-linux

KMAKE=make -C $(LINUXSRC) ARCH=riscv CROSS_COMPILE=riscv64-unknown-linux-gnu- M=$(PWD)

icenet.ko: icenet.c
	$(KMAKE)

clean:
	$(KMAKE) clean

endif
