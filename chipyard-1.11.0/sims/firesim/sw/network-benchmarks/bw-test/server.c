#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "mmio.h"
#include "nic.h"
#include "encoding.h"

uint64_t in_packets[NPACKETS][PACKET_WORDS];
int total_req = 0;
int total_comp = 0;
char inflight[NPACKETS];

static inline void process_loop(void)
{
        uint32_t counts, recv_req, recv_comp;
        static int req_id = 0, comp_id = 0;
	int len;

	counts = nic_counts();
	recv_req  = (counts >> NIC_COUNT_RECV_REQ)  & 0xff;
        recv_comp = (counts >> NIC_COUNT_RECV_COMP) & 0xff;

        for (int i = 0; i < recv_comp; i++) {
		len = nic_complete_recv();
		if (len != PACKET_WORDS * sizeof(uint64_t)) {
			printf("Incorrectly sized packet\n");
			abort();
		}
		inflight[comp_id] = 0;
		comp_id = (comp_id + 1) % NPACKETS;
		total_comp++;
        }

	for (int i = 0; i < recv_req; i++) {
		if (inflight[req_id])
			break;
		nic_post_recv((uint64_t) in_packets[req_id]);
		inflight[req_id] = 1;
		req_id = (req_id + 1) % NPACKETS;
		total_req++;
	}
}

int main(void)
{
	uint64_t cycle;

	memset(inflight, 0, NPACKETS);

	do {
                process_loop();
		cycle = rdcycle();
	} while (cycle < END_CYCLE);

	printf("cycles: %lu, completed: %d\n",
			cycle, total_comp);

        return 0;
}
