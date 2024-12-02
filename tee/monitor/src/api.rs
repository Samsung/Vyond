
use crate::enclave;
use crate::trap::TrapFrame;

#[cfg(feature = "semihosting")] 
use semihosting::hprintln;
#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;

#[no_mangle]
pub extern "C" fn sbi_sm_create_enclave(base: usize, size: usize, entry: usize) -> isize {
    #[cfg(feature = "semihosting")] {
        hprintln!("sbi_sm_create_enclave ecall handler");
        hprintln!("start={:#x}, size={:#x}, entry={:#x}", base, size, entry);
    }

    #[cfg(not(feature = "semihosting"))] {
        let format = b"sbi_sm_create_enclave start: base: %#x size: %#x entry: %#x\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format, base, size, entry); }
    }

    if let Ok(enclave) = enclave::create_enclave(base, size, entry) {
        #[cfg(not(feature = "semihosting"))] {
            let format = b"sbi_sm_create_enclave success retval: %d\n\0".as_ptr().cast::<c_char>();
            unsafe { sbi_printf(format, enclave.id() as isize); }
        }
        return enclave.id() as isize;
    }

    #[cfg(not(feature = "semihosting"))] {
        let format = b"sbi_sm_create_enclave failed\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }
    0
}

#[no_mangle]
pub extern "C" fn sbi_sm_destroy_enclave(eid: usize) -> isize {
    #[cfg(feature = "semihosting")] {
        hprintln!("sbi_sm_destroy_enclave ecall handler");
    }
    #[cfg(not(feature = "semihosting"))] {
        let format = b"sbi_sm_destroy_enclave ecall handler\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }

    if let Err(_err) = enclave::destroy_enclave(eid) {
        return -1;
    }

    0
}

#[no_mangle]
pub extern "C" fn sbi_sm_enter_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    #[cfg(feature = "semihosting")] {
        hprintln!("sbi_sm_enter_enclave ecall handler");
    }
    #[cfg(not(feature = "semihosting"))] {
        let format = b"sbi_sm_enter_enclave ecall handler\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }

    if let Err(_err) = enclave::enter_enclave(regs, eid) {
        return -1;
    }

    0
}

#[no_mangle]
pub extern "C" fn sbi_sm_exit_enclave(regs: &mut TrapFrame, retval: usize) -> isize {
    #[cfg(feature = "semihosting")] {
        hprintln!("sbi_sm_exit_enclave ecall handler");
    }
    #[cfg(not(feature = "semihosting"))] {
        let format = b"sbi_sm_exit_enclave ecall handler\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }

    if let Err(_err) = enclave::exit_enclave(regs, retval) {
        return -1;
    }

    0
}

#[cfg(not(feature = "semihosting"))]
    #[link(name = "sbi_printf")]
    extern "C" {
    pub fn sbi_printf(format: *const c_char, ...) -> i32;
}
