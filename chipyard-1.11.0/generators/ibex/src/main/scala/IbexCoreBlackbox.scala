//******************************************************************************
// Copyright (c) 2021 - 2021, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// Ibex Tile Wrapper
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package ibex

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam, RawParam}

import scala.collection.mutable.{ListBuffer}

class IbexCoreBlackbox(
    pmpEnable: Int,
    pmpGranularity: Int,
    pmpNumRegions: Int,
    mhpmCounterNum: Int,
    mhpmCounterWidth: Int,
    rv32e: Int,
    rv32m: Int,
    rv32b: Int,
    regfile: Int,
    branchTargetALU: Int,
    wbStage: Int,
    branchPredictor: Int,
    dbgHwBreakNum: Int,
    dmHaltAddr: Int,
    dmExceptionAddr: Int)
    extends BlackBox(
        Map(
            "PMP_ENABLE" -> IntParam(pmpEnable),
            "PMP_GRANULARITY" -> IntParam(pmpGranularity),
            "PMP_NUM_REGIONS" -> IntParam(pmpNumRegions),
            "MHPM_COUNTER_NUM" -> IntParam(mhpmCounterNum),
            "MHPM_COUNTER_WIDTH" -> IntParam(mhpmCounterWidth),
            "RV32E" -> IntParam(rv32e),
            "RV32M" -> IntParam(rv32m),
            "RV32B" -> IntParam(rv32b),
            "REGFILE" -> IntParam(regfile),
            "BRANCH_TARGET_ALU" -> IntParam(branchTargetALU),
            "WB_STAGE" -> IntParam(wbStage),
            "BRANCH_PREDICTOR" -> IntParam(branchPredictor),
            "DBG_HW_BREAK_NUM" -> IntParam(dbgHwBreakNum),
            "DM_HALT_ADDR" -> IntParam(dmHaltAddr),
            "DM_EXCEPTION_ADDR" -> IntParam(dmExceptionAddr))
)
    with HasBlackBoxPath
{
    val io = IO(new Bundle {
        val clk_i = Input(Clock())
        val rst_ni = Input(Bool())
        val test_en_i = Input(Bool())
        val ram_cfg_i_ram_cfg_en = Input(Bool())
        val ram_cfg_i_ram_cfg = Input(UInt(4.W))
        val ram_cfg_i_rf_cfg_en = Input(Bool())
        val ram_cfg_i_rf_cfg = Input(UInt(4.W))
        val hart_id_i = Input(UInt(32.W))
        val boot_addr_i = Input(UInt(32.W))
        val instr_req_o = Output(Bool())
        val instr_gnt_i = Input(Bool())
        val instr_rvalid_i = Input(Bool())
        val instr_addr_o = Output(UInt(32.W))
        val instr_rdata_i = Input(UInt(32.W))
        val instr_err_i = Input(Bool())
        val data_req_o = Output(Bool())
        val data_gnt_i = Input(Bool())
        val data_rvalid_i = Input(Bool())
        val data_we_o = Output(Bool())
        val data_be_o = Output(UInt(4.W))
        val data_addr_o = Output(UInt(32.W))
        val data_wdata_o = Output(UInt(32.W))
        val data_rdata_i = Input(UInt(32.W))
        val data_err_i = Input(Bool())
        val irq_software_i = Input(Bool())
        val irq_timer_i = Input(Bool())
        val irq_external_i = Input(Bool())
        val irq_fast_i = Input(UInt(15.W))
        val irq_nm_i = Input(Bool())
        val debug_req_i = Input(Bool())
        val crash_dump_o_current_pc = Output(UInt(32.W))
        val crash_dump_o_next_pc = Output(UInt(32.W))
        val crash_dump_o_last_data_addr = Output(UInt(32.W))
        val crash_dump_o_exception_addr = Output(UInt(32.W))
        val fetch_enable_i = Input(Bool())
        val alert_minor_o = Output(Bool())
        val alert_major_o = Output(Bool())
        val core_sleep_o = Output(Bool())
        val scan_rst_ni = Input(Bool())
    })

    val chipyardDir = System.getProperty("user.dir")
    val ibexVsrcDir = s"$chipyardDir/generators/ibex/src/main/resources/vsrc"

    val proc = s"make -C $ibexVsrcDir default"
    require (proc.! == 0, "Failed to run preprocessing step")

    // generated from preprocessing step
    addPath(s"$ibexVsrcDir/IbexCoreBlackbox.preprocessed.sv")
}
