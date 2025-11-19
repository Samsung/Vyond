#include <stdio.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>

#include "MyDMADevice.hpp"

MyDMADevice::MyDMADevice()
{
    fd = open(MYDEV_PATH, O_RDWR);
    if (fd < 0)
    {
        perror("open");
    }

    map = mmap(NULL, DMA_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED)
    {
        perror("mmap");
        close(fd);
    }
    printf("[HOST] Mapped dma buffer at %p (size %d)\n", map, DMA_SIZE);
}
MyDMADevice::~MyDMADevice()
{
    munmap(map, DMA_SIZE);
    close(fd);
}

int MyDMADevice::device2DMA()
{
    int ret;
    // Trigger device -> guest DMA (device will write into the buffer)
    if (ret = ioctl(fd, MYDEV_CMD_DEVICE_TO_USER) < 0)
    {
        perror("[HOST] ioctl DEVICE_TO_USER ERROR");
    }
    else
    {
        printf("[HOST] After DEVICE -> USER DMA, first 64 bytes:\n");
        unsigned char *p = (unsigned char *)map;
        for (int i = 0; i < 64; ++i)
        {
            printf("%02x ", p[i]);
            if ((i & 0xf) == 0xf)
                printf("\n");
        }
        printf("\n");
    }
    return ret;
}

int MyDMADevice::dma2Device()
{
    int ret;
    // Fill buffer with 0xA5 pattern and trigger USER->DEVICE DMA
    // memset(map, 0xA5, DMA_SIZE);
    if (ret = ioctl(fd, MYDEV_CMD_USER_TO_DEVICE) < 0)
    {
        perror("[HOST] ioctl USER_TO_DEVICE");
    }
    else
    {
        printf("[HOST] USER -> DEVICE DMA completed (device should have read the buffer)\n");
    }
    return ret;
}
