//see LICENSE for license
// The following is a RISC-V program to test the functionality of the
// sha3 RoCC accelerator.
// Compile with riscv-gcc sha3-rocc.c
// Run with spike --extension=sha3 pk a.out

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <malloc.h>
#include "rocc.h"
#include "compiler.h"
#include "encoding.h"

#ifdef __linux
#include <sys/mman.h>
#endif
 
#define MAX_STREAMS 16
#define CL_BYTES 64

enum STREAM_TYPE {
    STRIDE_RD = 0,
    STRIDE_WR = 1,
    BURST_RD = 2,
    BURST_WR = 3,
    RAND_RD = 4,
    RAND_WR = 5
};

int main() {
  printf("main() started\n");

  int ii, jj, kk;
  unsigned long cycle_cnt, req_sent;

  int kB = 1024;
  int l2_kB = 2; // Set L2 size as 2kB for CI tests
  int addr_range = l2_kB * kB * 4;

  int stream_cnt = 2;
  int max_reqs = addr_range / (stream_cnt * 2 * CL_BYTES);
  assert(stream_cnt <= MAX_STREAMS);

  int stride_bytes[MAX_STREAMS];
  int stride_idx[MAX_STREAMS];
  for (ii = 0; ii < MAX_STREAMS; ii++) {
      stride_bytes[ii] = 2 * (ii + 1) * CL_BYTES;
      stride_idx[ii] = stride_bytes[ii] / (int)sizeof(char);
  }

  enum STREAM_TYPE stream_type[MAX_STREAMS];
  for (ii = 0; ii < MAX_STREAMS; ii++) {
      int r = ii % 2;
      if (r == 0) stream_type[ii] = RAND_RD;
      else stream_type[ii] = RAND_WR;
  }

  int mem_size = addr_range * stream_cnt;
  int idx_offset[MAX_STREAMS];
  for (ii = 0; ii < stream_cnt; ii++) {
      idx_offset[ii] = addr_range * ii / (int)sizeof(char);
  }
  int max_idx = mem_size / (int)sizeof(char);

  printf("Start allocating memory by memalign\n");
  char* input = (char*)memalign(sizeof(char), (size_t)mem_size);

  printf("Prevents page faults by touching them\n");
  int page_stride = 4 * kB / (int)sizeof(char);
  for (ii = 0; ii < max_idx; ii += page_stride) {
      input[ii] = '3'; // 0011_0011
  }

#ifdef __linux
  // Ensure all pages are resident to avoid accelerator page faults
  if (mlockall(MCL_CURRENT | MCL_FUTURE)) {
    perror("mlockall");
    return 1;
  }
#endif

  do {
    printf("Start basic test\n");

    // insert fence instruction
    asm volatile ("fence");

    // number of streams && max request per stream
    uint64_t stream_cnt_n_addr_range = (addr_range << 5) | (stream_cnt);
    ROCC_INSTRUCTION_SS(2, stream_cnt_n_addr_range, max_reqs, 1);
    for (ii = 0; ii < stream_cnt; ii++) {
        uint64_t stride_n_type = (stride_bytes[ii] << 3) | ((int)stream_type[ii]);

        //set per stream stride, type, starting addr
        ROCC_INSTRUCTION_SS(2, stride_n_type, &input[idx_offset[ii]], 2);
    }

    // get hw counter values
    ROCC_INSTRUCTION_D(2, cycle_cnt, 3);
    ROCC_INSTRUCTION_D(2, req_sent, 4);

    // remove fence instruction
    asm volatile ("fence" ::: "memory");

    assert(cycle_cnt != 0);
    int bytes_sent = req_sent * 16;
    int nano_sec = cycle_cnt / 2; // assuming 2.0 GHz .... float support missing?
    int bw_MBps = (bytes_sent * 1000) / nano_sec;

    printf("cycle_cnt value: %lu\n", cycle_cnt);
    printf("req_sent value: %lu\n", req_sent);
    printf("Achieved BW of the system: %d MB/s\n", bw_MBps);
  } while(0);

  printf("Execution Finished!\n");
  return 0;
}
