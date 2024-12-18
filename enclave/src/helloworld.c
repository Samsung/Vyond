#include <stdio.h>

#ifdef __linux
#include <sys/mman.h>
#endif



int main() 
{
#ifdef __linux
  // Ensure all pages are resident to avoid accelerator page faults
  if (mlockall(MCL_CURRENT | MCL_FUTURE)) {
    perror("mlockall");
    return 1;
  }
  printf("hello linux world\n");
#else
  printf("hello world\n");
#endif

  return 0;
}
