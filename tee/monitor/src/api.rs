use crate::dbg;
use crate::enclave;
use crate::trap::TrapFrame;
use crate::Error;

#[no_mangle]
pub extern "C" fn sbi_sm_create_enclave(
    eid: *mut usize,
    create_args: *const enclave::KeystoneSBICreate,
) -> isize {
    dbg!("[create_enclave]");
    let create_args = unsafe { &*create_args };
    match enclave::create_enclave(create_args) {
        Ok(enclave) => {
            unsafe {
                *eid = enclave.id();
            }
            Error::Success as isize
        }
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_destroy_enclave(eid: usize) -> isize {
    dbg!("[destroy_enclave] eid: {:?}", eid);
    match enclave::destroy_enclave(eid) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_enter_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    dbg!("[enter_enclave] eid: {:?}", eid);
    match enclave::enter_enclave(regs, eid) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_resume_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    dbg!("[resume_enclave] eid: {:?}", eid);
    match enclave::resume_enclave(regs) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            if err != Error::Interrupted && err != Error::EdgeCallHost {
                dbg!("Failed {:?}", err);
            }
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_stop_enclave(regs: &mut TrapFrame, request: usize) -> isize {
    dbg!("[stop_enclave] request: {:?}", request);
    match enclave::stop_enclave(regs, request) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            if err != Error::Interrupted && err != Error::EdgeCallHost {
                dbg!("Failed {:?}", err);
            }
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_exit_enclave(regs: &mut TrapFrame) -> isize {
    dbg!("[exit_enclave]");
    match enclave::exit_enclave(regs) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_create_shm_region(
    rid: *mut usize,
    eid: usize,
    paddr: usize,
    size: usize,
) -> isize {
    match enclave::create_shared_mem(eid, paddr, size) {
        Ok(id) => {
            unsafe {
                *rid = id;
            }
            Error::Success as isize
        }
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_change_shm_region(rid: usize, dyn_perm: i8) -> isize {
    match enclave::change_shm_region(rid, dyn_perm.into()) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}

#[no_mangle]
pub extern "C" fn sbi_sm_share_shm_region(rid: usize, eid2share: usize, st_perm: i8) -> isize {
    match enclave::share_shm_region(rid, eid2share, st_perm.into()) {
        Ok(_) => Error::Success as isize,
        Err(err) => {
            dbg!("Failed {:?}", err);
            err.to_isize()
        }
    }
}
