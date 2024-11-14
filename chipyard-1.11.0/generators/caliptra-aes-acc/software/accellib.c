#include <assert.h>
#include <malloc.h>
#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>
#include "accellib.h"

#define PAGESIZE_BYTES 4096

unsigned char * Aes256AccelSetup(size_t write_region_size) {
#ifndef NOACCEL_DEBUG
    ROCC_INSTRUCTION(AES256_OPCODE, FUNCT_SFENCE);
#endif

    size_t regionsize = sizeof(char) * (write_region_size);

    unsigned char* fixed_alloc_region = (unsigned char*)memalign(PAGESIZE_BYTES, regionsize);
    for (uint64_t i = 0; i < regionsize; i += PAGESIZE_BYTES) {
        fixed_alloc_region[i] = 0;
    }

    uint64_t fixed_ptr_as_int = (uint64_t)fixed_alloc_region;

    assert((fixed_ptr_as_int & 0x7) == 0x0);

    printf("constructed %" PRIu64 " byte region, starting at 0x%016" PRIx64 ", paged-in, for accel\n",
            (uint64_t)regionsize, fixed_ptr_as_int);

    return fixed_alloc_region;
}

volatile int Aes256BlockOnCompletion(volatile int * completion_flag) {
    uint64_t retval;
#ifndef NOACCEL_DEBUG
    ROCC_INSTRUCTION_D(AES256_OPCODE, retval, FUNCT_CHECK_COMPLETION);
#endif
    asm volatile ("fence");

#ifndef NOACCEL_DEBUG
    while (! *(completion_flag)) {
        asm volatile ("fence");
    }
#endif
    return *completion_flag;
}

void Aes256AccelNonblocking(bool encrypt,
                            const unsigned char* data,
                            size_t data_length,
                            uint64_t key0,
                            uint64_t key1,
                            uint64_t key2,
                            uint64_t key3,
                            unsigned char* result,
                            int* success_flag) {
    assert (data_length % 16 == 0 && "Data length must be divisible by block size of 128b (16B)");
#ifndef NOACCEL_DEBUG
    ROCC_INSTRUCTION_SS(AES256_OPCODE,
                        (uint64_t)data,
                        (uint64_t)data_length,
                        FUNCT_SRC_INFO);

    ROCC_INSTRUCTION_SS(AES256_OPCODE,
                        (uint64_t)key0,
                        (uint64_t)key1,
                        FUNCT_KEY_0);

    ROCC_INSTRUCTION_SS(AES256_OPCODE,
                        (uint64_t)key2,
                        (uint64_t)key3,
                        FUNCT_KEY_1);

    ROCC_INSTRUCTION_S(AES256_OPCODE,
                        (uint64_t)encrypt,
                        FUNCT_MODE);

    ROCC_INSTRUCTION_SS(AES256_OPCODE,
                        (uint64_t)result,
                        (uint64_t)success_flag,
                        FUNCT_DEST_INFO);
#endif
}

int Aes256Accel(bool encrypt,
                const unsigned char* data,
                size_t data_length,
                uint64_t key0,
                uint64_t key1,
                uint64_t key2,
                uint64_t key3,
                unsigned char* result) {
    int completion_flag = 0;

#ifdef NOACCEL_DEBUG
    printf("completion_flag addr : 0x%x\n", &completion_flag);
#endif

    Aes256AccelNonblocking(encrypt,
                            data,
                            data_length,
                            key0,
                            key1,
                            key2,
                            key3,
                            result,
                            &completion_flag);
    return Aes256BlockOnCompletion(&completion_flag);
}
