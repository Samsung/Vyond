#include "uart.h"

int init_uart()
{
	REG32(uart, UART_REG_TXCTRL) = UART_TXEN;
	return 0;
}

void kputc(char c)
{
	volatile uint32_t *tx = &REG32(uart, UART_REG_TXFIFO);
	while ((int32_t)(*tx) < 0);
	*tx = c;
}
