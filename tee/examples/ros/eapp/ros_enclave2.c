#include "app/eapp_utils.h"
#include "app/string.h"
#include "app/syscall.h"
#include "app/malloc.h"
#include "edge_wrapper.h"

typedef struct Loan
{
  int size;
  int perm;
} loan_t;

void EAPP_ENTRY eapp_entry()
{

  // loan_t loan;
  // loan.size = 1024;
  // loan.perm = 7;
  // unsigned long rid = ocall_loan_shm(&loan, sizeof(loan_t));
  // ocall_print_value(rid);

  // void *shm = map_shm(rid);
  // ocall_print_value((uintptr_t)shm);
  // for (int i = 0; i < 99999; i++)
  //   ;

  // ocall_print_value(*(int *)shm);

  EAPP_RETURN(0);
}
