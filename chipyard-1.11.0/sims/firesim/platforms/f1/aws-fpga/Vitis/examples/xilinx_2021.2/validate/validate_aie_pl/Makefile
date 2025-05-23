#
# Copyright 2019-2021 Xilinx, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# makefile-generator v1.0.4
#

# ####################################### Help Section #####################################
.PHONY: help

help::
	$(ECHO) "Makefile Usage:"
	$(ECHO) "  make all TARGET=<aiesim/x86sim/hw_emu/hw> PLATFORM=<FPGA platform> HOST_ARCH=<aarch64>"
	$(ECHO) "      Command to generate the design for specified Target and Shell."
	$(ECHO) ""
	$(ECHO) "  make clean "
	$(ECHO) "      Command to remove the generated non-hardware files."
	$(ECHO) ""
	$(ECHO) "  make cleanall"
	$(ECHO) "      Command to remove all the generated files."
	$(ECHO) ""
	$(ECHO) "  make sd_card TARGET=<aiesim/x86sim/hw_emu/hw> PLATFORM=<FPGA platform> HOST_ARCH=<aarch64>"
	$(ECHO) "      Command to prepare sd_card files."
	$(ECHO) "      This target is only used in embedded device."
	$(ECHO) ""
	$(ECHO) "  make run TARGET=<aiesim/x86sim/hw_emu/hw> PLATFORM=<FPGA platform> HOST_ARCH=<aarch64>"
	$(ECHO) "      Command to run application in emulation or on board."
	$(ECHO) ""
	$(ECHO) "  make build TARGET=<aiesim/x86sim/hw_emu/hw> PLATFORM=<FPGA platform> HOST_ARCH=<aarch64>"
	$(ECHO) "      Command to build xclbin application."
	$(ECHO) ""
	$(ECHO) "  make host HOST_ARCH=<aarch64>"
	$(ECHO) "      Command to build host application."
	$(ECHO) ""
	$(ECHO) "  NOTE: For embedded devices, e.g. zcu102/zcu104/vck190, env variable EDGE_COMMON_SW and PERL need to be set first, and HOST_ARCH is either aarch32 or aarch64. For example,"
	$(ECHO) "       export EDGE_COMMON_SW=< path-to-rootfs-and-Image-files >"
	$(ECHO) ""

# ##################### Setting up default value of TARGET ##########################
TARGET ?= aiesim

# ################### Setting up default value of PLATFORM ##############################
PLATFORM ?= xilinx_vck190_base_202110_1

# ###################### Setting up default value of HOST_ARCH ####################### 
HOST_ARCH ?= aarch64

# ######################## Setting up Project Variables #################################
MK_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
CUR_DIR := $(patsubst %/,%,$(dir $(MK_PATH)))
COMMON_REPO ?= $(shell bash -c 'export MK_PATH=$(MK_PATH); echo $${MK_PATH%validate/validate_aie_pl/*}')
XF_PROJ_ROOT = $(shell readlink -f $(COMMON_REPO))


# ################### Setting environment variables for versal flow ##################
ifneq ($(wildcard $(XILINX_VITIS)/aietools/.*),)
XILINX_VITIS_AIETOOLS := $(XILINX_VITIS)/aietools
LDXRT := -ladf_api_xrt
else
XILINX_VITIS_AIETOOLS := $(XILINX_VITIS)/cardano
LDXRT := -lcardano_api_xrt -lxaiengine -lxrt_core
endif

# ######################### Include environment variables in utils.mk ####################
include ./utils.mk
XPLATFORM := $(call device2xsa, $(PLATFORM))
TEMP_DIR := _x_temp.$(TARGET).$(XPLATFORM)
TEMP_REPORT_DIR := $(CUR_DIR)/reports/_x.$(TARGET).$(XPLATFORM)
BUILD_DIR := build_dir.$(TARGET).$(XPLATFORM)
BUILD_REPORT_DIR := $(CUR_DIR)/reports/_build.$(TARGET).$(XPLATFORM)
EMCONFIG_DIR := $(BUILD_DIR)
XCLBIN_DIR := $(CUR_DIR)/$(BUILD_DIR)
export XCL_BINDIR = $(XCLBIN_DIR)

# ####################### Setting environment variables for embedded #####################
ifneq ($(HOST_ARCH), x86)
SDCARD := sd_card
EMU_DIR := $(SDCARD)/data/emulation
endif
RUN_APP_SCRIPT = ./run_script.sh

# ######################### Setting up Host Variables #########################
#Include Required Host Source Files
HOST_SRCS += $(CUR_DIR)/src/ps/host.cpp
HOST_SRCS += $(XF_PROJ_ROOT)/common/includes/xcl2/xcl2.cpp
HOST_SRCS += $(CUR_DIR)/Work/ps/c_rts/aie_control_xrt.cpp
CXXFLAGS += -I$(XF_PROJ_ROOT)/common/includes/xcl2
CXXFLAGS += -I$(SYSROOT)/usr/include/xrt
CXXFLAGS += -I$(CUR_DIR)/src/ps
CXXFLAGS += -I$(CUR_DIR)/src/aie
LDFLAGS += -L$(SYSROOT)/usr/lib/
LDFLAGS += --sysroot=$(SYSROOT)
CXXFLAGS += -I$(XILINX_VITIS_AIETOOLS)/include
CXXFLAGS += -Wno-int-to-pointer-cast

# ############################# Setting AIETARGET ##############################
ifeq ($(TARGET),aiesim)
AIETARGET := hw
else ifeq ($(TARGET),x86sim)
AIETARGET := x86sim
else ifeq ($(TARGET),hw_emu)
AIETARGET := hw
else
AIETARGET := $(TARGET)
endif

AIE_CXXFLAGS += --target=$(AIETARGET) --platform=$(XPLATFORM) --pl-freq=100 
AIE_CXXFLAGS += -I $(XILINX_VITIS_AIETOOLS)/include
AIE_CXXFLAGS += -I $(CUR_DIR)/src/aie

ifeq ($(TARGET),sw_emu)
CXXFLAGS += -D SW_EMU_TEST
endif

ifeq ($(TARGET),hw_emu)
CXXFLAGS += -D HW_EMU_TEST
endif

# ######################### Host compiler global settings ############################
CXXFLAGS += -I$(SYSROOT)/usr/include/xrt -I$(XILINX_HLS)/include -std=c++14 -O3 -Wall -Wno-unknown-pragmas -Wno-unused-label
CXXFLAGS += -fmessage-length=0 --sysroot=$(SYSROOT)
LDFLAGS += -L$(SYSROOT)/usr/lib -L$(XILINX_VITIS_AIETOOLS)/lib/aarch64.o $(LDXRT) -lxilinxopencl -lpthread -lc -lrt -ldl -lcrypt -lstdc++ -lxrt++ -lxrt_coreutil


# ################### Setting package and image directory #######################
PACKAGE_OUT = package_$(TARGET)
K_IMAGE := $(EDGE_COMMON_SW)/Image

EXE_NAME := host.elf
EXE_FILE := $(BUILD_DIR)/$(EXE_NAME)

# ##################### Kernel compiler global settings ##########################
VPP_FLAGS += -t $(TARGET) --platform $(XPLATFORM) --save-temps --optimize 2
VPP_FLAGS += --hls.jobs 8
VPP_LDFLAGS += --vivado.synth.jobs 8 --vivado.impl.jobs 8
AIE_LDFLAGS := --config $(CUR_DIR)/system.cfg

VPP_FLAGS += -I$(CUR_DIR)/src/pl

mm2s_VPP_FLAGS += --hls.clock 100000000:mm2s
s2mm_VPP_FLAGS += --hls.clock 100000000:s2mm


# Kernel args

# ############################ Declaring Binary Containers ##########################

BINARY_CONTAINERS += $(BUILD_DIR)/kernel_pkg.xclbin
BINARY_CONTAINERS_PKG += $(BUILD_DIR)/kernel.xclbin
BINARY_CONTAINER_kernel_OBJS += $(TEMP_DIR)/mm2s.xo
BINARY_CONTAINER_kernel_OBJS += $(TEMP_DIR)/s2mm.xo
AIE_CONTAINERS := ./libadf.a
AIE_CONTAINER_OBJS := $(AIE_CONTAINERS)

# ######################### Setting Targets of Makefile ################################

.PHONY: all clean cleanall docs emconfig
ifeq ($(HOST_ARCH), x86)
all:  check_version check_vpp check_platform check_xrt $(EXE_FILE) $(BINARY_CONTAINERS) emconfig
else
all:  check_version check_vpp check_platform check_edge_common_sw $(EXE_FILE) $(BINARY_CONTAINERS) emconfig  sd_card
endif

.PHONY: host
ifeq ($(HOST_ARCH), x86)
host:   check_xrt $(EXE_FILE)
else
host:   check_edge_common_sw $(EXE_FILE)
endif

.PHONY: xclbin
ifeq ($(HOST_ARCH), x86)
xclbin: check_vpp  check_xrt $(AIE_CONTAINERS) $(BINARY_CONTAINERS)
else
xclbin: check_vpp  check_edge_common_sw $(AIE_CONTAINERS) $(BINARY_CONTAINERS)
endif

.PHONY: aiesim
aiesim: $(AIE_CONTAINERS)
	$(AIESIMULATOR) --pkg-dir ./Work  --input-dir ./ --output-dir ./ --profile

.PHONY: x86sim
x86sim: $(AIE_CONTAINERS)
	$(X86SIMULATOR) --pkg-dir=./Work --input-dir=./ --output-dir=./


# ################## Setting Rules for AIE (Building Kernels) ####################

$(AIE_CONTAINERS): $(CUR_DIR)/src/aie/graph.cpp
	$(ECHO) "Compiling: libsdf.a"
	mkdir -p $(BUILD_DIR)
	$(AIECXX) $(libsdf.a_AIE_CXXFLAGS) $(AIE_CXXFLAGS) $^
$(TEMP_DIR)/mm2s.xo: $(CUR_DIR)/src/pl/mm2s.cpp
	$(ECHO) "Compiling Kernel: mm2s"
	mkdir -p $(TEMP_DIR)
	$(VPP) -c $(mm2s_VPP_FLAGS) $(VPP_FLAGS) -k mm2s -I'$(<D)' --temp_dir $(TEMP_DIR) --report_dir $(TEMP_REPORT_DIR) -o'$@' $^
$(TEMP_DIR)/s2mm.xo: $(CUR_DIR)/src/pl/s2mm.cpp
	$(ECHO) "Compiling Kernel: s2mm"
	mkdir -p $(TEMP_DIR)
	$(VPP) -c $(s2mm_VPP_FLAGS) $(VPP_FLAGS) -k s2mm -I'$(<D)' --temp_dir $(TEMP_DIR) --report_dir $(TEMP_REPORT_DIR) -o'$@' $^


$(BUILD_DIR)/kernel_pkg.xclbin: $(BINARY_CONTAINER_kernel_OBJS) $(AIE_CONTAINER_OBJS)
	mkdir -p $(BUILD_DIR)
	$(VPP) -l $(VPP_FLAGS) --temp_dir $(TEMP_DIR) --report_dir $(BUILD_REPORT_DIR)/kernel $(VPP_LDFLAGS) $(VPP_LDFLAGS_kernel) $(AIE_LDFLAGS) -o '$@' $^


# ################# Setting Rules for Host (Building Host Executable) ################
ifeq ($(HOST_ARCH), x86)
$(EXE_FILE): $(AIE_CONTAINERS) $(HOST_SRCS) | check_xrt 
else
$(EXE_FILE): $(AIE_CONTAINERS) $(HOST_SRCS) | check_edge_common_sw 
endif

	mkdir -p $(BUILD_DIR)
	$(CXX) -o $@ $^ $(CXXFLAGS) $(LDFLAGS)

emconfig:$(EMCONFIG_DIR)/emconfig.json
$(EMCONFIG_DIR)/emconfig.json:
	emconfigutil --platform $(XPLATFORM) --od $(EMCONFIG_DIR)


# ########## Setting Essential Checks and Building Rules for versal flow ############

BUILD_OBJS :=
ifeq ($(TARGET),$(filter $(TARGET), x86sim))
	BUILD_OBJS := $(AIE_CONTAINERS)
endif
ifeq ($(TARGET),$(filter $(TARGET), aiesim))
	BUILD_OBJS := $(AIE_CONTAINERS)
endif
ifeq ($(TARGET),$(filter $(TARGET), hw_emu))
	BUILD_OBJS := $(AIE_CONTAINERS) $(BINARY_CONTAINERS) $(EXE_FILE) sd_card
endif
ifeq ($(TARGET),$(filter $(TARGET), hw))
	BUILD_OBJS := $(AIE_CONTAINERS) $(BINARY_CONTAINERS) $(EXE_FILE) sd_card
endif

.PHONY: build
build: $(BUILD_OBJS)

# ############### Setting Essential Checks And Running Rules For Vitis Vitis ############
run: build
ifeq ($(TARGET),$(filter $(TARGET), x86sim))
	$(X86SIMULATOR) --pkg-dir=./Work --input-dir=./ --output-dir=./
endif
ifeq ($(TARGET),$(filter $(TARGET), aiesim))
	$(AIESIMULATOR) --pkg-dir=./Work  --input-dir=./ --output-dir=./ --profile
endif
ifeq ($(TARGET),$(filter $(TARGET), hw_emu))
	export XCL_EMULATION_MODE=$(TARGET)
	./$(PACKAGE_OUT)/launch_$(TARGET).sh -noc-memory-config qemu-memory-_ddr@0x00000000 -no-reboot -run-app ./run_script.sh | tee run_app.log; exit $${PIPESTATUS[0]}
endif
ifeq ($(TARGET),$(filter $(TARGET), hw))
ifeq ($(HOST_ARCH), x86)
	$(EXE_FILE) $(HOST_ARGS)
else
	$(ECHO) "Please copy the content of sd_card folder and data to an SD Card and run on the board"
endif
endif

# ######################### Preparing sdcard folder ##############################
EMBEDDED_EXEC_SCRIPT = run_script.sh
SD_FILES += $(EMBEDDED_EXEC_SCRIPT)
SD_FILES += $(EXE_FILE)
SD_FILES += emconfig.json
SD_FILES += $(DATA_FILE)
SD_FILES += $(AIE_CONTAINERS)
SD_FILES_WITH_PREFIX = $(foreach sd_file,$(SD_FILES),--package.sd_file $(sd_file))
SD_DIRS_WITH_PREFIX = $(foreach sd_dir,$(DATA_DIR),--package.sd_dir $(sd_dir))
PACKAGE_FILES := $(BINARY_CONTAINERS)
PACKAGE_FILES += $(AIE_CONTAINERS)
sd_card: host xclbin emconfig
ifneq ($(HOST_ARCH), x86)
	@echo "Generating sd_card folder...."
	mkdir -p $(PACKAGE_OUT)
	rm -rf run_script.sh
	@echo 'export LD_LIBRARY_PATH=/mnt:/tmp:$$LD_LIBRARY_PATH' >> run_script.sh
ifeq ($(TARGET), $(filter $(TARGET),sw_emu hw_emu))
	@echo 'export XCL_EMULATION_MODE=$(TARGET)' >> run_script.sh
endif
	@echo 'export XILINX_VITIS=/mnt' >> run_script.sh
	@echo 'export XILINX_XRT=/usr' >> run_script.sh
	@echo 'if [ -f platform_desc.txt  ]; then' >> run_script.sh
	@echo '         cp platform_desc.txt /etc/xocl.txt' >> run_script.sh
	@echo 'fi'  >> run_script.sh
	emconfigutil --platform $(PLATFORM) --nd 1;
	@echo './$(EXE_NAME) $(PKG_HOST_ARGS)' >> run_script.sh
	@echo 'return_code=$$?' >> run_script.sh
	@echo 'if [ $$return_code -ne 0 ]; then' >> run_script.sh
	@echo '         echo "ERROR: Embedded host run failed, RC=$$return_code"' >> run_script.sh
	@echo 'fi' >> run_script.sh
	@echo 'echo "INFO: Embedded host run completed."' >> run_script.sh
	@echo 'exit $$return_code' >> run_script.sh
	chmod a+rx run_script.sh
	$(VPP) -t $(TARGET) --platform $(PLATFORM) -o $(BINARY_CONTAINERS_PKG) -p $(PACKAGE_FILES) --package.defer_aie_run --package.out_dir $(PACKAGE_OUT) --package.rootfs $(EDGE_COMMON_SW)/rootfs.ext4 --package.kernel_image $(K_IMAGE) --package.boot_mode sd $(SD_FILES_WITH_PREFIX) $(SD_DIRS_WITH_PREFIX)
	@echo "### ***** sd_card generation done! ***** ###"
endif

# ################################# Cleaning Rules ##################################
cleanh:
	-$(RMDIR) $(EXE_FILE) vitis_* TempConfig system_estimate.xtxt *.rpt .run/
	-$(RMDIR) src/*.ll _xocc_* .Xil dltmp* xmltmp* *.log *.jou *.wcfg *.wdb sample_link.ini sample_compile.ini obj* bin* *.csv *.jpg *.jpeg *.png

cleank:
	-$(RMDIR) $(BUILD_DIR)/*.xclbin _vimage *xclbin.run_summary qemu-memory-_* emulation/ _vimage/ start_simulation.sh *.xclbin
	-$(RMDIR) _x_temp.*/_x.* _x_temp.*/.Xil _x_temp.*/profile_summary.* xo_* _x*
	-$(RMDIR) _x_temp.*/dltmp* _x_temp.*/kernel_info.dat _x_temp.*/*.log 
	-$(RMDIR) _x_temp.* 

cleanall: cleanh cleank
	-$(RMDIR) $(BUILD_DIR) sd_card* build_dir.* emconfig.json *.html $(TEMP_DIR) $(CUR_DIR)/reports *.csv *.run_summary $(CUR_DIR)/*.raw package_* run_script.sh .ipcache *.str
	-$(RMDIR) $(AIE_CONTAINERS) $(CUR_DIR)/Work $(CUR_DIR)/*.xpe $(CUR_DIR)/hw.o $(CUR_DIR)/*.xsa $(CUR_DIR)/xnwOut aiesimulator_output .AIE_SIM_CMD_LINE_OPTIONS

clean: cleanh
