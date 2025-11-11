
//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#include "SharedMemory.hpp"

#include <sys/mman.h>

namespace Keystone {

SharedMemory::SharedMemory() {
  fd = open(KEYSTONE_DEV_PATH, O_RDWR);
  if (fd < 0) {
    PERROR("cannot open device file");
  }
}
SharedMemory::~SharedMemory() { close(fd); }

rid_t
SharedMemory::createShm(size_t size) {
  struct keystone_ioctl_create_shm create_shm;
  create_shm.size = size;
  if (ioctl(fd, KEYSTONE_IOC_CREATE_SHM, &create_shm)) {
    return 0;
  }

  return create_shm.rid;
}

void*
SharedMemory::mapShm(rid_t rid) {
  unsigned long size;
  struct keystone_ioctl_map_shm params = {.rid = rid, .size = size};
  int ret = ioctl(fd, KEYSTONE_IOC_MAP_SHM, &params);
  if (ret == -1) return NULL;
  return mmap(NULL, params.size, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
}
int
SharedMemory::unmapShm(void* va) {
  unsigned long size;
  struct keystone_ioctl_unmap_shm params = {.va = (uintptr_t)va, .size = size};
  int ret = ioctl(fd, KEYSTONE_IOC_UNMAP_SHM, &params);
  if (ret) return ret;
  return munmap(va, params.size);
}

int
SharedMemory::changeShm(rid_t rid, unsigned long perm) {
  struct keystone_ioctl_change_shm params = {.rid = rid, .perm = perm};
  return ioctl(fd, KEYSTONE_IOC_CHANGE_SHM, &params);
}

int
SharedMemory::shareShm(rid_t rid, int eid, unsigned long perm) {
  struct keystone_ioctl_share_shm params = {
      .rid = rid, .eid = eid, .perm = perm};
  return ioctl(fd, KEYSTONE_IOC_SHARE_SHM, &params);
}

}  // namespace Keystone
