# Makefile.  Generated from Makefile.in by configure.

SHELL := /bin/sh
.DEFAULT_GOAL := libgloss

prefix := /tools/Xilinx/Vitis/2023.2/gnu/riscv/lin/riscv64-unknown-elf/bin/../riscv32-xilinx-elf
exec_prefix := ${prefix}
libdir := ${exec_prefix}/lib

srcdir := /home/sk84kim/workspace/vyond-public/chipyard-1.11.0/toolchains/libgloss
builddir := .

CC := riscv64-unknown-elf-gcc
AR := riscv64-unknown-elf-ar
SIZE := riscv64-unknown-elf-size

CFLAGS ?= -g -O2 -std=c11 -pedantic -Wall
CPPFLAGS ?= 
LDFLAGS ?= 

multilib := 

libgloss_c_srcs := \
	misc/traps.c \
	misc/tls.c \
	misc/main.c \
	misc/htif.c \
	sys/nanosleep.c \
	sys/access.c \
	sys/chdir.c \
	sys/chmod.c \
	sys/chown.c \
	sys/close.c \
	sys/execve.c \
	sys/exit.c \
	sys/faccessat.c \
	sys/fork.c \
	sys/fstat.c \
	sys/fstatat.c \
	sys/ftime.c \
	sys/getcwd.c \
	sys/getpid.c \
	sys/gettimeofday.c \
	sys/isatty.c \
	sys/kill.c \
	sys/link.c \
	sys/lseek.c \
	sys/lstat.c \
	sys/open.c \
	sys/openat.c \
	sys/read.c \
	sys/sbrk.c \
	sys/stat.c \
	sys/sysconf.c \
	sys/times.c \
	sys/unlink.c \
	sys/utime.c \
	sys/wait.c \
	sys/write.c \
	libc/stdio/vxprintf.c \
	libc/stdio/printf.c \
	libc/stdio/sprintf.c \
	libc/stdio/snprintf.c \
	libc/stdio/puts.c \
	libc/stdio/printbuf.c

libgloss_asm_srcs := \
	misc/crt0.S \
	misc/crtmain.S \
	misc/crtmain-argv.S \
	misc/init.S

libgloss_hdrs := \
	$(wildcard $(srcdir)/include/*.h) \
	$(wildcard $(srcdir)/misc/*.h) \
	$(wildcard $(srcdir)/libc/stdio/*.h)

specs_newlib := $(srcdir)/util/htif.specs
specs_nano := $(srcdir)/util/htif_nano.specs
specs_wrap := $(srcdir)/util/htif_wrap.specs
specs_argv := $(srcdir)/util/htif_argv.specs

libgloss_lds := $(srcdir)/util/htif.ld

# Additional files to install
libgloss_data := \
	$(specs_newlib) \
	$(specs_nano) \
	$(specs_wrap) \
	$(specs_argv) \
	$(libgloss_lds)

libgloss_CFLAGS := $(CFLAGS) -fno-common -fno-builtin -mcmodel=medany
libgloss_CPPFLAGS := $(CPPFLAGS) -I $(srcdir)/include

libgloss_libs :=
libgloss_objs :=
libgloss_install :=

# $(1): subdirectory
# $(2): variable uniquifier
# $(3): additional compiler flags
define libgloss

libgloss$(2)_c_objs := $$(addprefix $$(builddir)/$(1)/,$$(libgloss_c_srcs:.c=.o))
libgloss$(2)_asm_objs := $$(addprefix $$(builddir)/$(1)/,$$(libgloss_asm_srcs:.S=.o))
libgloss$(2)_lib := $$(builddir)/$(1)/libgloss_htif.a

$$(libgloss$(2)_c_objs): $$(builddir)/$(1)/%.o: $$(srcdir)/%.c $$(libgloss_hdrs)
	@mkdir -p $$(dir $$@)
	$$(CC) $$(libgloss_CFLAGS) $(3) $$(libgloss_CPPFLAGS) -c -o $$@ $$<

$$(libgloss$(2)_asm_objs): $$(builddir)/$(1)/%.o: $$(srcdir)/%.S $$(libgloss_hdrs)
	@mkdir -p $$(dir $$@)
	$$(CC) $$(libgloss_CFLAGS) $(3) $$(libgloss_CPPFLAGS) -D__ASSEMBLY__ -c -o $$@ $$<

$$(libgloss$(2)_lib): $$(libgloss$(2)_c_objs) $$(libgloss$(2)_asm_objs)
	$$(AR) rcs $$@ $$^

.PHONY: libgloss($2)_install
libgloss$(2)_install: $$(libgloss$(2)_lib) $$(libgloss_data)
	install -m 644 -p -D -t $$(libdir)/$(1)/ $$^

libgloss_objs += $$(libgloss$(2)_c_objs) $$(libgloss$(2)_asm_objs)
libgloss_libs += $$(libgloss$(2)_lib)
libgloss_install += libgloss$(2)_install

endef

define libgloss_multilib
$(eval arch := $(patsubst %/,%,$(dir $(1))))
$(eval abi := $(notdir $(1)))
$(call libgloss,$(1),_$(arch)_$(abi),-march=$(arch) -mabi=$(abi))
endef

$(eval $(call libgloss))
$(foreach subdir,$(multilib),$(eval $(call libgloss_multilib,$(subdir))))

.PHONY: libgloss
libgloss: $(libgloss_libs)

# Verify that libdir is part of the compiler library search paths
searchdirs = $(shell $(CC) -print-search-dirs | sed -n '/^libraries: =/{s///;s/:/ /g;p;}')

.PHONY: install
install: $(libgloss_install)
	$(if $(filter $(realpath $(libdir)),\
		$(foreach d,$(call searchdirs),$(realpath $(d)))),,\
		$(error libdir is not in $(CC) library search path: $(libdir)))


#
# Test programs
#

check_bins := # test binaries

# $(1): output file
# $(2): source files
# $(3): spec files
define check
check_bins += $(1)

$(1): LDFLAGS += -L $$(builddir)
$(1): $(2) $$(libgloss_lib) $$(libgloss_lds) $(3)
	$$(CC) $$(CFLAGS) $(foreach f,$(3),-specs=$(f)) $$(LDFLAGS) \
		-T $$(libgloss_lds) -Wl,-Map=$$@.map -o $$@ $(2)
	$$(SIZE) $$@
endef

hello_src := $(srcdir)/tests/hello.c

# Manual equivalent:
# 	$(CC) $(CFLAGS) -mcmodel=medany -nostdlib -nostartfiles -T htif.ld \
# 		hello.c -Wl,--start-group -lc -lgloss_htif -Wl,--end-group -lgcc
#
$(eval $(call check,hello.riscv,$(hello_src),$(specs_newlib)))

# Demonstrate use of newlib-nano
$(eval $(call check,hello.nano.riscv,$(hello_src),$(specs_nano)))

# Demonstrate using GNU ld's --wrap feature to replace newlib functions
# with more compact alternatives implemented by libgloss_htif
$(eval $(call check,hello.pico.riscv,$(hello_src),$(specs_nano) $(specs_wrap)))

# Demonstrate support for passing command-line arguments from fesvr
$(eval $(call check,hello.argv.riscv,$(hello_src),$(specs_nano) $(specs_argv)))


.PHONY: check
check: $(check_bins)

.PHONY: clean
clean:
	rm -f -- $(libgloss_libs) $(libgloss_objs) $(check_bins) $(addsuffix .map,$(check_bins))

# Disable built-in suffix rules
.SUFFIXES:
