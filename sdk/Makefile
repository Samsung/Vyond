#################################
# RISCV Toolchain
#################################
TARGET = riscv64-unknown-linux-gnu

GCC = $(TARGET)-gcc
CXX = $(TARGET)-g++
CP = $(TARGET)-objcopy
OBJDUMP = $(TARGET)-objdump
DG = $(TARGET)-gdb
SIZE = $(TARGET)-size


#################################
# Flags
#################################

CFLAGS  = -std=gnu99 -O2 -Wall -I../include
LDFLAGS =


#################################
# Source files
#################################
SRC_DIR = ./src
#SRC = $(wildcard $(LIB_SRC_DIR)/*.c)
#OBJ = $(patsubst $(LIB_SRC_DIR)/%.c, %.o, $(SRC))

PROGRAMS = helloworld
.DEFAULT_GOAL := default


#################################
# Build
#################################
%.o: $(SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

%.o: %.S
	$(GCC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

%.riscv: %.o
	$(GCC) $(LDFLAGS) $^ -o $(notdir $@)

%.dump: %.riscv
	$(OBJDUMP) -D $< > $@

#################################
# Recipes
#################################

.PHONY: clean
clean:
	rm -f *.riscv *.o *.dump

.PHONY: default
default: $(addsuffix .riscv, $(PROGRAMS))

.PHONY: dumps
dumps: $(addsuffix .dump, $(PROGRAMS))
