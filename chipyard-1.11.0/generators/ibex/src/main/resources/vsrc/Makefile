base_dir=$(abspath ../../../../../..)
vsrc_dir=$(abspath .)
ibex_dir=$(vsrc_dir)/ibex

PREPROC_VERILOG = IbexCoreBlackbox.preprocessed.sv

.PHONY: default $(PREPROC_VERILOG)
default: $(PREPROC_VERILOG)

IBEX_PKGS = \
	$(ibex_dir)/rtl/ibex_pkg.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_assert.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_ram_1p_pkg.sv \
	$(ibex_dir)/dv/uvm/core_ibex/common/prim/prim_pkg.sv

IBEX_VSRCS = \
	$(ibex_dir)/rtl/ibex_alu.sv \
	$(ibex_dir)/rtl/ibex_branch_predict.sv \
	$(ibex_dir)/rtl/ibex_compressed_decoder.sv \
	$(ibex_dir)/rtl/ibex_controller.sv \
	$(ibex_dir)/rtl/ibex_core.sv \
	$(ibex_dir)/rtl/ibex_counter.sv \
	$(ibex_dir)/rtl/ibex_cs_registers.sv \
	$(ibex_dir)/rtl/ibex_csr.sv \
	$(ibex_dir)/rtl/ibex_decoder.sv \
	$(ibex_dir)/rtl/ibex_dummy_instr.sv \
	$(ibex_dir)/rtl/ibex_ex_block.sv \
	$(ibex_dir)/rtl/ibex_fetch_fifo.sv \
	$(ibex_dir)/rtl/ibex_icache.sv \
	$(ibex_dir)/rtl/ibex_id_stage.sv \
	$(ibex_dir)/rtl/ibex_if_stage.sv \
	$(ibex_dir)/rtl/ibex_load_store_unit.sv \
	$(ibex_dir)/rtl/ibex_lockstep.sv \
	$(ibex_dir)/rtl/ibex_multdiv_fast.sv \
	$(ibex_dir)/rtl/ibex_multdiv_slow.sv \
	$(ibex_dir)/rtl/ibex_pmp.sv \
	$(ibex_dir)/rtl/ibex_prefetch_buffer.sv \
	$(ibex_dir)/rtl/ibex_register_file_ff.sv \
	$(ibex_dir)/rtl/ibex_register_file_fpga.sv \
	$(ibex_dir)/rtl/ibex_register_file_latch.sv \
	$(ibex_dir)/rtl/ibex_top.sv \
	$(ibex_dir)/rtl/ibex_wb_stage.sv \
	$(ibex_dir)/dv/uvm/core_ibex/common/prim/prim_clock_gating.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim_generic/rtl/prim_generic_clock_gating.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_secded_39_32_enc.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_secded_39_32_dec.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_lfsr.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_secded_28_22_enc.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim/rtl/prim_secded_28_22_dec.sv \
	$(ibex_dir)/dv/uvm/core_ibex/common/prim/prim_ram_1p.sv \
	$(ibex_dir)/dv/uvm/core_ibex/common/prim/prim_buf.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim_generic/rtl/prim_generic_ram_1p.sv \
	$(ibex_dir)/vendor/lowrisc_ip/ip/prim_generic/rtl/prim_generic_buf.sv 

IBEX_WRAPPER = \
	$(vsrc_dir)/IbexCoreBlackbox.sv

ALL_VSRCS = $(IBEX_PKGS) $(IBEX_VSRCS) $(IBEX_WRAPPER)

lookup_dirs = $(shell find -L $(ibex_dir) -name target -prune -o -type d -print 2> /dev/null | grep '.*/\($(1)\)$$')
INC_DIR_NAMES ?= vendor/lowrisc_ip/ip/prim/rtl formal/data_ind_timing vendor/lowrisc_ip/dv/sv/dv_utils
INC_DIRS ?= $(foreach dir_name,$(INC_DIR_NAMES),$(call lookup_dirs,$(dir_name)))

# these flags are specific to Chipyard
EXTRA_PREPROC_DEFINES ?=
PREPROC_DEFINES ?= \
	VERILATOR \
	$(EXTRA_PREPROC_DEFINES)

PREPROC_SCRIPT = $(base_dir)/scripts/insert-includes.py

$(PREPROC_VERILOG): $(ALL_VSRCS)
	mkdir -p $(dir $(PREPROC_VERILOG))
	$(foreach def,$(PREPROC_DEFINES),echo "\`define $(def)" >> def.v; )
	$(foreach def,$(PREPROC_DEFINES),echo "\`undef $(def)" >> undef.v; )
	cat def.v $(ALL_VSRCS) undef.v > combined.sv
	$(PREPROC_SCRIPT) combined.sv $@ $(INC_DIRS)
	sed -i '/.*prim_xilinx_clock_gating/,/;/c\    // Remove when upgrading verilator version' $@ # Remove FPGA module so Ibex works in Verilator v4.304
	rm -rf combined.sv def.v undef.v

clean:
	rm -rf $(PREPROC_VERILOG)