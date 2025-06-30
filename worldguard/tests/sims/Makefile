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

CFLAGS  = -std=gnu99 -fno-common -fno-builtin-printf -Wall -I../include
CFLAGS += $(ARCHFLAGS)
CFLAGS += -g #-O2 <- optimization is disabled for debugging
LDFLAGS = -static


#################################
# Source files
#################################
SRC_DIR = ./src
SRC = $(wildcard $(LIB_SRC_DIR)/*.c)
OBJ = $(patsubst $(LIB_SRC_DIR)/%.c, %.o, $(SRC))

LIB_SRC_DIR = ../lib
LIB_SRC = $(wildcard $(LIB_SRC_DIR)/*.c)
LIB_OBJ = $(patsubst $(LIB_SRC_DIR)/%.c, %.o, $(LIB_SRC))

TEST_SRC_DIR = ../lib/tests
TEST_SRC = $(wildcard $(TEST_SRC_DIR)/*.c)
TEST_OBJ = $(patsubst $(TEST_SRC_DIR)/%.c, %.o, $(TEST_SRC))

include libgloss.mk

PROGRAMS = icache                             \
  				 multiple_permissions_on_cacheline  \
  				 overwrite_stackframe               \
  				 raw_cacheline                      \
  				 read_unauthorized_cacheline1       \
  				 read_unauthorized_cacheline2       \
					 markertest                         \
					 markertest2                        \
					 markertest3
.DEFAULT_GOAL := default


#################################
# Build
#################################
%.o: $(SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

%.o: $(LIB_SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

%.o: $(TEST_SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

%.o: %.S
	$(GCC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

%.o: %.c
	$(GCC) $(CFLAGS) -c $< -o $@ 

#%.riscv: %_main.o %.o $(libgloss) $(LIB_OBJ) 
%.riscv: %_main.o %.o $(LIB_OBJ) 
	$(GCC) $(LDFLAGS) $^ -o $(notdir $@)

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
