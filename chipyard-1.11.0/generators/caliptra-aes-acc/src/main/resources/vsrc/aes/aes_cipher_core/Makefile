# See LICENSE for license details

BASE_DIR = $(abspath ../../../../../../../..)
VSRC_DIR = $(abspath .)

PKG_FILES = \
    $(VSRC_DIR)/rtl/entropy_src_pkg.sv \
    $(VSRC_DIR)/rtl/edn_pkg.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_util_pkg.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_pkg.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_sparse_fsm_pkg.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_cipher_pkg.sv \
    $(VSRC_DIR)/rtl/aes_reg_pkg.sv \
    $(VSRC_DIR)/rtl/aes_pkg.sv \
    $(VSRC_DIR)/rtl/aes_sbox_canright_pkg.sv

TB_FILES = \
    $(VSRC_DIR)/aes_cipher_core_tb.sv

WRAPPER_FILES = \
    $(VSRC_DIR)/AesCipherCoreWrapper_AES256_ECB_NoMask.sv

AES_FILES = \
    $(VSRC_DIR)/rtl/aes_cipher_control_fsm_n.sv \
    $(VSRC_DIR)/rtl/aes_cipher_control_fsm_p.sv \
    $(VSRC_DIR)/rtl/aes_cipher_control_fsm.sv \
    $(VSRC_DIR)/rtl/aes_cipher_control.sv \
    $(VSRC_DIR)/rtl/aes_cipher_core.sv \
    $(VSRC_DIR)/rtl/aes_key_expand.sv \
    $(VSRC_DIR)/rtl/aes_mix_columns.sv \
    $(VSRC_DIR)/rtl/aes_mix_single_column.sv \
    $(VSRC_DIR)/rtl/aes_prng_masking.sv \
    $(VSRC_DIR)/rtl/aes_sbox_canright_masked_noreuse.sv \
    $(VSRC_DIR)/rtl/aes_sbox_canright_masked.sv \
    $(VSRC_DIR)/rtl/aes_sbox_canright.sv \
    $(VSRC_DIR)/rtl/aes_sbox_dom.sv \
    $(VSRC_DIR)/rtl/aes_sbox_lut.sv \
    $(VSRC_DIR)/rtl/aes_sbox.sv \
    $(VSRC_DIR)/rtl/aes_sel_buf_chk.sv \
    $(VSRC_DIR)/rtl/aes_shift_rows.sv \
    $(VSRC_DIR)/rtl/aes_sub_bytes.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_buf.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_sparse_fsm_flop.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_flop.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_flop_en.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_lfsr.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_generic_flop.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_generic_flop_en.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_generic_buf.sv \
    $(VSRC_DIR)/rtl/caliptra_prim_sec_anchor_buf.sv

lookup_dirs = $(shell find -L $(VSRC_DIR) -name target -prune -o -type d -print 2> /dev/null | grep '.*/\($(1)\)$$')
INC_DIR_NAMES ?= rtl
INC_DIRS ?= $(foreach dir_name,$(INC_DIR_NAMES),$(call lookup_dirs,$(dir_name)))

PREPROC_SCRIPT = $(BASE_DIR)/scripts/insert-includes.py

PREPROC_DEFINES ?= \
	SYNTHESIS

.PHONY: default
default: core

TB_FILE = $(VSRC_DIR)/tb.sv
.PHONY: tb
tb: $(TB_FILE)
$(TB_FILE): $(PKG_FILES) $(TB_FILES) $(AES_FILES)
	mkdir -p $(dir $@)
	$(foreach def,$(PREPROC_DEFINES),echo "\`define $(def)" >> def.v; )
	$(foreach def,$(PREPROC_DEFINES),echo "\`undef $(def)" >> undef.v; )
	cat def.v $^ undef.v > combined.sv
	$(PREPROC_SCRIPT) combined.sv $@ $(INC_DIRS)
	rm -rf combined.sv def.v undef.v

CORE_FILE = $(VSRC_DIR)/core.sv
.PHONY: core
core: $(CORE_FILE)
$(CORE_FILE): $(PKG_FILES) $(WRAPPER_FILES) $(AES_FILES)
	mkdir -p $(dir $@)
	$(foreach def,$(PREPROC_DEFINES),echo "\`define $(def)" >> def.v; )
	$(foreach def,$(PREPROC_DEFINES),echo "\`undef $(def)" >> undef.v; )
	cat def.v $^ undef.v > combined.sv
	$(PREPROC_SCRIPT) combined.sv $@ $(INC_DIRS)
	rm -rf combined.sv def.v undef.v

clean:
	rm -rf $(TB_FILE) $(CORE_FILE)
