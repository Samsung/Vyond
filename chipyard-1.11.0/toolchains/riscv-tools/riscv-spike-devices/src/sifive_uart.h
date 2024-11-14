#ifndef _SIFIVE_UART_H
#define _SIFIVE_UART_H

#include <riscv/abstract_device.h>
#include <riscv/dts.h>
#include <riscv/sim.h>
#include <fesvr/term.h>
#include <fdt/libfdt.h>

#define UART_TXFIFO (0x00)
#define UART_RXFIFO (0x04)
#define UART_TXCTRL (0x08)
#define UART_TXMARK (0x0a)
#define UART_RXCTRL (0x0c)
#define UART_RXMARK (0x0e)

#define UART_IE     (0x10)
#define UART_IP     (0x14)
#define UART_DIV    (0x18)
#define UART_PARITY (0x1c)
#define UART_WIRE4  (0x20)
#define UART_EITHER8OR9 (0x24)

#define UART_GET_TXCNT(txctrl)   ((txctrl >> 16) & 0x7)
#define UART_GET_RXCNT(rxctrl)   ((rxctrl >> 16) & 0x7)
#define UART_RX_FIFO_SIZE (8)

#define UART_IE_TXWM       (1)
#define UART_IE_RXWM       (2)

#define UART_IP_TXWM       (1)
#define UART_IP_RXWM       (2)

class sifive_uart_t : public abstract_device_t {
public:
  sifive_uart_t(abstract_interrupt_controller_t *intctrl, reg_t int_id) :
    ie(0), ip(0), txctrl(0), rxctrl(0), div(0), interrupt_id(int_id), intctrl(intctrl) {}

  bool load(reg_t addr, size_t len, uint8_t* bytes) override;
  bool store(reg_t addr, size_t len, const uint8_t* bytes) override;
  void tick(reg_t UNUSED rtc_ticks) override;

private:
  std::queue<uint8_t> rx_fifo;
  uint32_t ie;
  uint32_t ip;
  uint32_t txctrl;
  uint32_t rxctrl;
  uint32_t div;
  reg_t interrupt_id;
  abstract_interrupt_controller_t *intctrl;

  uint64_t read_ip() {
    uint64_t ret = 0;
    uint64_t txcnt = UART_GET_TXCNT(txctrl);
    uint64_t rxcnt = UART_GET_RXCNT(rxctrl);
    if (txcnt != 0) ret |= UART_IP_TXWM;
    if (rx_fifo.size() > rxcnt) ret |= UART_IP_RXWM;
    return ret;
  }

  uint32_t read_rxfifo() {
    if (!rx_fifo.size()) return 0x80000000;
    uint8_t r = rx_fifo.front();
    rx_fifo.pop();
    update_interrupts();
    return r;
  }

  void update_interrupts() {
    int cond = 0;
    if ((ie & UART_IE_TXWM) ||
        ((ie & UART_IE_RXWM) && rx_fifo.size())) {
      cond = 1;
    }
    intctrl->set_interrupt_level(interrupt_id, (cond) ? 1 : 0);
  }
};

#endif
