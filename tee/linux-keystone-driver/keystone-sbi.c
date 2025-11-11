#include "keystone-sbi.h"

struct sbiret sbi_sm_create_enclave(struct keystone_sbi_create_t* args) {
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
      SBI_SM_CREATE_ENCLAVE,
      (unsigned long) args, 0, 0, 0, 0, 0);
}

struct sbiret sbi_sm_run_enclave(unsigned long eid) {
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
      SBI_SM_RUN_ENCLAVE,
      eid, 0, 0, 0, 0, 0);
}

struct sbiret sbi_sm_destroy_enclave(unsigned long eid) {
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
      SBI_SM_DESTROY_ENCLAVE,
      eid, 0, 0, 0, 0, 0);
}

struct sbiret sbi_sm_resume_enclave(unsigned long eid) {
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
      SBI_SM_RESUME_ENCLAVE,
      eid, 0, 0, 0, 0, 0);
}

struct sbiret sbi_sm_create_shm(unsigned long pa, unsigned long size)
{
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
                   SBI_SM_CREATE_SHM_REGION,
                   pa, size, 0, 0, 0, 0);
}

struct sbiret sbi_sm_map_shm(unsigned long rid)
{
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
                   SBI_SM_MAP_SHM_REGION,
                   rid, 0, 0, 0, 0, 0);
}

struct sbiret sbi_sm_unmap_shm(unsigned long rid)
{
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
                   SBI_SM_UNMAP_SHM_REGION,
                   rid, 0, 0, 0, 0, 0);
}

struct sbiret sbi_sm_change_shm(unsigned long rid, unsigned long perm)
{
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
                   SBI_SM_CHANGE_SHM_REGION,
                   rid, perm, 0, 0, 0, 0);
}

struct sbiret sbi_sm_share_shm(unsigned long rid, unsigned long eid, unsigned long perm)
{
  return sbi_ecall(SBI_EXT_EXPERIMENTAL_KEYSTONE_ENCLAVE,
                   SBI_SM_SHARE_SHM_REGION,
                   rid, eid, perm, 0, 0, 0);
}
