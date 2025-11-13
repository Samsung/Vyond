use crate::cpu;
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
    let ret = match enclave::create_enclave(create_args) {
        Ok(enclave) => {
            unsafe {
                *eid = enclave.id();
            }
            Error::Success
        }
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_destroy_enclave(eid: usize) -> isize {
    dbg!("[destroy_enclave] eid: {:?}", eid);
    let ret = match enclave::destroy_enclave(eid) {
        Ok(_) => Error::Success,
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_enter_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    dbg!("[enter_enclave] eid: {:?}", eid);
    let ret = match enclave::enter_enclave(regs, eid) {
        Ok(_) => Error::Success,
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_resume_enclave(regs: &mut TrapFrame, eid: usize) -> isize {
    dbg!("[resume_enclave] eid: {:?}", eid);
    let ret = match enclave::resume_enclave(regs, eid) {
        Ok(_) => Error::Success,
        Err(err) => {
            if err != Error::Interrupted && err != Error::EdgeCallHost {
                dbg!("Failed {:?}", err);
                panic!("Failed {:?}", err);
            } else {
                err
            }
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_stop_enclave(regs: &mut TrapFrame, request: usize) -> isize {
    dbg!(
        "[stop_enclave] eid {:?} request: {:?}",
        cpu::get_enclave_id(),
        request
    );
    let ret = match enclave::stop_enclave(regs, request) {
        Ok(_) => Error::Success,
        Err(err) => {
            if err != Error::Interrupted && err != Error::EdgeCallHost {
                dbg!("Failed {:?}", err);
                panic!("Failed {:?}", err);
            } else {
                err
            }
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_exit_enclave(regs: &mut TrapFrame) -> isize {
    dbg!("[exit_enclave] eid {:?}", cpu::get_enclave_id());
    let ret = match enclave::exit_enclave(regs) {
        Ok(_) => Error::Success,
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_create_shm_region(rid: *mut usize, pa: usize, size: usize) -> isize {
    let ret = match enclave::create_shared_mem(pa, size) {
        Ok(id) => {
            unsafe {
                *rid = id;
            }
            Error::Success
        }
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    unsafe {
        dbg!(
            "[create_shm_region paddr {:x} size {:?} returning rid {:?}",
            pa,
            size,
            *rid
        );
    }
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_map_shm_region(regs: &mut TrapFrame, rid: usize) -> isize {
    let ret = match enclave::map_shm_region(regs, rid) {
        Ok(_) => Error::Success,
        Err(err) => {
            unsafe {
                dbg!(
                    "[map_shm_region] rid {:?} paddr {:x} size {:?} ",
                    rid,
                    regs.a2,
                    regs.a3
                );
            }
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    unsafe {
        dbg!(
            "[map_shm_region] rid {:?} paddr {:x} size {:?} ",
            rid,
            regs.a2,
            regs.a3
        );
    }
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_unmap_shm_region(rid: usize) -> isize {
    let ret = match enclave::unmap_shm_region(rid) {
        Ok(_) => Error::Success,
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    unsafe {
        dbg!("[unmap_shm_region] rid {:?}", rid);
    }
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_change_shm_region(rid: usize, dyn_perm: i8) -> isize {
    dbg!("[change_shm_region] rid {:?} perm: {:?}", rid, dyn_perm);
    let ret = match enclave::change_shm_region(rid, dyn_perm.into()) {
        Ok(_) => Error::Success,
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    ret as isize
}

#[no_mangle]
pub extern "C" fn sbi_sm_share_shm_region(rid: usize, eid2share: usize, st_perm: i8) -> isize {
    dbg!(
        "[share_shm_region] rid {:?} eid {:?} perm {:?}",
        rid,
        eid2share,
        st_perm
    );
    let ret = match enclave::share_shm_region(rid, eid2share, st_perm.into()) {
        Ok(_) => Error::Success,
        Err(err) => {
            dbg!("Failed {:?}", err);
            panic!("Failed {:?}", err);
        }
    };
    ret as isize
}
