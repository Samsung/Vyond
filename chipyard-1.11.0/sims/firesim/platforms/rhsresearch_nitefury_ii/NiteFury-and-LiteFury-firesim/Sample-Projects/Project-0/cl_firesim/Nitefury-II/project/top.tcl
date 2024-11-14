

set ifrequency              [lindex $argv 0]
set istrategy               [lindex $argv 1]

source project.tcl
cd project
source ../../../common/Scripts/rebuild.tcl
close_project
