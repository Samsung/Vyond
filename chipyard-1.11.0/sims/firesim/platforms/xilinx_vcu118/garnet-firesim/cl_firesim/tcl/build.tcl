#-
# Copyright (c) 2020-2021 Jessica Clarke
#
# @BERI_LICENSE_HEADER_START@
#
# Licensed to BERI Open Systems C.I.C. (BERI) under one or more contributor
# license agreements.  See the NOTICE file distributed with this work for
# additional information regarding copyright ownership.  BERI licenses this
# file to you under the BERI Hardware-Software License, Version 1.0 (the
# "License"); you may not use this file except in compliance with the
# License.  You may obtain a copy of the License at:
#
#   http://www.beri-open-systems.org/legal/license-1-0.txt
#
# Unless required by applicable law or agreed to in writing, Work distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#
# @BERI_LICENSE_HEADER_END@
#

set ifrequency  [lindex $argv 0]
set istrategy   [lindex $argv 1]
set iboard      [lindex $argv 2]

set desired_host_frequency $ifrequency
# TODO: this needs to be piped through elsewhere?
set strategy $istrategy

set project_dir ".."
set CL_DIR "$project_dir"
set garnet_dir "$project_dir/.."

set project_name "example"
set partition_module "example"

set_param sta.enableAutoGenClkNamePersistence 0

source "$garnet_dir/tcl/build.tcl"

garnet_create_synth_project

# Tie-offs deliberately drive ports with constant 0
set_msg_config -id {Synth 8-3917} -suppress
# Many inputs deliberately left unused
set_msg_config -id {Synth 8-3331} -suppress


#file mkdir $CL_DIR/design/ipgen
#set ipgen_scripts [glob -nocomplain $CL_DIR/design/FireSim-generated.*.ipgen.tcl]
#foreach script $ipgen_scripts {
#    source $script
#}

# Generate targets for all IPs contained within the generated module hierarchy.
# With the exception of the PLL, these are the only IP instances that don't have
# their output artifacts checked in.
#generate_target all [get_ips]

#synth_ip [get_ips]

#source $HDK_SHELL_DIR/build/scripts/aws_gen_clk_constraints.tcl

read_ip [ list \
  $CL_DIR/ip/axi_clock_converter_dramslim/axi_clock_converter_dramslim.xci \
  $CL_DIR/ip/axi_clock_converter_oclnew/axi_clock_converter_oclnew.xci \
  $CL_DIR/ip/axi_clock_converter_512_wide/axi_clock_converter_512_wide.xci \
  $CL_DIR/ip/axi_dwidth_converter_0/axi_dwidth_converter_0.xci
]

# Additional IP's that might be needed if using the DDR

read_xdc [ list \
   $CL_DIR/build/constraints/cl_synth_user.xdc \
   $CL_DIR/design/FireSim-generated.synthesis.xdc \
]

# FireSim custom clocking
source $CL_DIR/build/scripts/synth_firesim_clk_wiz.tcl

#Do not propagate local clock constraints for clocks generated in the SH
#set_property USED_IN {synthesis implementation OUT_OF_CONTEXT} [get_files cl_clocks_aws.xdc]
#set_property PROCESSING_ORDER EARLY  [get_files cl_clocks_aws.xdc]

add_files [list \
    "$CL_DIR/design/cl_firesim_defines.vh" \
    "$CL_DIR/design/FireSim-generated.defines.vh" \
    "$CL_DIR/design/cl_firesim.sv" \
    "$CL_DIR/design/FireSim-generated.sv" \
]

garnet_synth_design
close_project

set_param sta.enableAutoGenClkNamePersistence 1

garnet_create_impl_project

read_xdc [ list \
 $CL_DIR/build/constraints/cl_pnr_user.xdc \
 $CL_DIR/design/FireSim-generated.implementation.xdc
]

set_property PROCESSING_ORDER late [get_files cl_pnr_user.xdc]
set_property PROCESSING_ORDER late [get_files FireSim-generated.implementation.xdc]

garnet_link_design

garnet_opt_design
garnet_place_design
garnet_route_design

garnet_report_timing
garnet_write_artifacts

close_project
