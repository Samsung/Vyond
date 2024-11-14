#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"
#include "nic.h"
#include "encoding.h"

#define NPACKETS 1000
#define PACKET_WORDS 180

uint64_t out_packets[NPACKETS][PACKET_WORDS];
uint64_t in_packet[3];
int send_idx = 0, comp_idx = 0;

static inline uint64_t macaddr_reverse(uint64_t macaddr)
{
	return ((macaddr >> 40) & 0xffL) |
	       ((macaddr >> 24) & 0xff00L) |
	       ((macaddr >> 8)  & 0xff0000L) |
	       ((macaddr << 8)  & 0xff000000L) |
	       ((macaddr << 24) & 0xff00000000L) |
	       ((macaddr << 40) & 0xff0000000000L);
}

uint64_t macaddr_add(uint64_t macaddr, int inc)
{
	uint64_t temp;

	temp = macaddr_reverse(macaddr);
	temp += inc;
	return macaddr_reverse(temp);
}

static void fill_packet(
	uint64_t *packet, uint64_t srcmac, uint64_t dstmac, int id)
{
	packet[0] = dstmac << 16;
	packet[1] = srcmac | (0x1008L << 48);

	for (int i = 2; i < PACKET_WORDS; i++)
		packet[i] = (id << 16) | i;
}

static void send_packets(void)
{
	while (send_idx < NPACKETS) {
		uint32_t counts = nic_counts();
		int requests = (counts >> NIC_COUNT_SEND_REQ) & 0xff;
		int complete = (counts >> NIC_COUNT_SEND_COMP) & 0xff;

		while (complete > 0 && comp_idx < NPACKETS) {
			nic_complete_send();
			comp_idx++;
			complete--;
		}

		while (requests > 0 && send_idx < NPACKETS) {
			nic_post_send((uint64_t) out_packets[send_idx], PACKET_WORDS * 8);
			send_idx++;
			requests--;
		}
	}

	while (comp_idx < NPACKETS) {
		uint32_t counts = nic_counts();
		int complete = (counts >> NIC_COUNT_SEND_COMP) & 0xff;

		while (complete > 0 && comp_idx < NPACKETS) {
			nic_complete_send();
			comp_idx++;
			complete--;
		}
	}
}

static void recv_ack(void)
{
	int len;

	while ((nic_counts() >> NIC_COUNT_RECV_REQ) == 0) {}
	nic_post_recv((uint64_t) in_packet);

	while ((nic_counts() >> NIC_COUNT_RECV_COMP) == 0) {}
	len = nic_complete_recv();

	if (len != 24)
		printf("Error: incorrect acknowledgement length %d\n", len);

	asm volatile ("fence");

	if (in_packet[2] != NPACKETS)
		printf("Error: incorrect acknowledgement packet!\n");
}

int main(void)
{
	uint64_t srcmac = nic_macaddr();
	uint64_t dstmac = macaddr_add(srcmac, 1);

	for (int i = 0; i < NPACKETS; i++)
		fill_packet(out_packets[i], srcmac, dstmac, i);

	asm volatile ("fence");

	send_packets();
	recv_ack();

	printf("All packets acknowledged\n");

	return 0;
}
