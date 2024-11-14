#include <sbi/sbi_string.h>
#include <sbi/sbi_trap.h>
#include <sbi/sbi_error.h>
#include <sbi/sbi_console.h>
#include <sbi/riscv_asm.h>
#include <sbi/sbi_ecall.h>

#include <vyond.h>

unsigned long sbi_sm_create_enclave(unsigned long base, unsigned long size, unsigned long entry);
unsigned long sbi_sm_destroy_enclave(unsigned long eid);
unsigned long sbi_sm_enter_enclave(struct sbi_trap_regs *regs, unsigned long eid);
unsigned long sbi_sm_exit_enclave(struct sbi_trap_regs *regs, unsigned long retval);

static int sbi_ecall_vyond_monitor_handler(
    unsigned long extid, unsigned long funcid,
    const struct sbi_trap_regs *regs,
    unsigned long *out_val,
    struct sbi_trap_info *out_trap)
{
    uintptr_t retval;

    sbi_printf("SBI ECALL\n");
  
    if (funcid <= FID_RANGE_DEPRECATED) {
        return SBI_ERR_SM_DEPRECATED;
    }

    switch (funcid) {
    case SBI_SM_CREATE_ENCLAVE:
        retval = sbi_sm_create_enclave(regs->a0, regs->a1, regs->a2);
        break;
    case SBI_SM_DESTROY_ENCLAVE:
        retval = sbi_sm_destroy_enclave(regs->a0);
        break;
    case SBI_SM_ENTER_ENCLAVE:
        retval = sbi_sm_enter_enclave((struct sbi_trap_regs*) regs, regs->a0);
        ((struct sbi_trap_regs *)regs)->mepc += 4;
        sbi_trap_exit(regs);
        break;
    case SBI_SM_EXIT_ENCLAVE:
        retval = sbi_sm_exit_enclave((struct sbi_trap_regs*) regs, regs->a0);
        ((struct sbi_trap_regs *)regs)->mepc += 4;
        sbi_trap_exit(regs);
        break;
    default:
        retval = SBI_ERR_SM_NOT_IMPLEMENTED;
        break;
    }
  
    sbi_printf("Retval = %lx\n", retval);

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

    sbi_ecall_register_extension(&ecall_vyond_monitor);

    return ret;
}
