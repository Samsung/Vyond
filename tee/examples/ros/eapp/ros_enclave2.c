#include "app/eapp_utils.h"
#include "app/string.h"
#include "app/syscall.h"
#include "app/malloc.h"
#include "edge_wrapper.h"

void EAPP_ENTRY eapp_entry()
{
  // get region id (rid) from the host and map to the enclave's va space.
  unsigned long rid = ocall_loan_shm();
  void *shm = map_shm(rid);
  ocall_print_value((uintptr_t)shm);

  // read shared memory data written by the publisher
  ocall_print_value(*(int *)shm);

  EAPP_RETURN(0);
}
