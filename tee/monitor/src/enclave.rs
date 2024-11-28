use crate::cpu;
use crate::encoding::*;
use crate::pmp;
use crate::spinlock::SpinLock;
use crate::thread;
use crate::trap::TrapFrame;
use crate::Error;

#[cfg(feature = "semihosting")] 
use semihosting::{heprintln, hprintln};
#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;
#[cfg(not(feature = "semihosting"))]
use crate::api::sbi_printf;


#[derive(PartialEq, Clone, Copy)]
pub enum State {
    Stopped,
    Running,
    Destroying,
}

pub struct Region {
    id: usize,
}

/* TODO: does not support multithreaded enclave yet */
const MAX_ENCLAVE_THREADS: usize = 1;
const MAX_ENCLAVE_REGIONS: usize = 8;

pub struct RunState {
    count: usize,
    state: State,
}

// enclave metadata
pub struct Enclave {
    eid: usize,                // enclave id
    state: SpinLock<RunState>, // global state of the enclave

    // Physical memory regions associate with this enclave
    regions: [Option<Region>; MAX_ENCLAVE_REGIONS],

    // enclave execution context
    threads: [Option<thread::State>; MAX_ENCLAVE_THREADS],
}

impl Enclave {
    const REGION_INIT: Option<Region> = None;
    const THREAD_INIT: Option<thread::State> = None;

    pub fn allocate<'a>() -> Result<&'a mut Enclave, Error> {
        for eid in 0..MAX_ENCLAVES {
            if unsafe { ENCLAVES[eid].is_none() } {
                #[cfg(feature = "semihosting")] {
                    hprintln!("Found enclave: {}", eid);
                }
                unsafe { ENCLAVES[eid] = Some(Enclave::new(eid)) };
                return Ok(unsafe { ENCLAVES[eid].as_mut().unwrap() });
            }
        }

        Err(Error::NoFreeResource)
    }

    pub fn new(eid: usize) -> Self {
        Enclave {
            eid: eid,
            regions: [Self::REGION_INIT; MAX_ENCLAVE_REGIONS],
            state: SpinLock::new(RunState {
                count: 0,
                state: State::Stopped,
            }),
            threads: [Self::THREAD_INIT; MAX_ENCLAVE_THREADS],
        }
    }

    pub fn id(&self) -> usize {
        self.eid
    }

    pub fn free(eid: usize) -> Result<(), Error> {
        unsafe { ENCLAVES[eid] = None };
        Ok(())
    }

    pub fn switch_to_enclave(&mut self, regs: &mut TrapFrame) {
        let hartid = csr_read!(mhartid) as usize;

        /* save host context */
        let thread = &mut self.threads[hartid].as_mut().unwrap();

        thread.swap_prev_state(regs);
        thread.swap_prev_mepc(regs, regs.mepc);
        thread.swap_prev_mstatus(regs, regs.mstatus);

        #[cfg(feature = "semihosting")] {
            hprintln!(
                "to-enclave: mepc: {:#x}, mhstatus: {:#x}",
                regs.mepc,
                regs.mstatus
            );
        }

        let interrupts = 0;
        csr_write!(mideleg, interrupts);

        //switch_vector_enclave();

        // NOTICE: Temporarily commented out for testing using payload.
        // set PMP
        //let _ = crate::osm_pmp_set(pmp::PMP_NO_PERM);
        //(0..MAX_ENCLAVE_REGIONS).for_each(|memid| {
        //    if let Some(ref region) = self.regions[memid] {
        //        let _ = pmp::set_keystone(region.id, pmp::PMP_ALL_PERM);
        //    }
        //});

        // Setup any platform specific defenses
        cpu::enter_enclave_context(self.eid);
    }

    pub fn switch_to_host(&mut self, regs: &mut TrapFrame) {
        // set PMP
        //(0..MAX_ENCLAVE_REGIONS).for_each(|memid| {
        //    if let Some(region) = self.regions[memid].as_ref() {
        //        let _ = pmp::set_keystone(region.pmp_rid, pmp::PMP_NO_PERM);
        //    }
        //});

        let interrupts = MIP_SSIP | MIP_STIP | MIP_SEIP;
        csr_write!(mideleg, interrupts);

        let thread = &mut self.threads[0].as_mut().unwrap();

        /* restore host context */
        thread.swap_prev_state(regs);
        thread.swap_prev_mepc(regs, regs.mepc);
        thread.swap_prev_mstatus(regs, regs.mstatus);

        #[cfg(feature = "semihosting")] {
            hprintln!(
                "to-host: mepc: {:#x}, mhstatus: {:#x}",
                regs.mepc,
                regs.mstatus
            );
        }

        //switch_vector_host();

        let pending = csr_read!(mip);

        if (pending & MIP_MTIP) != 0 {
            csr_clear!(mip, MIP_MTIP);
            csr_set!(mip, MIP_STIP);
        }
        if (pending & MIP_MSIP) != 0 {
            csr_clear!(mip, MIP_MSIP);
            csr_set!(mip, MIP_SSIP);
        }
        if (pending & MIP_MEIP) != 0 {
            csr_clear!(mip, MIP_MEIP);
            csr_set!(mip, MIP_SEIP);
        }

        cpu::exit_enclave_context();
    }
}

const MAX_ENCLAVES: usize = 8;

const INIT_VALUE: Option<Enclave> = None;
static mut ENCLAVES: [Option<Enclave>; MAX_ENCLAVES] = [INIT_VALUE; MAX_ENCLAVES];

pub fn enclave_exists(enclaves: &[Option<Enclave>], eid: usize) -> bool {
    (eid < enclaves.len()) && enclaves[eid].is_some()
}

/* This handles creation of a new enclave, based on arguments provided
 * by the untrusted host.
 *
 * This may fail if: it cannot allocate PMP regions, EIDs, etc
 */
pub fn create_enclave<'a>(base: usize, size: usize, entry: usize) -> Result<&'a Enclave, Error> {
    let enclave: &mut Enclave = Enclave::allocate()?;
    
    #[cfg(not(feature = "semihosting"))] {
        let format = b"Entered create_enclave\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }

    // create a PMP region bound to the enclave
    if let Ok(region) = pmp::pmp_region_init(base, size, pmp::Priority::Any, false) {
        #[cfg(feature = "semihosting")] {
            hprintln!("Found unused pmp slot: {}", region);
        }
        #[cfg(not(feature = "semihosting"))] {
            let format = b"Found unused pmp slot\n\0".as_ptr().cast::<c_char>();
            unsafe { sbi_printf(format); }
        }
        enclave.regions[0] = Some(Region { id: region });

        enclave.threads[0] = Some(thread::State::new(
            entry - 4,
            (1 << crate::encoding::MSTATUS_MPP_SHIFT) | crate::encoding::MSTATUS_FS,
        ));

        return Ok(enclave);
    }

    #[cfg(feature = "semihosting")] {
        heprintln!("No pmp slot found");
    }
    #[cfg(not(feature = "semihosting"))] {
        let format = b"No pmp slot found\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }

    Err(Error::NoFreeResource)
}

pub fn find_enclave<'a>(eid: usize) -> Option<&'a mut Enclave> {
    if let Some(enclave) = unsafe { ENCLAVES[eid].as_mut() } {
        return Some(enclave);
    }

    None
}

/*
* Fully destroys an enclave
* Deallocates EID, clears epm, etc
* Fails only if the enclave isn't running.
*/
pub fn destroy_enclave(eid: usize) -> Result<(), Error> {
    if let Some(enclave) = find_enclave(eid) {
        let mut runstate = enclave.state.lock();
        let destroyable = runstate.state != State::Running && runstate.count == 0;

        /* update the enclave state first so that
         * no SM can run the enclave any longer */
        if destroyable {
            runstate.state = State::Destroying;
        }

        drop(runstate);

        if !destroyable {
            return Err(Error::NotDestroyable);
        }

        // 1. clear all the data in the enclave pages
        // requires no lock (single runner)
        for i in 0..MAX_ENCLAVE_REGIONS {
            if let Some(region) = &enclave.regions[i] {
                //1.a Clear all pages
                let rid = region.id;

                //1.b free pmp region
                let _ = pmp::pmp_region_free(rid);
            }
        }

        (0..MAX_ENCLAVE_REGIONS).for_each(|idx| {
            enclave.regions[idx] = None;
        });

        // 2. release eid
        let _ = Enclave::free(eid);

        return Ok(());
    }

    Err(Error::InvalidId)
}

pub fn enter_enclave(tf: &mut TrapFrame, eid: usize) -> Result<(), Error> {
    if let Some(enclave) = find_enclave(eid) {
        let mut runstate = enclave.state.lock();
        let runnable = runstate.state == State::Running || runstate.state == State::Stopped;

        if runnable {
            runstate.count += 1;
            runstate.state = State::Running;
        }

        drop(runstate);

        if !runnable {
            return Err(Error::NotRunnable);
        }

        enclave.switch_to_enclave(tf);

        return Ok(());
    }

    Err(Error::Invalid)
}

pub fn exit_enclave(tf: &mut TrapFrame, retval: usize) -> Result<(), Error> {
    if let Some(enclave) = find_enclave(cpu::get_enclave_id()) {
        let mut runstate = enclave.state.lock();
        let runnable = runstate.state == State::Running && runstate.count > 0;

        if runnable {
            runstate.count -= 1;
        }

        if runstate.count == 0 {
            runstate.state = State::Stopped;
        }

        drop(runstate);

        enclave.switch_to_host(tf);

        return Ok(());
    }

    Err(Error::Invalid)
}
