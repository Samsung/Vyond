#include <stdlib.h>
#include <stdio.h>

#include "mmio.h"
#include "nic.h"
#include "encoding.h"

#define NPACKETS 1000
#define PACKET_WORDS 180

uint64_t in_packets[NPACKETS][PACKET_WORDS];
uint64_t out_packet[3];
int recv_idx = 0, comp_idx = 0;

static void recv_packets(void)
{
	while (recv_idx < NPACKETS) {
		uint32_t counts = nic_counts();
		int requests = (counts >> NIC_COUNT_RECV_REQ) & 0xff;
		int complete = (counts >> NIC_COUNT_RECV_COMP) & 0xff;

		while (complete > 0 && comp_idx < NPACKETS) {
			nic_complete_recv();
			comp_idx++;
			complete--;
		}

		while (requests > 0 && recv_idx < NPACKETS) {
			nic_post_recv((uint64_t) in_packets[recv_idx]);
			recv_idx++;
			requests--;
		}
	}

	while (comp_idx < NPACKETS) {
		uint32_t counts = nic_counts();
		int complete = (counts >> NIC_COUNT_RECV_COMP) & 0xff;

		while (complete > 0 && comp_idx < NPACKETS) {
			nic_complete_recv();
			comp_idx++;
			complete--;
		}
	}
}

static uint64_t check_packets(void)
{
	uint64_t srcmac;

	srcmac = in_packets[0][1] & 0xFFFFFFFFFFFFL;

	for (int i = 0; i < NPACKETS; i++) {
		for (int j = 2; j < PACKET_WORDS; j++) {
			uint64_t expected = (i << 16) | j;
			if (in_packets[i][j] != expected) {
				printf("Error: bad data %lx @ %d, %d\n",
						in_packets[i][j], i, j);
			}
		}
	}

	return srcmac;
}

static void send_ack(uint64_t dstmac, uint64_t srcmac)
{
	out_packet[0] = dstmac << 16;
	out_packet[1] = srcmac | (0x1008L << 48);
	out_packet[2] = NPACKETS;
	asm volatile ("fence");

	while ((nic_counts() >> NIC_COUNT_SEND_REQ) == 0) {}
	nic_post_send((uint64_t) out_packet, 24);

	while ((nic_counts() >> NIC_COUNT_SEND_COMP) == 0) {}
	nic_complete_send();
}

int main(void)
{
	uint64_t srcmac = nic_macaddr();
	uint64_t dstmac;

	recv_packets();
	dstmac = check_packets();
	send_ack(dstmac, srcmac);

	printf("Received all packets\n");

	for (;;) {}

	return 0;
}
