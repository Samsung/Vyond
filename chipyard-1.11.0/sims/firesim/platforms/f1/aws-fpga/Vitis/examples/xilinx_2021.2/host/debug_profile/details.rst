Debug profile
=============

The Vitis development environment can generate a waveform view and
launch a live ``waveform viewer`` when running hardware emulation. It
displays in-depth details on the emulation results at system level,
compute unit level, and at function level. The details include data
transfers between the kernel and global memory, data flow via
inter-kernel pipes as well as data flow via intrakernel pipes. They
provide many insights into the performance bottleneck from the system
level down to individual function call to help developers optimize their
applications.

Example uses a simple vector addition kernel to demonstrate the
debugging information that can be viewed in the waveform.

``xrt.ini`` file is used to launch the waveform. Waveform can be viewed
at runtime by launching GUI with the following command in this file.

::

   [Emulation]
   debug_mode=GUI

Waveform can also be generated by using ``.wdb`` file generated during
hardware emulation which can be opened in ``Vivado`` with the commands
written in the script provided under ``scripts/open_waveform.tcl``. For
this case, we need to add the following flags in the ``xrt.ini`` file:

::

   [Emulation]
   debug_mode=batch

Waveforms are helpful to view data transfers to memory from host as well
as data transfer from each AXI Master ports. Another feature which
waveform viewer provides is the ``CU Stalls``. The stall bus compiles
all of the lowest level stall signals and reports the percentage that
are stalling at any point in time. This provides a factor of how much of
the kernel is stalling at any point in the simulation and user can
optimize the design to improve the utility of hardware based on these
stall signals.

If the user wants to record profiling information for arbitrary sections of his code, the following 2 features can be used - 

1. user_range - Profiles and captures the data in the specified range

2. user_event - Marks the event in the timeliene trace
