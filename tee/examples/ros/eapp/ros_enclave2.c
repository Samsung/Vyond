#include "app/eapp_utils.h"
#include "app/string.h"
#include "app/syscall.h"
#include "app/malloc.h"
#include "edge_wrapper.h"

void EAPP_ENTRY eapp_entry()
{
  // get region id (rid) from the host and map to the enclave's va space.
  shm_t shm = ocall_loan_shm(1);
  void *dma = map_shm(shm.rid);
  ocall_print_value((uintptr_t)dma);

  // read shared memory data written by the publisher
  ocall_print_value(*(int *)dma);

  unmap_shm(shm.rid, dma, shm.size);
  EAPP_RETURN(0);
}
