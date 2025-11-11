//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#ifndef __SBI_H_
#define __SBI_H_

#include <stdint.h>
#include <stddef.h>

#include "sm_call.h"
#define SBI_CALL(___ext, ___which, ___arg0, ___arg1, ___arg2)    \
  ({                                                             \
    register uintptr_t a0 __asm__("a0") = (uintptr_t)(___arg0);  \
    register uintptr_t a1 __asm__("a1") = (uintptr_t)(___arg1);  \
    register uintptr_t a2 __asm__("a2") = (uintptr_t)(___arg2);  \
    register uintptr_t a6 __asm__("a6") = (uintptr_t)(___which); \
    register uintptr_t a7 __asm__("a7") = (uintptr_t)(___ext);   \
    __asm__ volatile("ecall"                                     \
                     : "+r"(a0)                                  \
                     : "r"(a1), "r"(a2), "r"(a6), "r"(a7)        \
                     : "memory");                                \
    a0;                                                          \
  })

/* Lazy implementations until SBI is finalized */
#define SBI_CALL_0(___ext, ___which) SBI_CALL(___ext, ___which, 0, 0, 0)
#define SBI_CALL_1(___ext, ___which, ___arg0) \
  SBI_CALL(___ext, ___which, ___arg0, 0, 0)
#define SBI_CALL_2(___ext, ___which, ___arg0, ___arg1) \
  SBI_CALL(___ext, ___which, ___arg0, ___arg1, 0)
#define SBI_CALL_3(___ext, ___which, ___arg0, ___arg1, ___arg2) \
  SBI_CALL(___ext, ___which, ___arg0, ___arg1, ___arg2)

void
sbi_putchar(char c);
void
sbi_set_timer(uint64_t stime_value);
uintptr_t
sbi_stop_enclave(uint64_t request);
void
sbi_exit_enclave(uint64_t retval);
uintptr_t
sbi_random();
uintptr_t
sbi_query_multimem(size_t *size);
uintptr_t
sbi_query_multimem_addr(uintptr_t *addr);
uintptr_t
sbi_attest_enclave(void* report, void* buf, uintptr_t len);
uintptr_t
sbi_get_sealing_key(uintptr_t key_struct, uintptr_t key_ident, uintptr_t len);

#endif
