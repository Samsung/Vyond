//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#ifndef _KEYSTONE_SBI_
#define _KEYSTONE_SBI_

#include "keystone_user.h"
#include "sm_call.h"

#include <asm/sbi.h>

struct sbiret sbi_sm_create_enclave(struct keystone_sbi_create_t* args);
struct sbiret sbi_sm_destroy_enclave(unsigned long eid);
struct sbiret sbi_sm_run_enclave(unsigned long eid);
struct sbiret sbi_sm_resume_enclave(unsigned long eid);
struct sbiret sbi_sm_create_shm(unsigned long pa, unsigned long size);
struct sbiret sbi_sm_map_shm(unsigned long rid);
struct sbiret sbi_sm_unmap_shm(unsigned long rid);
struct sbiret sbi_sm_change_shm(unsigned long rid, unsigned long perm);
struct sbiret sbi_sm_share_shm(unsigned long rid, unsigned long eid, unsigned long perm);

#endif
