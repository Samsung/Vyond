//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#ifndef _EDGE_WRAPPER_H_
#define _EDGE_WRAPPER_H_

#include "edge/edge_call.h"
#include "host/keystone.h"

typedef struct packaged_str
{
    unsigned long str_offset;
    size_t len;
} packaged_str_t;

typedef struct Loan
{
    int size;
    int perm;
} loan_t;

typedef unsigned char byte;

void edge_init(Keystone::Enclave *enclave);

void print_buffer_wrapper(void *buffer, size_t size);
unsigned long print_buffer(char *str);

void print_value_wrapper(void *buffer, size_t size);
void print_value(unsigned long val);

void get_host_string_wrapper(void *buffer, size_t size);
const char *get_host_string();

void loan_shm_wrapper(void *buffer, size_t size);
unsigned long loan_shm();

#endif /* _EDGE_WRAPPER_H_ */
