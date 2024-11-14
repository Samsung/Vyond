//******************************************************************************
// Copyright (c) 2021 - 2021, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Ibex Core Wrapper
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

module IbexCoreBlackbox
    #(
        parameter MHPM_COUNTER_NUM = 0,
        parameter MHPM_COUNTER_WIDTH = 0,
        parameter RV32E = 0,
        parameter RV32M = 2,
        parameter RV32B = 0,
        parameter REGFILE = 0,
        parameter BRANCH_TARGET_ALU = 0,
        parameter WB_STAGE = 0,
        parameter BRANCH_PREDICTOR = 0,
        parameter PMP_ENABLE = 0,
        parameter PMP_GRANULARITY = 0,
        parameter PMP_NUM_REGIONS = 0,
        parameter DBG_HW_BREAK_NUM = 0,
        parameter [31:0] DM_HALT_ADDR = 0,
        parameter [31:0] DM_EXCEPTION_ADDR = 0
    )
(
    // Clock and Reset
    input  clk_i,
    input  rst_ni,

    input  test_en_i,     		// enable all clock gates for testing

    input  ram_cfg_i_ram_cfg_en,
    input  [3:0] ram_cfg_i_ram_cfg,
    input  ram_cfg_i_rf_cfg_en,
    input  [3:0] ram_cfg_i_rf_cfg,

    input  [31:0] hart_id_i,
    input  [31:0] boot_addr_i,

    // Instruction memory interface
    output instr_req_o,
    input  instr_gnt_i,
    input  instr_rvalid_i,
    output [31:0] instr_addr_o,
    input  [31:0] instr_rdata_i,
    input  instr_err_i,

    // Data memory interface
    output data_req_o,
    input  data_gnt_i,
    input  data_rvalid_i,
    output data_we_o,
    output [3:0] data_be_o,
    output [31:0] data_addr_o,
    output [31:0] data_wdata_o,
    input  [31:0] data_rdata_i,
    input  data_err_i,

    // Interrupt inputs
    input  irq_software_i,      //msip
    input  irq_timer_i,         //mtip
    input  irq_external_i,      //meip
    input  [14:0] irq_fast_i,   //fast local interrupts
    input  irq_nm_i,            //nmi

    // Debug Interface
    input  debug_req_i, //debug

    //debugging output for ibex_lockstep.sv
    output [31:0] crash_dump_o_current_pc,
    output [31:0] crash_dump_o_next_pc,
    output [31:0] crash_dump_o_last_data_addr,
    output [31:0] crash_dump_o_exception_addr,

    // CPU Control Signals
    input  fetch_enable_i,
    output alert_minor_o,
    output alert_major_o,
    output core_sleep_o,

    // DFT bypass controls
    input  scan_rst_ni
);

    prim_ram_1p_pkg::ram_1p_cfg_t ibex_ram_config;
    ibex_pkg::crash_dump_t ibex_crash_dump;

    ibex_top #(
        .PMPEnable(PMP_ENABLE),
        .PMPGranularity(PMP_GRANULARITY),
        .PMPNumRegions(PMP_NUM_REGIONS),
        .MHPMCounterNum(MHPM_COUNTER_NUM),
        .MHPMCounterWidth(MHPM_COUNTER_WIDTH),
        .RV32E(RV32E),
        .RV32M(ibex_pkg::rv32m_e'(RV32M)),
        .RV32B(ibex_pkg::rv32b_e'(RV32B)),
        .RegFile(ibex_pkg::regfile_e'(REGFILE)),
        .BranchTargetALU(BRANCH_TARGET_ALU),
        .WritebackStage(WB_STAGE),
        .ICache(1'b0),
        .ICacheECC(1'b0),
        .BranchPredictor(BRANCH_PREDICTOR),
        .DbgTriggerEn(1'b0),
        .DbgHwBreakNum(DBG_HW_BREAK_NUM),
        .SecureIbex(1'b0),
        .DmHaltAddr(DM_HALT_ADDR),
        .DmExceptionAddr(DM_EXCEPTION_ADDR)
    ) i_ibex (
        .clk_i,
        .rst_ni,
        .test_en_i,
        .ram_cfg_i ( ibex_ram_config ),
        .hart_id_i,
        .boot_addr_i,
        .instr_req_o,
        .instr_gnt_i,
        .instr_rvalid_i,
        .instr_addr_o,
        .instr_rdata_i,
        .instr_err_i,
        .data_req_o,
        .data_gnt_i,
        .data_rvalid_i,
        .data_we_o,
        .data_be_o,
        .data_addr_o,
        .data_wdata_o,
        .data_rdata_i,
        .data_err_i,
        .irq_software_i,
        .irq_timer_i,
        .irq_external_i,
        .irq_fast_i,
        .irq_nm_i,
        .debug_req_i,
        .crash_dump_o ( ibex_crash_dump ),
        .fetch_enable_i,
        .alert_minor_o,
        .alert_major_o,
        .core_sleep_o,
        .scan_rst_ni
    );

endmodule
