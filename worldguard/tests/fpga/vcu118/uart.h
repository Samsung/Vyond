
#ifndef _UART_H
#define _UART_H

#define UART_CTRL_ADDR 0x64000000UL

#define UART_REG_TXFIFO         0x00
#define UART_REG_TXCTRL         0x08
#define UART_TXEN               0x1

#include <stdint.h>
#include <stdlib.h>
#define REG32(p, i)	((p)[(i) >> 2])
static volatile uint32_t * const uart = (void *)(UART_CTRL_ADDR);

void kputc(char c);



int init_uart();
#endif
