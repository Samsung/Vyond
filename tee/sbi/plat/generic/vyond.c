#include <sbi/sbi_string.h>
#include <sbi/sbi_trap.h>
#include <sbi/sbi_error.h>
#include <sbi/sbi_console.h>
#include <sbi/riscv_asm.h>
#include <sbi/sbi_ecall.h>

#include <vyond.h>
#include <mprv.h>

unsigned long sbi_sm_create_enclave(unsigned long* edi, uintptr_t create_args);
unsigned long sbi_sm_destroy_enclave(unsigned long eid);
unsigned long sbi_sm_enter_enclave(struct sbi_trap_regs *regs, unsigned long eid);
unsigned long sbi_sm_resume_enclave(struct sbi_trap_regs *regs, unsigned long eid);
unsigned long sbi_sm_stop_enclave(struct sbi_trap_regs *regs, unsigned long request);
unsigned long sbi_sm_exit_enclave(struct sbi_trap_regs *regs);

unsigned long copy_enclave_create_args(uintptr_t src, struct keystone_sbi_create_t* dest);

static int sbi_ecall_vyond_monitor_handler(
    unsigned long extid, unsigned long funcid,
    const struct sbi_trap_regs *regs,
    unsigned long *out_val,
    struct sbi_trap_info *out_trap)
{
    uintptr_t retval;

    //sbi_printf("SBI ECALL funcid: %ld\n", funcid);
  
    if (funcid <= FID_RANGE_DEPRECATED) {
        return SBI_ERR_SM_DEPRECATED;
    }

    switch (funcid) {
    case SBI_SM_CREATE_ENCLAVE:
        struct keystone_sbi_create_t create_args_local;
        retval = copy_enclave_create_args((uintptr_t)regs->a0, &create_args_local);
        if (!retval) {
            retval = sbi_sm_create_enclave(out_val, (uintptr_t)&create_args_local);
        }
        break;
    case SBI_SM_DESTROY_ENCLAVE:
        retval = sbi_sm_destroy_enclave(regs->a0);
        break;
    case SBI_SM_ENTER_ENCLAVE:
        retval = sbi_sm_enter_enclave((struct sbi_trap_regs*) regs, regs->a0);
        ((struct sbi_trap_regs *)regs)->mepc += 4;
        sbi_trap_exit(regs);
        break;
    case SBI_SM_RESUME_ENCLAVE:
        retval = sbi_sm_resume_enclave((struct sbi_trap_regs*) regs, regs->a0);
        if (!regs->zero) {
          ((struct sbi_trap_regs *)regs)->a0 = retval;
        }
        ((struct sbi_trap_regs *)regs)->mepc += 4;
        sbi_trap_exit(regs);
        break;
    case SBI_SM_RANDOM:
        static uint64_t w = 0, s = 0xb5ad4eceda1ce2a9;
        unsigned long cycles;
        asm volatile ("rdcycle %0" : "=r" (cycles));

        // from Middle Square Weyl Sequence algorithm
        uint64_t x = cycles;
        x *= x;
        x += (w += s);
        *out_val = (x>>32) | (x<<32);
        retval = 0;
        break;
    case SBI_SM_STOP_ENCLAVE:
        retval = sbi_sm_stop_enclave((struct sbi_trap_regs*) regs, regs->a0);
        ((struct sbi_trap_regs *)regs)->a0 = retval;
        ((struct sbi_trap_regs *)regs)->mepc += 4;
        sbi_trap_exit(regs);
        break;
    case SBI_SM_EXIT_ENCLAVE:
        unsigned long prev_reg_a0 = regs->a0;
        retval = sbi_sm_exit_enclave((struct sbi_trap_regs*) regs);
        ((struct sbi_trap_regs *)regs)->a0 = retval;
        ((struct sbi_trap_regs *)regs)->a1 = prev_reg_a0;
        ((struct sbi_trap_regs *)regs)->mepc += 4;
        sbi_trap_exit(regs);
        break;
    default:
        retval = SBI_ERR_SM_NOT_IMPLEMENTED;
        break;
    }
  
    //sbi_printf("Retval = %lx\n", retval);

    return retval;
}
  
//extern struct sbi_ecall_extension ecall_vyond_monitor;
#define SBI_EXT_EXPERIMENTAL_VYOND_MONITOR 0x08424b45 // BKE (Berkeley Keystone Enclave)
  
struct sbi_ecall_extension ecall_vyond_monitor = {
    .extid_start = SBI_EXT_EXPERIMENTAL_VYOND_MONITOR,
    .extid_end = SBI_EXT_EXPERIMENTAL_VYOND_MONITOR,
    .handle = sbi_ecall_vyond_monitor_handler,
};
  
int sm_init(int cold_boot);

int vyond_monitor_init(int cold_boot)
{
    sbi_printf("Initializing vyond monitor\n");

    int ret = sm_init(cold_boot);
    if (ret != 0) {
        return ret;
    }
    sbi_printf("Finished Initializing vyond monitor\n");


    sbi_ecall_register_extension(&ecall_vyond_monitor);

    return ret;
}

// TODO: This function is externally used by sm-sbi.c.
// Change it to be internal (remove from the enclave.h and make static)
/* Internal function enforcing a copy source is from the untrusted world.
 * Does NOT do verification of dest, assumes caller knows what that is.
 * Dest should be inside the SM memory.
 */
unsigned long copy_enclave_create_args(uintptr_t src, struct keystone_sbi_create_t* dest){

  int region_overlap = copy_to_sm(dest, src, sizeof(struct keystone_sbi_create_t));

  if (region_overlap)
    return SBI_ERR_SM_ENCLAVE_REGION_OVERLAPS;
  else
    return SBI_ERR_SM_ENCLAVE_SUCCESS;
}
