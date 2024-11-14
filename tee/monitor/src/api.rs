use crate::enclave;
use crate::trap::TrapFrame;

use semihosting::hprintln;

#[no_mangle]
pub extern "C" fn sbi_sm_create_enclave(base: usize, size: usize, entry: usize) -> isize {
    hprintln!("sbi_sm_create_enclave ecall handler");
    hprintln!("start={:#x}, size={:#x}, entry={:#x}", base, size, entry);

    if let Ok(enclave) = enclave::create_enclave(base, size, entry) {
        return enclave.id() as isize;
    }

    0
}

#[no_mangle]
pub extern "C" fn sbi_sm_destroy_enclave(eid: usize) -> isize {
    hprintln!("sbi_sm_destroy_enclave ecall handler");

    if let Err(_err) = enclave::destroy_enclave(eid) {
        return -1;
    }

    0
}

#[no_mangle]
pub extern "C" fn sbi_sm_enter_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    hprintln!("sbi_sm_enter_enclave ecall handler");

    if let Err(_err) = enclave::enter_enclave(regs, eid) {
        return -1;
    }

    0
}

#[no_mangle]
pub extern "C" fn sbi_sm_exit_enclave(regs: &mut TrapFrame, retval: usize) -> isize {
    hprintln!("sbi_sm_exit_enclave ecall handler");

    if let Err(_err) = enclave::exit_enclave(regs, retval) {
        return -1;
    }

    0
}
