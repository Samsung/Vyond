use crate::cpu;
use crate::encoding::*;
use crate::isolator;
use crate::os_region_id;
use crate::pmp;
use crate::spinlock::SpinLock;
use crate::thread;
use crate::trap::TrapFrame;
use crate::Error;

#[derive(PartialEq, Clone, Copy)]
pub enum State {
    Stopped,
    Running,
    Destroying,
}

#[derive(PartialEq, Clone, Copy)]
pub enum RegionType {
    RegionInvalid,
    RegionEPM,
    RegionUTM,
    RegionOther,
}

pub struct Region {
    id: usize,
    r_type: RegionType,
}

/* TODO: does not support multithreaded enclave yet */
const MAX_ENCLAVE_THREADS: usize = 1;
const MAX_ENCLAVE_REGIONS: usize = 8;

pub struct RunState {
    count: usize,
    state: State,
}

#[repr(C)]
pub struct RuntimeVAParams {
    pub runtime_entry: usize,
    pub user_entry: usize,
    pub untrusted_ptr: usize,
    pub untrusted_size: usize,
    pub num_eapp_pages: usize,
}

#[repr(C)]
pub struct RuntimePAParams {
    dram_base: usize,
    dram_size: usize,
    runtime_base: usize,
    user_base: usize,
    free_base: usize,
    untrusted_base: usize,
    untrusted_size: usize,
    free_requested: usize,
}

#[repr(C)]
pub struct KeystoneSBIPReigion {
    pub paddr: usize,
    pub size: usize,
}

#[repr(C)]
pub struct KeystoneSBICreate {
    pub epm_region: KeystoneSBIPReigion,
    pub utm_region: KeystoneSBIPReigion,

    pub runtime_paddr: usize,
    pub user_paddr: usize,
    pub free_paddr: usize,
    pub free_requested: usize,
}

// enclave metadata
pub struct Enclave {
    eid: usize,                // enclave id
    state: SpinLock<RunState>, // global state of the enclave

    // Physical memory regions associate with this enclave
    regions: [Option<Region>; MAX_ENCLAVE_REGIONS],

    // enclave execution context
    threads: [Option<thread::State>; MAX_ENCLAVE_THREADS],

    pa_params: RuntimePAParams,
}

impl Enclave {
    const REGION_INIT: Option<Region> = None;
    const THREAD_INIT: Option<thread::State> = None;

    pub fn allocate<'a>(pa_params: RuntimePAParams) -> Result<&'a mut Enclave, Error> {
        for eid in 0..MAX_ENCLAVES {
            if unsafe { ENCLAVES[eid].is_none() } {
                unsafe { ENCLAVES[eid] = Some(Enclave::new(eid, pa_params)) };
                return Ok(unsafe { ENCLAVES[eid].as_mut().unwrap() });
            }
        }

        Err(Error::NoFreeResource)
    }

    pub fn new(eid: usize, pa_params: RuntimePAParams) -> Self {
        Enclave {
            eid,
            regions: [Self::REGION_INIT; MAX_ENCLAVE_REGIONS],
            state: SpinLock::new(RunState {
                count: 0,
                state: State::Stopped,
            }),
            threads: [Self::THREAD_INIT; MAX_ENCLAVE_THREADS],
            pa_params,
        }
    }

    pub fn id(&self) -> usize {
        self.eid
    }

    pub fn free(eid: usize) -> Result<(), Error> {
        unsafe { ENCLAVES[eid] = None };
        Ok(())
    }

    pub fn switch_to_enclave(&mut self, regs: &mut TrapFrame, load_parameters: bool) {
        /* save host context */
        let thread = &mut self.threads[0].as_mut().unwrap();

        thread.swap_prev_state(regs);
        thread.swap_prev_mepc(regs, regs.mepc);
        thread.swap_prev_mstatus(regs, regs.mstatus);

        let interrupts = 0;
        csr_write!(mideleg, interrupts);

        if load_parameters {
            //csr_write!(sepc, self.params.user_entry);
            regs.mepc = self.pa_params.dram_base - 4; // regs->mepc will be +4 before sbi_ecall_handler return
            regs.mstatus = 1 << crate::encoding::MSTATUS_MPP_SHIFT;
            regs.a1 = self.pa_params.dram_base; // $a1: (PA) DRAM base,
            regs.a2 = self.pa_params.dram_size; // $a2: (PA) DRAM size,
            regs.a3 = self.pa_params.runtime_base; // $a3: (PA) kernel location,
            regs.a4 = self.pa_params.user_base; // $a4: (PA) user location,
            regs.a5 = self.pa_params.free_base; // $a5: (PA) freemem location,
            regs.a6 = self.pa_params.untrusted_base; // $a6: (VA) utm base,
            regs.a7 = self.pa_params.untrusted_size; // $a7: (size_t) utm size

            csr_write!(satp, 0);
        }

        switch_vector_enclave();

        #[cfg(feature = "usepmp")]
        let _ = pmp::set_keystone(os_region_id(), pmp::PMP_NO_PERM);
        (0..MAX_ENCLAVE_REGIONS).for_each(|memid| {
            if let Some(ref region) = self.regions[memid] {
                let _ = isolator::set_isolator(region.id);
            }
        });

        // Setup any platform specific defenses
        cpu::enter_enclave_context(self.eid);
    }

    pub fn switch_to_host(&mut self, regs: &mut TrapFrame) {
        // set PMP
        (0..MAX_ENCLAVE_REGIONS).for_each(|memid| {
            if let Some(region) = self.regions[memid].as_ref() {
                let _ = isolator::reset_isolator(region.id);
            }
        });
        let _ = isolator::set_isolator(os_region_id());

        let interrupts = MIP_SSIP | MIP_STIP | MIP_SEIP;
        csr_write!(mideleg, interrupts);

        let thread = &mut self.threads[0].as_mut().unwrap();

        /* restore host context */
        thread.swap_prev_state(regs);
        thread.swap_prev_mepc(regs, regs.mepc);
        thread.swap_prev_mstatus(regs, regs.mstatus);

        switch_vector_host();

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

const MAX_ENCLAVES: usize = 8; // FIXME: should be associated with NWORLDS

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
pub fn create_enclave<'a>(create_args: &KeystoneSBICreate) -> Result<&'a Enclave, Error> {
    let pa_params = RuntimePAParams {
        dram_base: create_args.epm_region.paddr,
        dram_size: create_args.epm_region.size,
        runtime_base: create_args.runtime_paddr,
        user_base: create_args.user_paddr,
        free_base: create_args.free_paddr,
        untrusted_base: create_args.utm_region.paddr,
        untrusted_size: create_args.utm_region.size,
        free_requested: create_args.free_requested,
    };
    let enclave: &mut Enclave = Enclave::allocate(pa_params)?;

    // TODO: Check if create_args is valid

    // create a PMP/WG region bound to the enclave
    match isolator::region_init(
        create_args.epm_region.paddr,
        create_args.epm_region.size,
        enclave.id(),
        false,
    ) {
        Ok(region) => {
            enclave.regions[0] = Some(Region {
                id: region,
                r_type: RegionType::RegionEPM,
            });
            enclave.threads[0] = Some(thread::State::new(
                create_args.epm_region.paddr - 4,
                (1 << crate::encoding::MSTATUS_MPP_SHIFT) | crate::encoding::MSTATUS_FS,
            ));
        }
        Err(e) => return Err(e),
    };

    match isolator::region_init(
        create_args.utm_region.paddr,
        create_args.utm_region.size,
        enclave.id(),
        true,
    ) {
        Ok(shared_region) => {
            enclave.regions[1] = Some(Region {
                id: shared_region,
                r_type: RegionType::RegionUTM,
            });
        }
        Err(e) => return Err(e),
    };

    Ok(enclave)
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
                if region.r_type == RegionType::RegionInvalid
                    || region.r_type == RegionType::RegionUTM
                {
                    continue;
                }
                //1.a Clear all pages
                let rid = region.id;

                //1.b free pmp region
                let _ = isolator::region_free(rid);
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

        enclave.switch_to_enclave(tf, true);

        return Ok(());
    }

    Err(Error::Invalid)
}

pub fn resume_enclave(tf: &mut TrapFrame) -> Result<(), Error> {
    if let Some(enclave) = find_enclave(cpu::get_enclave_id()) {
        let mut runstate = enclave.state.lock();
        let resumable = (runstate.state == State::Running || runstate.state == State::Stopped)
            && runstate.count < MAX_ENCLAVE_THREADS;

        if resumable {
            runstate.count += 1;
            runstate.state = State::Running;
        }

        drop(runstate);

        if !resumable {
            return Err(Error::NotResumable);
        }

        enclave.switch_to_enclave(tf, false);

        return Ok(());
    }

    return Err(Error::InvalidId);
}

pub fn stop_enclave(tf: &mut TrapFrame, request: usize) -> Result<(), Error> {
    if let Some(enclave) = find_enclave(cpu::get_enclave_id()) {
        let mut runstate = enclave.state.lock();
        let runnable = runstate.state == State::Running;

        if runnable {
            runstate.count -= 1;
            if runstate.count == 0 {
                runstate.state = State::Stopped;
            }
        }

        drop(runstate);

        if !runnable {
            return Err(Error::NotRunning);
        }

        enclave.switch_to_host(tf);

        let ret = match request {
            0/*StopReason::TimerInterrupt*/ => Err(Error::Interrupted),
            1/*StopReason::EdgeCallHost*/ => Err(Error::EdgeCallHost),
            _ => Err(Error::Unknown),
        };
        return ret;
    }

    return Err(Error::Invalid);
}

pub fn exit_enclave(tf: &mut TrapFrame) -> Result<(), Error> {
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

fn switch_vector_enclave() {
    csr_write!(mtvec, trap_vector_enclave);
}

fn switch_vector_host() {
    csr_write!(mtvec, _trap_handler);
}

extern "C" {
    fn trap_vector_enclave();
}
extern "C" {
    fn _trap_handler();
}
