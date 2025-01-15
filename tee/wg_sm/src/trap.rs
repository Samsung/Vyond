#[repr(C)]
pub struct TrapFrame {
    pub zero: usize,     // zero register state
    pub ra: usize,       // ra register state
    pub sp: usize,       // sp register state
    pub gp: usize,       // gp register state
    pub tp: usize,       // tp register state
    pub t0: usize,       // t0 register state
    pub t1: usize,       // t1 register state
    pub t2: usize,       // t2 register state
    pub s0: usize,       // s0 register state
    pub s1: usize,       // s1 register state
    pub a0: usize,       // a0 register state
    pub a1: usize,       // a1 register state
    pub a2: usize,       // a2 register state
    pub a3: usize,       // a3 register state
    pub a4: usize,       // a4 register state
    pub a5: usize,       // a5 register state
    pub a6: usize,       // a6 register state
    pub a7: usize,       // a7 register state
    pub s2: usize,       // s2 register state
    pub s3: usize,       // s3 register state
    pub s4: usize,       // s4 register state
    pub s5: usize,       // s5 register state
    pub s6: usize,       // s6 register state
    pub s7: usize,       // s7 register state
    pub s8: usize,       // s8 register state
    pub s9: usize,       // s9 register state
    pub s10: usize,      // s10 register state
    pub s11: usize,      // s11 register state
    pub t3: usize,       // t3 register state
    pub t4: usize,       // t4 register state
    pub t5: usize,       // t5 register state
    pub t6: usize,       // t6 register state
    pub mepc: usize,     // mepc register state
    pub mstatus: usize,  // mstatus register state
    pub mstatush: usize, // mstatusH register state (only for 32-bit)
}

impl Default for TrapFrame {
    fn default() -> Self {
        TrapFrame {
            zero: 0,
            ra: 0,
            sp: 0,
            gp: 0,
            tp: 0,
            t0: 0,
            t1: 0,
            t2: 0,
            s0: 0,
            s1: 0,
            a0: 0,
            a1: 0,
            a2: 0,
            a3: 0,
            a4: 0,
            a5: 0,
            a6: 0,
            a7: 0,
            s2: 0,
            s3: 0,
            s4: 0,
            s5: 0,
            s6: 0,
            s7: 0,
            s8: 0,
            s9: 0,
            s10: 0,
            s11: 0,
            t3: 0,
            t4: 0,
            t5: 0,
            t6: 0,
            mepc: 0,
            mstatus: 0,
            mstatush: 0,
        }
    }
}

/// Representation of trap details
pub struct TrapInfo {
    pub epc: usize,   // epc Trap program counter
    pub cause: usize, // cause Trap exception cause
    pub tval: usize,  // tval Trap value
    pub tval2: usize, // tval2 Trap value 2
    pub tinst: usize, // tinst Trap instruction
    pub gva: usize,   // gva Guest virtual address in tval flag
}
