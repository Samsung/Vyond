//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#ifndef _KEYSTONE_H_
#define _KEYSTONE_H_

#include <asm/sbi.h>
#include <asm/csr.h>
#include <linux/slab.h>
#include <linux/module.h>
#include <linux/uaccess.h>
#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/idr.h>

#include <linux/file.h>

/* IMPORTANT: This code assumes Sv39 */
#include "riscv64.h"

#define PAGE_UP(addr)	(((addr)+((PAGE_SIZE)-1))&(~((PAGE_SIZE)-1)))

typedef uintptr_t vaddr_t;
typedef uintptr_t paddr_t;
typedef uint32_t rid_t;

extern struct miscdevice keystone_dev;
extern struct list_head shm_list;

long keystone_ioctl(struct file* filep, unsigned int cmd, unsigned long arg);
int keystone_release(struct inode *inode, struct file *file);
int keystone_mmap(struct file *filp, struct vm_area_struct *vma);

/* enclave private memory */
struct epm {
  pte_t* root_page_table;
  vaddr_t ptr;
  size_t size;
  unsigned long order;
  paddr_t pa;
  bool is_cma;
};

struct utm {
  pte_t* root_page_table;
  void* ptr;
  size_t size;
  unsigned long order;
};

struct shm
{
  struct list_head list;
  void *ptr;    // kernel address
  paddr_t pa;   // physical address
  uintptr_t va; // user virtual address
  size_t size;
  unsigned long order;
  int is_cma;
};

struct mem_mapping
{
  rid_t rid;
  uintptr_t va;
  uintptr_t pa;
  size_t size;
};

extern struct mem_mapping mem_mappings[];
extern int mem_mappings_n;

struct enclave
{
  unsigned long eid;
  int close_on_pexit;
  struct utm* utm;
  struct epm* epm;
  struct shm *recent_shm;
  bool is_init;
  bool epm_mapped;
};

extern struct enclave host_enclave;
extern int map_pending;
extern rid_t map_rid;
extern uintptr_t map_pa;
extern unsigned long map_size;

// global debug functions
void debug_dump(char* ptr, unsigned long size);

// runtime/app loader
int keystone_rtld_init_runtime(struct enclave* enclave, void* __user rt_ptr, size_t rt_sz, unsigned long rt_stack_sz, unsigned long* rt_offset);

int keystone_rtld_init_app(struct enclave* enclave, void* __user app_ptr, size_t app_sz, size_t app_stack_sz, unsigned long stack_offset);

// untrusted memory mapper
int keystone_rtld_init_untrusted(struct enclave* enclave, void* untrusted_ptr, size_t untrusted_size);

struct enclave* get_enclave_by_id(unsigned int ueid);
struct enclave* create_enclave(unsigned long min_pages);
int destroy_enclave(struct enclave* enclave);

unsigned int enclave_idr_alloc(struct enclave* enclave);
struct enclave* enclave_idr_remove(unsigned int ueid);
struct enclave* get_enclave_by_id(unsigned int ueid);

static inline uintptr_t  epm_satp(struct epm* epm) {
  return ((uintptr_t)epm->root_page_table >> RISCV_PGSHIFT | SATP_MODE_CHOICE);
}

int epm_destroy(struct epm* epm);
int epm_init(struct epm* epm, unsigned int count);
int utm_destroy(struct utm* utm);
int utm_init(struct utm* utm, size_t untrusted_size);
paddr_t epm_va_to_pa(struct epm* epm, vaddr_t addr);

uintptr_t allocate_shm(struct enclave *enclave, uintptr_t size);
int destroy_shm_by_pa(uintptr_t pa);
int shm_destroy(struct shm *shm);
int shm_init(struct shm *shm, size_t shared_size);

#define keystone_info(fmt, ...) \
  pr_info("keystone_enclave: " fmt, ##__VA_ARGS__)
#define keystone_err(fmt, ...) \
  pr_err("keystone_enclave: " fmt, ##__VA_ARGS__)
#define keystone_warn(fmt, ...) \
  pr_warn("keystone_enclave: " fmt, ##__VA_ARGS__)
#endif
