#include <stdio.h>
#include <stddef.h>
#include <stdint.h>
#include <inttypes.h>
#include <stdlib.h>

#include "accellib.h"
#include "encoding.h"

#define AES_BLOCK_BITS (128)
#define AES_BLOCK_BYTES (AES_BLOCK_BITS / 8)

#define ROUNDS 10
#define MAX_DATA_LEN_BYTES (ROUNDS * AES_BLOCK_BYTES)

void print_blocks(unsigned char* data, size_t blk_cnt) {
  uint64_t* data64 = (uint64_t*)data;
  for (size_t i = 0; i < blk_cnt; i++) {
    uint64_t* data64_1 = data64 + (2*i) + 1;
    uint64_t* data64_0 = data64 + (2*i) + 0;

    printf("Block[%" PRIu64 "]: 0x%016" PRIx64 "%016" PRIx64 " (%p,%p)\n", i, *data64_1, *data64_0, data64_1, data64_0);
  }
}

int main() {
  // initialize large array of data to process
  unsigned char data[MAX_DATA_LEN_BYTES];
  for (size_t i = 0; i < MAX_DATA_LEN_BYTES; ++i) {
    data[i] = i;
  }

  // initialize random key
  uint64_t key[4];
  for (size_t i = 0; i < 4; ++i) {
    key[i] = i * 2;
  }

  // begin ROUNDS of encrypt/decrypt (ROUND is i * AES_BLOCK_BYTES encrypt/decrypt + check)
  for (size_t r = 1; r <= ROUNDS; ++r) {
    uint64_t data_len = r * AES_BLOCK_BYTES;

    print_blocks(data, r);

    printf(">> Encrypt start: L:%lu\n", data_len);

    uint8_t* ciphertext_area = Aes256AccelSetup(data_len); // fence, write zero

    printf("src start addr: 0x%016" PRIx64 "\n", (uint64_t)data);
    printf("dest start addr: 0x%016" PRIx64 "\n", (uint64_t)ciphertext_area);

    uint64_t t1 = rdcycle();

    // encrypt
    Aes256Accel(true,
                data,
                data_len,
                key[0],
                key[1],
                key[2],
                key[3],
                ciphertext_area);
    uint64_t t2 = rdcycle();

    printf("Start cycle: %" PRIu64 ", End cycle: %" PRIu64 ", Took: %" PRIu64 "\n",
            t1, t2, t2 - t1);

    print_blocks(ciphertext_area, r);

    // decryption start area
    printf(">> Decrypt start: L:%lu\n", data_len);

    uint8_t* plaintext_area = Aes256AccelSetup(data_len); // fence, write zero

    printf("src start addr: 0x%016" PRIx64 "\n", (uint64_t)ciphertext_area);
    printf("dest start addr: 0x%016" PRIx64 "\n", (uint64_t)plaintext_area);

    t1 = rdcycle();

    // decrypt
    Aes256Accel(false,
                ciphertext_area,
                data_len,
                key[0],
                key[1],
                key[2],
                key[3],
                plaintext_area);
    t2 = rdcycle();

    printf("Start cycle: %" PRIu64 ", End cycle: %" PRIu64 ", Took: %" PRIu64 "\n",
            t1, t2, t2 - t1);

    print_blocks(plaintext_area, r);

    printf("Checking encrypt/decrypt data correctness:\n");
    bool fail = false;
    for (size_t i = 0; i < data_len; i++) {
      if (data[i] != plaintext_area[i]) {
        printf("idx %" PRIu64 ": expected: %x got: %x\n",
            i, data[i], plaintext_area[i]);
        fail = true;
        break;
      }
    }

    free(ciphertext_area);
    free(plaintext_area);

    if (fail) {
        printf("TEST FAILED!\n");
        exit(1);
    } else {
        printf("TEST PASSED!\n");
    }
  }

  return 0;
}
