#ifndef _MM_H_
#define _MM_H_
#include <stddef.h>
#include <stdint.h>

#include "keystone_user.h"
#include "mm/vm_defs.h"

enum vma_type { VMA_TYPE_ANON, VMA_TYPE_SHARED };

struct vma {
  uintptr_t vaddr, paddr, size;
  enum vma_type type;
  rid_t rid;
};

uintptr_t translate(uintptr_t va);
pte* pte_of_va(uintptr_t va);
uintptr_t map_page(uintptr_t vpn, uintptr_t ppn, int flags);
uintptr_t alloc_page(uintptr_t vpn, int flags);
uintptr_t realloc_page(uintptr_t vpn, int flags);
void free_page(uintptr_t vpn);
size_t alloc_pages(uintptr_t vpn, size_t count, int flags);
void free_pages(uintptr_t vpn, size_t count);
size_t test_va_range(uintptr_t vpn, size_t count);

uintptr_t get_program_break();
void set_program_break(uintptr_t new_break);

void map_with_reserved_page_table(uintptr_t base, uintptr_t size, uintptr_t ptr, pte* l2_pt, pte* l3_pt);

void
map_pages(
    uintptr_t vaddr_base, uintptr_t paddr_base, uintptr_t size,
    unsigned int mode, enum vma_type type, rid_t rid);

void
unmap_pages(struct vma* vma);

uintptr_t
find_va_range(uintptr_t size);

struct vma*
get_vma_by_pa(uintptr_t pa);

struct vma*
get_vma_by_va(uintptr_t va);

void
add_vma(
    uintptr_t vaddr, uintptr_t paddr, uintptr_t size, enum vma_type type,
    rid_t rid);

void
remove_vma(struct vma* vma);

struct vma*
get_vma_by_rid(rid_t rid);
#endif /* _MM_H_ */
