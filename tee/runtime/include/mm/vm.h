#ifndef __VM_H__
#define __VM_H__

#include "mm/common.h"
#include "util/printf.h"
#include "mm/vm_defs.h"

extern uintptr_t runtime_va_start;

/* root page table */
extern pte* root_page_table;
// extern pte secondary_page_tables[MAX_PT_COUNT][BIT(RISCV_PT_INDEX_BITS)]
//     __attribute__((aligned(RISCV_PAGE_SIZE)));

uintptr_t kernel_va_to_pa(void* ptr);
uintptr_t __va(uintptr_t pa);
uintptr_t __pa(uintptr_t va);

#ifndef LOADER_BIN

#define MAX_PT_COUNT 512

#define PAGE_MODE_RT_FULL (PTE_R | PTE_W | PTE_X | PTE_A | PTE_D)
#define PAGE_MODE_USER_FULL (PAGE_MODE_RT_FULL | PTE_U)
#define PAGE_MODE_RT_DATA (PTE_R | PTE_W | PTE_A | PTE_D)
#define PAGE_MODE_USER_DATA (PAGE_MODE_RT_DATA | PTE_U)

extern void* rt_base;
extern uintptr_t kernel_offset; // TODO: is this needed?
extern uintptr_t load_pa_start;

/* Program break */
extern uintptr_t program_break;

/* freemem */
extern uintptr_t freemem_va_start;
extern size_t freemem_size;

/* shared buffer */
extern uintptr_t shared_buffer;
extern uintptr_t shared_buffer_size;

#endif

static inline pte pte_create(uintptr_t ppn, int type)
{
  return (pte)((ppn << PTE_PPN_SHIFT) | PTE_V | (type & PTE_FLAG_MASK) );
}

static inline pte pte_create_invalid(uintptr_t ppn, int type)
{
  return (pte)((ppn << PTE_PPN_SHIFT) | (type & PTE_FLAG_MASK & ~PTE_V));
}

static inline pte ptd_create(uintptr_t ppn)
{
  return pte_create(ppn, PTE_V);
}

static inline uintptr_t ppn(uintptr_t pa)
{
  return pa >> RISCV_PAGE_BITS;
}

// this is identical to ppn, but separate it to avoid confusion between va/pa
static inline uintptr_t vpn(uintptr_t va)
{
  return va >> RISCV_PAGE_BITS;
}

static inline uintptr_t pte_ppn(pte pte)
{
  return pte >> PTE_PPN_SHIFT;
}

#endif
