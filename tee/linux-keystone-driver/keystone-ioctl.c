//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#include "keystone.h"
#include "keystone-sbi.h"
#include "keystone_user.h"
#include <asm/sbi.h>
#include <linux/uaccess.h>
#include <linux/string.h>

#define read_reg(reg)                                     \
  ({                                                      \
    unsigned long __v;                                    \
    __asm__ __volatile__("mv %0, " #reg : "=r"(__v) : :); \
    __v;                                                  \
  })

int __keystone_destroy_enclave(unsigned int ueid);

int keystone_create_enclave(struct file *filep, unsigned long arg)
{
  keystone_err("[DRV] create_enclave\n");
  /* create parameters */
  struct keystone_ioctl_create_enclave *enclp = (struct keystone_ioctl_create_enclave *) arg;

  struct enclave *enclave;
  enclave = create_enclave(enclp->min_pages);

  if (enclave == NULL) {
    return -ENOMEM;
  }

  /* Pass base page table */
  enclp->epm_paddr = enclave->epm->pa;
  enclp->epm_size = enclave->epm->size;

  /* allocate UID */
  enclp->eid = enclave_idr_alloc(enclave);

  filep->private_data = (void *) enclp->eid;

  return 0;
}


int keystone_finalize_enclave(unsigned long arg)
{
  keystone_err("[DRV] finalize_enclave\n");
  struct sbiret ret;
  struct enclave *enclave;
  struct utm *utm;
  struct keystone_sbi_create_t create_args;

  struct keystone_ioctl_create_enclave *enclp = (struct keystone_ioctl_create_enclave *) arg;

  enclave = get_enclave_by_id(enclp->eid);
  if(!enclave) {
    keystone_err("invalid enclave id\n");
    return -EINVAL;
  }

  enclave->is_init = false;

  /* SBI Call */
  create_args.epm_region.paddr = enclave->epm->pa;
  create_args.epm_region.size = enclave->epm->size;

  utm = enclave->utm;

  if (utm) {
    create_args.utm_region.paddr = __pa(utm->ptr);
    create_args.utm_region.size = utm->size;
  } else {
    create_args.utm_region.paddr = 0;
    create_args.utm_region.size = 0;
  }

  // physical addresses for runtime, user, and freemem
  create_args.runtime_paddr = enclp->runtime_paddr;
  create_args.user_paddr = enclp->user_paddr;
  create_args.free_paddr = enclp->free_paddr;
  create_args.free_requested = enclp->free_requested;

  ret = sbi_sm_create_enclave(&create_args);

  if (ret.error) {
    keystone_err("keystone_create_enclave: SBI call failed with error code %ld\n", ret.error);
    goto error_destroy_enclave;
  }

  enclave->eid = ret.value;

  return 0;

error_destroy_enclave:
  /* This can handle partial initialization failure */
  destroy_enclave(enclave);

  return -EINVAL;

}

int keystone_run_enclave(unsigned long data)
{
  keystone_err("[DRV] run_enclave\n");
  struct sbiret ret;
  unsigned long ueid;
  struct enclave* enclave;
  struct keystone_ioctl_run_enclave *arg = (struct keystone_ioctl_run_enclave*) data;

  ueid = arg->eid;
  enclave = get_enclave_by_id(ueid);

  if (!enclave) {
    keystone_err("invalid enclave id\n");
    return -EINVAL;
  }

  if (enclave->eid < 0) {
    keystone_err("real enclave does not exist\n");
    return -EINVAL;
  }

  ret = sbi_sm_run_enclave(enclave->eid);

  arg->error = ret.error;
  arg->value = ret.value;

  return 0;
}

int utm_init_ioctl(struct file *filp, unsigned long arg)
{
  int ret = 0;
  struct utm *utm;
  struct enclave *enclave;
  struct keystone_ioctl_create_enclave *enclp = (struct keystone_ioctl_create_enclave *) arg;
  long long unsigned untrusted_size = enclp->utm_size;

  enclave = get_enclave_by_id(enclp->eid);

  if(!enclave) {
    keystone_err("invalid enclave id\n");
    return -EINVAL;
  }

  utm = kmalloc(sizeof(struct utm), GFP_KERNEL);
  if (!utm) {
    ret = -ENOMEM;
    return ret;
  }

  ret = utm_init(utm, untrusted_size);

  /* prepare for mmap */
  enclave->utm = utm;
  enclave->epm_mapped = true;

  enclp->utm_paddr = __pa(utm->ptr);

  return ret;
}


int keystone_destroy_enclave(struct file *filep, unsigned long arg)
{
  keystone_err("[DRV] destroy_enclave\n");
  int ret;
  struct keystone_ioctl_create_enclave *enclp = (struct keystone_ioctl_create_enclave *) arg;
  unsigned long ueid = enclp->eid;

  ret = __keystone_destroy_enclave(ueid);
  if (!ret) {
    filep->private_data = NULL;
  }
  return ret;
}

struct enclave_shm
{
  uintptr_t pa;
  uintptr_t size;
};

struct enclave_shm_list
{
  unsigned int shm_count;
  struct enclave_shm shms[16];
};

int __keystone_destroy_enclave(unsigned int ueid)
{
  keystone_err("[DRV] __destroy_enclave\n");
  struct sbiret ret;
  struct enclave *enclave;
  enclave = get_enclave_by_id(ueid);

  if (!enclave) {
    keystone_err("invalid enclave id\n");
    return -EINVAL;
  }

  if (enclave->eid >= 0) {
    ret = sbi_sm_destroy_enclave(enclave->eid);
    if (ret.error) {
      keystone_err("fatal: cannot destroy enclave: SBI failed with error code %ld\n", ret.error);
      return -EINVAL;
    }
  } else {
    keystone_warn("keystone_destroy_enclave: skipping (enclave does not exist)\n");
  }

  // for (int i = 0; i < enclave_shm_list.shm_count; i++)
  //{
  //   destroy_shm_by_pa(enclave_shm_list.shm[i].pa);
  // }

  destroy_enclave(enclave);
  enclave_idr_remove(ueid);

  return 0;
}

int keystone_resume_enclave(unsigned long data)
{
  keystone_err("[DRV] resume_enclave --\n");
  struct sbiret ret;
  struct keystone_ioctl_run_enclave *arg = (struct keystone_ioctl_run_enclave*) data;
  unsigned long ueid = arg->eid;
  struct enclave* enclave;
  enclave = get_enclave_by_id(ueid);

  if (!enclave)
  {
    keystone_err("invalid enclave id\n");
    return -EINVAL;
  }

  if (enclave->eid < 0) {
    keystone_err("real enclave does not exist\n");
    return -EINVAL;
  }

  ret = sbi_sm_resume_enclave(enclave->eid);

  arg->error = ret.error;
  arg->value = ret.value;
  keystone_err("[DRV] resume_enclave ret.error: %xld# ret.value: %ld\n",
               arg->error, arg->value);

  return 0;
}

int create_shm(unsigned long args)
{
  struct sbiret ret;
  struct keystone_ioctl_create_shm *ioctl_args = (struct keystone_ioctl_create_shm *)args;

  unsigned long pa = allocate_shm(&host_enclave, ioctl_args->size);
  if (!pa)
    return -1;

  ret = sbi_sm_create_shm(pa, ioctl_args->size);
  if (ret.error)
  {
    keystone_err("keystone_create_shm: SBI call failed with error code %ld\n", ret.error);
    destroy_shm_by_pa(pa);
    goto error;
  }

  ioctl_args->rid = ret.value;
  keystone_info("keystone_create_shm: paddr: %#lx, size: %ld, rid: %ld\n", pa, ioctl_args->size, ioctl_args->rid);

  return 0;
error:
  return -EINVAL;
}

int map_shm(unsigned long arg)
{
  struct sbiret ret;
  struct keystone_ioctl_map_shm *params = (struct keystone_ioctl_map_shm *)arg;
  ret = sbi_sm_map_shm(params->rid);
  if (ret.error)
  {
    keystone_err("keystone_map_shm: SBI call failed with error code %ld\n", ret.error);
    goto error;
  }
  map_pending = 1;
  map_pa = read_reg(a2);
  map_size = read_reg(a3);
  map_rid = params->rid;

  params->size = map_size;

  keystone_err("keystone_map_shm: rid %d pa %#lx size %#lx\n", map_rid, map_pa, map_size);
  return 0;
error:
  return -EINVAL;
}

int unmap_shm(unsigned long arg)
{
  struct sbiret ret;
  struct keystone_ioctl_unmap_shm *params = (struct keystone_ioctl_unmap_shm *)arg;
  uintptr_t va = (uintptr_t)params->va;
  int i;
  for (i = 0; i < mem_mappings_n && mem_mappings[i].va != va; i++)
    ;
  if (i == mem_mappings_n)
    return -1;
  // int ret = SBI_CALL_1(SBI_SM_ELASTICLAVE_UNMAP, (uintptr_t)mem_mappings[i].uid);
  ret = sbi_sm_unmap_shm(mem_mappings[i].rid);

  if (ret.error)
  {
    keystone_err("keystone_unmap_shm: SBI call failed with error code %ld\n", ret.error);
    goto error;
  }

  params->size = mem_mappings[i].size;

  for (; i < mem_mappings_n - 1; i++)
    mem_mappings[i] = mem_mappings[i + 1];
  --mem_mappings_n;
  return 0;
error:
  return -EINVAL;
}

int change_shm(unsigned long arg)
{
  struct sbiret ret;
  struct keystone_ioctl_change_shm *params = (struct keystone_ioctl_change_shm *)arg;
  ret = sbi_sm_change_shm(params->rid, params->perm);

  if (ret.error)
  {
    keystone_err("keystone_change_shm: SBI call failed with error code %ld\n", ret.error);
    goto error;
  }

  return ret.error;

error:
  return -EINVAL;
}

int share_shm(unsigned long arg)
{
  struct sbiret ret;
  unsigned long ueid;
  struct enclave *enclave;
  struct keystone_ioctl_share_shm *params = (struct keystone_ioctl_share_shm *)arg;

  ueid = params->eid;
  enclave = get_enclave_by_id(ueid);

  if (!enclave)
  {
    keystone_err("invalid enclave id\n");
    return -EINVAL;
  }

  if (enclave->eid < 0)
  {
    keystone_err("real enclave does not exist\n");
    return -EINVAL;
  }

  ret = sbi_sm_share_shm(params->rid, enclave->eid, params->perm);

  if (ret.error)
  {
    keystone_err("keystone_share_shm: SBI call failed with error code %ld\n", ret.error);
    goto error;
  }

  return ret.error;

error:
  return -EINVAL;
}

long keystone_ioctl(struct file *filep, unsigned int cmd, unsigned long arg)
{
  long ret;
  char data[512];

  size_t ioc_size;

  if (!arg)
    return -EINVAL;

  ioc_size = _IOC_SIZE(cmd);
  ioc_size = ioc_size > sizeof(data) ? sizeof(data) : ioc_size;

  if (copy_from_user(data,(void __user *) arg, ioc_size))
    return -EFAULT;

  switch (cmd) {
    case KEYSTONE_IOC_CREATE_ENCLAVE:
      ret = keystone_create_enclave(filep, (unsigned long) data);
      break;
    case KEYSTONE_IOC_FINALIZE_ENCLAVE:
      ret = keystone_finalize_enclave((unsigned long) data);
      break;
    case KEYSTONE_IOC_DESTROY_ENCLAVE:
      ret = keystone_destroy_enclave(filep, (unsigned long) data);
      break;
    case KEYSTONE_IOC_RUN_ENCLAVE:
      ret = keystone_run_enclave((unsigned long) data);
      break;
    case KEYSTONE_IOC_RESUME_ENCLAVE:
      ret = keystone_resume_enclave((unsigned long) data);
      break;
    /* Note that following commands could have been implemented as a part of ADD_PAGE ioctl.
     * However, there was a weird bug in compiler that generates a wrong control flow
     * that ends up with an illegal instruction if we combine switch-case and if statements.
     * We didn't identified the exact problem, so we'll have these until we figure out */
    case KEYSTONE_IOC_UTM_INIT:
      ret = utm_init_ioctl(filep, (unsigned long) data);
      break;
  case KEYSTONE_IOC_CREATE_SHM:
    ret = create_shm((unsigned long)data);
    break;
  case KEYSTONE_IOC_MAP_SHM:
    ret = map_shm((unsigned long)data);
    break;
  case KEYSTONE_IOC_UNMAP_SHM:
    ret = unmap_shm((unsigned long)data);
    break;
  case KEYSTONE_IOC_CHANGE_SHM:
    ret = change_shm((unsigned long)data);
    break;
  case KEYSTONE_IOC_SHARE_SHM:
    ret = share_shm((unsigned long)data);
    break;
  default:
    return -ENOSYS;
  }

  if (copy_to_user((void __user*) arg, data, ioc_size))
    return -EFAULT;

  return ret;
}

int keystone_release(struct inode *inode, struct file *file) {
  unsigned long ueid = (unsigned long)(file->private_data);
  struct enclave *enclave;

  /* enclave has been already destroyed */
  if (!ueid) {
    return 0;
  }

  /* We need to send destroy enclave just the eid to close. */
  enclave = get_enclave_by_id(ueid);

  if (!enclave) {
    /* If eid is set to the invalid id, then we do not do anything. */
    return -EINVAL;
  }
  if (enclave->close_on_pexit) {
    return __keystone_destroy_enclave(ueid);
  }
  return 0;
}
