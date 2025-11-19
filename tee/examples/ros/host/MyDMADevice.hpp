#ifndef _MY_DMA_DEVICE_H_
#define _MY_DMA_DEVICE_H_

#define MYDEV_PATH "/dev/mydev"
#define MYDEV_IOC_MAGIC 'M'
#define MYDEV_CMD_DEVICE_TO_USER _IO(MYDEV_IOC_MAGIC, 1)
#define MYDEV_CMD_USER_TO_DEVICE _IO(MYDEV_IOC_MAGIC, 2)
#define DMA_SIZE 4096

class MyDMADevice
{

public:
    MyDMADevice();
    ~MyDMADevice();
    int device2DMA();
    int dma2Device();

private:
    int fd;
    void *map;
};

#endif
