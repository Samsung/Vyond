use core::arch::asm;
use core::ptr::swap;

use crate::encoding::*;
use crate::trap::TrapFrame;
use crate::{csr_read, csr_write};

/* enclave thread state */
pub struct State {
    mpp: isize,
    mepc: usize,
    mstatus: usize,

    sstatus: usize, //Supervisor status register.
    //sedeleg: usize,    //Supervisor exception delegation register.
    //sideleg: usize,    //Supervisor interrupt delegation register.
    sie: usize,        //Supervisor interrupt-enable register.
    stvec: usize,      //Supervisor trap handler base address.
    scounteren: usize, //Supervisor counter enable

    /*  Supervisor Trap Handling */
    sscratch: usize, //Scratch register for supervisor trap handlers.
    sepc: usize,     //Supervisor exception program counter.
    scause: usize,   //Supervisor trap cause.
    //NOTE: This should be stval, toolchain issue?
    //sbadaddr: usize, //Supervisor bad address.
    sip: usize, //Supervisor interrupt pending.

    /*  Supervisor Protection and Translation */
    satp: usize, //Page-table base register.
    context: TrapFrame,
}

impl Default for State {
    fn default() -> Self {
        State {
            mpp: 0,
            mepc: 0,
            mstatus: 0,

            sstatus: 0, //Supervisor status register.
            //sedeleg: 0,    //Supervisor exception delegation register.
            //sideleg: 0,    //Supervisor interrupt delegation register.
            sie: 0,        //Supervisor interrupt-enable register.
            stvec: 0,      //Supervisor trap handler base address.
            scounteren: 0, //Supervisor counter enable

            /*  Supervisor Trap Handling */
            sscratch: 0, //Scratch register for supervisor trap handlers.
            sepc: 0,     //Supervisor exception program counter.
            scause: 0,   //Supervisor trap cause.
            //NOTE: This should be stval, toolchain issue?
            //sbadaddr: 0, //Supervisor bad address.
            sip: 0, //Supervisor interrupt pending.

            /*  Supervisor Protection and Translation */
            satp: 0, //Page-table base register.
            context: TrapFrame::default(),
        }
    }
}

extern "C" {
    fn trap_vector_enclave();
    fn _trap_handler();
}

pub fn switch_vector_enclave() {
    csr_write!(mtvec, &trap_vector_enclave);
}

pub fn switch_vector_host() {
    csr_write!(mtvec, &_trap_handler);
}

/* Swaps all s-mode csrs defined in 1.10 standard */
/* TODO: Right now we are only handling the ones that our test
platforms support. Realistically we should have these behind
defines for extensions (ex: N extension)*/

impl State {
    pub fn new(entry: usize, status: usize) -> Self {
        State {
            mepc: entry,
            mstatus: status,
            ..Default::default()
        }
    }

    pub fn swap_prev_mstatus(&mut self, regs: &mut TrapFrame, current_mstatus: usize) {
        //Time interrupts can occur in either user mode or supervisor mode
        let mstatus_mask = MSTATUS_SIE
            | MSTATUS_SPIE
            | MSTATUS_SPP
            | MSTATUS_MPP
            | MSTATUS_FS
            | MSTATUS_SUM
            | MSTATUS_MXR;

        let tmp = self.mstatus;
        self.mstatus = (current_mstatus & !mstatus_mask) | (current_mstatus & mstatus_mask);
        regs.mstatus = (current_mstatus & !mstatus_mask) | tmp;
    }

    /* Swaps the entire s-mode visible state, general registers and then csrs */
    pub fn swap_prev_state(&mut self, regs: &mut TrapFrame) {
        let state = &mut self.context;

        unsafe {
            swap(&mut state.zero, &mut regs.zero);
            swap(&mut state.ra, &mut regs.ra);
            swap(&mut state.sp, &mut regs.sp);
            swap(&mut state.gp, &mut regs.gp);
            swap(&mut state.tp, &mut regs.tp);
            swap(&mut state.t0, &mut regs.t0);
            swap(&mut state.t1, &mut regs.t1);
            swap(&mut state.t2, &mut regs.t2);
            swap(&mut state.s0, &mut regs.s0);
            swap(&mut state.s1, &mut regs.s1);
            //let a0 = state.a0;
            //let a1 = state.a1;
            //let a2 = state.a2;
            //let a3 = state.a3;
            //let a4 = state.a4;
            swap(&mut state.a5, &mut regs.a5);
            swap(&mut state.a6, &mut regs.a6);
            swap(&mut state.a7, &mut regs.a7);
            swap(&mut state.s2, &mut regs.s2);
            swap(&mut state.s3, &mut regs.s3);
            swap(&mut state.s4, &mut regs.s4);
            swap(&mut state.s5, &mut regs.s5);
            swap(&mut state.s6, &mut regs.s6);
            swap(&mut state.s7, &mut regs.s7);
            swap(&mut state.s8, &mut regs.s8);
            swap(&mut state.s9, &mut regs.s9);
            swap(&mut state.s10, &mut regs.s10);
            swap(&mut state.s11, &mut regs.s11);
            swap(&mut state.t3, &mut regs.t3);
            swap(&mut state.t4, &mut regs.t4);
            swap(&mut state.t5, &mut regs.t5);
            swap(&mut state.t6, &mut regs.t6);
        }
        self.swap_prev_smode_csrs();
    }

    pub fn swap_prev_smode_csrs(&mut self) {
        let sstatus = self.sstatus;
        //let sedeleg = self.sedeleg;
        //let sideleg = self.sideleg;
        let sie = self.sie;
        let stvec = self.stvec;
        let scounteren = self.scounteren;
        let sscratch = self.sscratch;
        let sepc = self.sepc;
        let scause = self.scause;
        //let sbadaddr = self.sbadaddr;
        let sip = self.sip;
        let satp = self.satp;

        self.sstatus = csr_read!(sstatus);
        //self.sedeleg = csr_read!(sedeleg); //sedeleg
        //self.sideleg = csr_read!(sideleg); //sideleg
        self.sie = csr_read!(sie); //sie
        self.stvec = csr_read!(stvec);
        self.scounteren = csr_read!(scounteren);
        self.sscratch = csr_read!(sscratch);
        self.sepc = csr_read!(sepc);
        self.scause = csr_read!(scause);
        //self.sbadaddr = csr_read!(sbadaddr);
        self.sip = csr_read!(sip);
        self.satp = csr_read!(satp);

        csr_write!(sstatus, sstatus);
        //csr_write!(sedeleg, sedeleg);
        //csr_write!(sideleg, sideleg);
        csr_write!(sie, sie);
        csr_write!(stvec, stvec);
        csr_write!(scounteren, scounteren);
        csr_write!(sscratch, sscratch);
        csr_write!(sepc, sepc);
        csr_write!(scause, scause);
        //csr_write!(sbadddr, sbadaddr);
        csr_write!(sip, sip);
        csr_write!(satp, satp);
    }

    pub fn swap_prev_mepc(&mut self, regs: &mut TrapFrame, current_mepc: usize) {
        let tmp = self.mepc;
        self.mepc = current_mepc;
        regs.mepc = tmp;
    }
}
