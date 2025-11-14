//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#ifndef _EDGE_WRAPPER_H_
#define _EDGE_WRAPPER_H_
#include "edge/edge_call.h"

typedef struct shm
{
    rid_t rid;
    size_t size;
} shm_t;

void edge_init();

unsigned long ocall_print_buffer(char *data, size_t data_len);
void ocall_print_value(unsigned long val);
void ocall_get_string(struct edge_data *retdata);
shm_t ocall_loan_shm();
#endif /* _EDGE_WRAPPER_H_ */
