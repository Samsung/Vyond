HBM Bandwidth - Pseudo Random Ethash
====================================

This is a HBM bandwidth example using a pseudo random 1024 bit data access pattern to mimic Ethereum Ethash workloads. The design contains 3 compute units of a kernel, reading 1024 bits from a pseudo random address in each of 2 pseudo channels and writing the results of a simple mathematical operation to a pseudo random address in 2 other pseudo channels. To maximize bandwidth the pseudo channels are used in  P2P like configuration - See https://developer.xilinx.com/en/articles/maximizing-memory-bandwidth-with-vitis-and-xilinx-ultrascale-hbm-devices.html for more information on HBM memory access configurations. The host application allocates buffers in 12  HBM banks and runs the compute units concurrently to measure the overall bandwidth between kernel and HBM Memory.

**KEY CONCEPTS:** `High Bandwidth Memory <https://docs.xilinx.com/r/en-US/ug1393-vitis-application-acceleration/HBM-Configuration-and-Use>`__, `Multiple HBM Pseudo-channels <https://docs.xilinx.com/r/en-US/ug1393-vitis-application-acceleration/HBM-Configuration-and-Use>`__, Random Memory Access, Linear Feedback Shift Register

**KEYWORDS:** `HBM <https://docs.xilinx.com/r/en-US/ug1393-vitis-application-acceleration/HBM-Configuration-and-Use>`__, `XCL_MEM_TOPOLOGY <https://docs.xilinx.com/r/en-US/ug1393-vitis-application-acceleration/Assigning-DDR-Bank-in-Host-Code>`__, `cl_mem_ext_ptr_t <https://docs.xilinx.com/r/en-US/ug1393-vitis-application-acceleration/Assigning-DDR-Bank-in-Host-Code>`__

**EXCLUDED PLATFORMS:** 

 - Alveo U25 SmartNIC
 - Alveo U30
 - Alveo U200
 - All Embedded Zynq Platforms, i.e zc702, zcu102 etc
 - All Versal Platforms, i.e vck190 etc
 - Alveo U250
 - AWS VU9P F1
 - Samsung SmartSSD Computation Storage Drive
 - Samsung U.2 SmartSSD
 - X3 Compute Shell
 - All NoDMA Platforms, i.e u50 nodma etc

DESIGN FILES
------------

Application code is located in the src directory. Accelerator binary files will be compiled to the xclbin directory. The xclbin directory is required by the Makefile and its contents will be filled during compilation. A listing of all the files in this example is shown below

::

   src/host.cpp
   src/krnl_vaddmul.cpp
   src/krnl_vaddmul.h
   
COMMAND LINE ARGUMENTS
----------------------

Once the environment has been configured, the application can be executed by

::

   ./hbm_bandwidth_pseudo_random <krnl_vaddmul XCLBIN>

DETAILS
-------

This is host application to test HBM interface bandwidth for pseudo
random 1024 bit data access pattern, mimicking Ethereum Ethash
workloads. Design contains 3 compute units of Kernel. Each compute unit
reads 1024 bits from a pseudo random address in each of 2 pseudo
channels and writes the results of a simple mathematical operation to a
pseudo random address in 2 other pseudo channels. Host application
allocates buffers into all 12 HBM Banks (6 Input buffers and 6 output
buffers). Host application runs all 3 compute units together and
measures the overall HBM bandwidth.

HBM is a high performance RAM interface for 3D-stacked DRAM. HBM can
provide very high bandwidth greater than **400 GB/s** with low power
consumption (HBM2 ~ 20W vs GDDR5 ~ 100W). These 32 memory resources
referenced as HBM [0:31] by v++ are accessed via 16 memory controllers.

Host can allocate a buffer into specific HBM bank using
``CL_MEM_EXT_PTR_XILINX`` flag of buffer. ``cl_mem_ext_ptr`` object
needs to be used in cases where memory assignment is done by user
explicitly:

.. code:: cpp

   cl_mem_ext_ptr_t bufExt;
   bufExt.obj = host_pointer;
   bufExt.param = 0;
   bufExt.flags = n  | XCL_MEM_TOPOLOGY; 
   buffer_input = cl::Buffer(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR, size, &bufExt, &err));

HBM memory must be associated to respective kernel I/O ports using
``sp`` option. We need to add mapping between HBM memory and I/O ports
in ``krnl_vaddmul.cfg`` file

::

   sp=krnl_vaddmul_1.in1:HBM[0]
   sp=krnl_vaddmul_1.in2:HBM[1] 
   sp=krnl_vaddmul_1.out_add:HBM[2]
   sp=krnl_vaddmul_1.out_mul:HBM[3]

To improve the random access bandwidth, in ``krnl_vaddmul.cpp`` the
``latency`` and ``num_read_outstanding`` switches have been added to the
``HLS INTERFACE`` definition.

::

   void krnl_vaddmul(
       const v_dt *in1,             // Read-Only Vector 1
       const v_dt *in2,             // Read-Only Vector 2
       v_dt *out_add,               // Output Result for ADD
       v_dt *out_mul,               // Output Result for MUL
       const unsigned int size,     // Size in integer
       const unsigned int num_times // Running the same kernel operations num_times
       ) {
   #pragma HLS INTERFACE m_axi port = in1 offset = slave bundle = gmem0 latency = 300 num_read_outstanding=64
   #pragma HLS INTERFACE m_axi port = in2 offset = slave bundle = gmem1 latency = 300 num_read_outstanding=64

To see the benefit of HBM, user can look into the runtime logs and see
the overall throughput. Following is the real log reported while running
the design on U50 platform:

::

   Loading: './build_dir.hw.xilinx_u50_xdma_201920_1/krnl_vaddmul.xclbin'
   Creating a kernel [krnl_vaddmul:{krnl_vaddmul_1}] for CU(1)
   Creating a kernel [krnl_vaddmul:{krnl_vaddmul_2}] for CU(2)
   Creating a kernel [krnl_vaddmul:{krnl_vaddmul_3}] for CU(3)
   OVERALL THROUGHPUT = 138.022 GB/s
   CHANNEL THROUGHPUT = 11.501 GB/s
   TEST PASSED

By default we are going with 3 compute units of kernel as we have power
consumption limitation while targeting U50 platform.

For more comprehensive documentation, `click here <http://xilinx.github.io/Vitis_Accel_Examples>`__.