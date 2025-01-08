use crate::trap::TrapFrame;

#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;
#[cfg(feature = "semihosting")]
use semihosting::hprintln;

#[no_mangle]
pub extern "C" fn sbi_sm_create_enclave(base: usize, size: usize, entry: usize) -> isize {
    #[cfg(feature = "semihosting")]
    {
        hprintln!("sbi_sm_create_enclave ecall handler");
        hprintln!("start={:#x}, size={:#x}, entry={:#x}", base, size, entry);
    }

    #[cfg(not(feature = "semihosting"))]
    {
        let format = b"sbi_sm_create_enclave start: base: %#x size: %#x entry: %#x\n\0"
            .as_ptr()
            .cast::<c_char>();
        unsafe {
            sbi_printf(format, base, size, entry);
        }
    }

    panic!("sbi_sm_create_enclave is not implemented yet");
}

#[no_mangle]
pub extern "C" fn sbi_sm_destroy_enclave(eid: usize) -> isize {
    panic!("sbi_sm_destroy_enclave is not implemented yet");
}

#[no_mangle]
pub extern "C" fn sbi_sm_enter_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    panic!("sbi_sm_enter_enclave is not implemented yet");
}

#[no_mangle]
pub extern "C" fn sbi_sm_exit_enclave(regs: &mut TrapFrame, retval: usize) -> isize {
    panic!("sbi_sm_exit_enclave is not implemented yet");
}

#[cfg(not(feature = "semihosting"))]
#[link(name = "sbi_printf")]
extern "C" {
    pub fn sbi_printf(format: *const c_char, ...) -> i32;
}
