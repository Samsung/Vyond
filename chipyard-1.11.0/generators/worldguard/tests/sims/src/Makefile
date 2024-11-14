#################################
# RISCV Toolchain
#################################
TARGET = riscv64-unknown-elf

GCC = $(TARGET)-gcc
CXX = $(TARGET)-g++
CP = $(TARGET)-objcopy
OBJDUMP = $(TARGET)-objdump
DG = $(TARGET)-gdb
SIZE = $(TARGET)-size


#################################
# Flags
#################################

# SoC Settings
ARCH = rv64imafdc
ABI = lp64d
ARCHFLAGS = -march=$(ARCH) -mabi=$(ABI)

CFLAGS  = -std=gnu99 -fno-common -fno-builtin-printf -Wall -I./ -I../ 
CFLAGS += $(ARCHFLAGS)
CFLAGS += -g #-O2 <- optimization is disabled for debugging
LDFLAGS = -static

include ../common.mk

$(info obj $(COMMON_OBJ))

include libgloss.mk

PROGRAMS = icache 														\
					 raw_cacheline 											\
					 read_unauthorized_cacheline1 			\
					 read_unauthorized_cacheline2 			\
					 overwrite_stackframe 							\
					 multiple_permissions_on_cacheline

.DEFAULT_GOAL := default


#################################
# Build
#################################

%.o: %.S
	$(GCC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

%.o: %.c
	$(GCC) $(CFLAGS) -c $< -o $@ 

%.riscv: %.o $(libgloss) $(COMMON_OBJ)
	$(GCC) $(LDFLAGS) $< $(COMMON_OBJ) -o $(notdir $@)

%.dump: %.riscv
	$(OBJDUMP) -D $< > $@

#################################
# Recipes
#################################

.PHONY: clean
clean:
	rm -f *.riscv *.o *.dump
	$(if $(libgloss),rm -rf $(libgloss_builddir)/)

.PHONY: default
default: $(addsuffix .riscv, $(PROGRAMS))

.PHONY: dumps
dumps: $(addsuffix .dump, $(PROGRAMS))
