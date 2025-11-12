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

// int main()
void EAPP_ENTRY eapp_entry()
{
  // char *msg = "hello world!\n";
  // char *msg2 = "2nd hello world!\n";

  // edge_init();

  // unsigned long ret = ocall_print_buffer(msg, 13);
  // ocall_print_buffer(msg2, 17);

  // ocall_print_value(ret);

  // struct edge_data pkgstr;
  // ocall_get_string(&pkgstr);

  // void *host_str = malloc(pkgstr.size);
  // copy_from_shared(host_str, pkgstr.offset, pkgstr.size);

  // int i;
  // int ct;
  // for (i = 0; i < pkgstr.size; i++)
  //{
  //   if (((char *)host_str)[i] == 'l')
  //   {
  //     ct++;
  //   }
  // }

  // ocall_print_value(ct);
  // ocall_print_value(1234);

  loan_t loan;
  loan.size = 1024;
  loan.perm = 7;
  unsigned long rid = ocall_loan_shm(&loan, sizeof(loan_t));
  ocall_print_value(rid);

  void *shm = map_shm(rid);
  ocall_print_value((uintptr_t)shm);
  //*((int *)shm) = 1234;
  ocall_print_value(*(int *)shm);

  EAPP_RETURN(0);
}
