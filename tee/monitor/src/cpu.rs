use crate::isolator;
use crate::os_region_id;
use core::arch::asm;

#[macro_export]
macro_rules! csr_read {
    ($csr:ident) => {{
        use core::arch::asm;
        let res: usize;
        unsafe {
            asm!(
                concat!("csrr {reg}, ", stringify!($csr)) ,
                reg = out(reg) res,
            );
        }
        res
    }}
}

#[macro_export]
macro_rules! csr_read_custom {
    ($csr:expr) => {{
        use core::arch::asm;
        let res: usize;
        unsafe {
            asm!(
                "csrr {reg}, {csr}",
                reg = out(reg) res,
                csr = const $csr,
            );
        }
        res
    }}
}

#[macro_export]
macro_rules! csr_write {
    ($csr:ident, $val:expr) => {{
        use core::arch::asm;
        unsafe {
            asm!(
                concat!("csrw ", stringify!($csr), ", {reg}"),
                reg = in(reg) $val,
            );
        }
    }}
}

#[macro_export]
macro_rules! csr_write_custom {
    ($csr:expr , $val:expr) => {{
        use core::arch::asm;
        unsafe {
            asm!(
            "csrw {csr}, {reg}",
            csr = const $csr,
            reg = in(reg) $val,
            );
        }
    }}
}

#[macro_export]
macro_rules! csr_swap {
    ($csr:ident, $val:expr) => {{
        use core::arch::asm;
        let res = val;
        unsafe {
            asm!(
                concat!("csrrw {reg0}, ", stringify!($csr), ", {reg1}"),
                reg0 = out(reg) res,
                reg1 = in(reg) res,
            );
        }
        res
    }}
}

#[macro_export]
macro_rules! csr_read_set {
    ($csr:ident, $val:expr) => {{
        use core::arch::asm;
        let res = $val;
        unsafe {
            asm!(
                concat!("csrrs {reg0}, ", stringify!($csr), ", {reg1}"),
                reg0 = out(reg) res,
                reg1 = in(reg) res,
            );
        }
        res
    }}
}

#[macro_export]
macro_rules! csr_set {
    ($csr:ident, $val:expr) => {{
        use core::arch::asm;
        unsafe {
            asm!(
                concat!("csrs ", stringify!($csr), ", {reg}"),
                reg = in(reg) $val,
            );
        }
    }}
}

#[macro_export]
macro_rules! csr_read_clear {
    ($csr:ident, $val:expr) => {{
        use core::arch::asm;
        let res = $val;
        unsafe {
            asm!(
                concat!("csrrc {reg0}, ", stringify!($csr), ", {reg1}"),
                reg0 = out(reg) res,
                reg1 = in(reg) res,
            );
        }
        res
    }}
}

#[macro_export]
macro_rules! csr_clear {
    ($csr:ident, $val:expr) => {{
        use core::arch::asm;
        unsafe {
            asm!(
                concat!("csrc ", stringify!($csr), ", {reg}"),
                reg = in(reg) $val,
            );
        }
    }}
}

/* hart state for regulating SBI */
struct CpuState {
    is_enclave: bool,
    eid: usize,
}

const MAX_HARTS: usize = 16;

const INIT: CpuState = CpuState {
    is_enclave: false,
    eid: 0,
};

static mut CPU_STATE: [CpuState; MAX_HARTS] = [INIT; MAX_HARTS];

pub fn is_enclave_context() -> bool {
    let hartid = csr_read!(mhartid) as usize;
    unsafe { CPU_STATE[hartid].is_enclave }
}

pub fn get_enclave_id() -> usize {
    let hartid = csr_read!(mhartid) as usize;
    unsafe { CPU_STATE[hartid].eid }
}

pub fn enter_enclave_context(eid: usize) {
    let hartid = csr_read!(mhartid) as usize;
    unsafe {
        CPU_STATE[hartid].is_enclave = true;
        CPU_STATE[hartid].eid = eid;
    }
    isolator::enter_context(eid);
}

pub fn exit_enclave_context() {
    let hartid = csr_read!(mhartid) as usize;
    unsafe {
        CPU_STATE[hartid].is_enclave = false;
    };
    isolator::enter_context(os_region_id());
}
