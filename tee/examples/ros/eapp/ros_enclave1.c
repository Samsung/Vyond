#include "app/eapp_utils.h"
#include "app/string.h"
#include "app/syscall.h"
#include "app/malloc.h"
#include "edge_wrapper.h"

#define MYDEV_BASE (0x6004000)
#define MYDEV_SIZE (0x1000)
#define MYDEV_OFF_CMD 0x00
#define MYDEV_OFF_DMA_GPA_LOW 0x08
#define MYDEV_OFF_DMA_GPA_HIGH 0x0c
#define MYDEV_OFF_DMA_LEN 0x10
#define MYDEV_OFF_STATUS 0x18

static inline volatile uint32_t mmio_read(void *reg)
{
  return *(volatile uint32_t *)(reg);
}

static inline void mmio_write(void *reg, uint32_t val)
{
  *(uint32_t *)(reg) = val;
}

void EAPP_ENTRY eapp_entry()
{
  // get region id (rid) from the host and map to the enclave's va space.
  shm_t shm_mydev = ocall_loan_shm(0);
  ocall_print_value((uintptr_t)shm_mydev.rid);
  ocall_print_value((uintptr_t)shm_mydev.pa);
  ocall_print_value((uintptr_t)shm_mydev.size);
  void *dma = map_shm(shm_mydev.rid);
  void *mydev = mydev_map(MYDEV_BASE, MYDEV_SIZE);
  ocall_print_value((uintptr_t)dma);

  // write data to shared memory
  *((int *)dma) = 1234;
  ocall_print_value(*(int *)dma);

  uint32_t status = 1;
  while (status != 0)
    status = mmio_read(mydev + MYDEV_OFF_STATUS);

  // printf("[PUBLISHER] setup dma address %#lx and size %#lx\n", shm.pa, shm.size);
  mmio_write(mydev + MYDEV_OFF_DMA_GPA_LOW, (uint32_t)(shm_mydev.pa & 0xffffffff));
  mmio_write(mydev + MYDEV_OFF_DMA_GPA_HIGH, (uint32_t)((shm_mydev.pa >> 32) & 0xffffffff));
  mmio_write(mydev + MYDEV_OFF_DMA_LEN, (uint32_t)shm_mydev.size);

  // printf("[PUBLISHER] Send CMD 1 (device to dma)\n");
  mmio_write(mydev + MYDEV_OFF_CMD, 1);

  status = mmio_read(mydev + MYDEV_OFF_STATUS);
  while (status != 0)
    status = mmio_read(mydev + MYDEV_OFF_STATUS);

  ocall_print_value(*(unsigned long *)dma);

  // printf("[PUBLISHER] Writing Publisher to DMA\n");
  unsigned char *p = (unsigned char *)dma;
  for (int i = 0; i < 64; ++i)
    p[i] = 2;

  // printf("[PUBLISHER] Send CMD 2 (dma to device)\n");
  mmio_write(mydev + MYDEV_OFF_CMD, 2);

  status = mmio_read(mydev + MYDEV_OFF_STATUS);
  while (status != 0)
    status = mmio_read(mydev + MYDEV_OFF_STATUS);

  // printf("[PUBLISHER] Writing to shared mem for subsriber \n");
  shm_t shm_sub = ocall_loan_shm(1);
  ocall_print_value((uintptr_t)shm_sub.rid);
  ocall_print_value((uintptr_t)shm_sub.pa);
  ocall_print_value((uintptr_t)shm_sub.size);
  void *sub = map_shm(shm_sub.rid);
  ocall_print_value((uintptr_t)sub);
  p = (unsigned char *)sub;
  for (int i = 0; i < 64; ++i)
    p[i] = 2;

  unmap_shm(shm_mydev.rid, dma, shm_mydev.size);
  unmap_shm(shm_sub.rid, sub, shm_sub.size);
  // mydev_unmap(mydev, MYDEV_SIZE);
  EAPP_RETURN(0);
}
