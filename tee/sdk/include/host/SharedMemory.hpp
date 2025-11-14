//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#pragma once

#include <assert.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stddef.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <iostream>

#include "./common.h"
#include "KeystoneDevice.hpp"
#include "hash_util.hpp"

namespace Keystone {

class SharedMemory {
 public:
  SharedMemory();
  ~SharedMemory();
  rid_t createShm(size_t size);
  void* mapShm(rid_t rid);
  int unmapShm(void* va);
  int changeShm(rid_t rid, unsigned long perm);
  int shareShm(rid_t rid, int eid, unsigned long perm);

  rid_t getRID() { return rid; }
  void* getVA() { return va; }
  size_t getSize() { return size; }

 private:
  int fd;
  rid_t rid;
  size_t size;
  void* va;
};

}  // namespace Keystone
