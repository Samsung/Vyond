
set(app_name ros)
set(eapp1_bin ros_e1)
set(eapp2_bin ros_e2)
set(all_eapps ros_e1 ros_e2)
set(eapp1_src eapp/ros_enclave1.c eapp/edge_wrapper.c)
set(eapp2_src eapp/ros_enclave2.c eapp/edge_wrapper.c)
set(host_bin ros-runner)
set(host_src host/ros_host.cpp host/edge_wrapper.cpp)
set(package_name "ros.ke")
set(package_script "./ros-runner ros_e1 ros_e2 eyrie-rt loader.bin")
set(eyrie_plugins "io_syscall linux_syscall env_setup")

# host
add_executable(${host_bin} ${host_src})
find_package(Threads REQUIRED)
target_link_libraries(${host_bin} PRIVATE ${KEYSTONE_LIB_HOST} ${KEYSTONE_LIB_EDGE} Threads::Threads)

# eapp
add_executable(${eapp1_bin} ${eapp1_src})
target_link_libraries(${eapp1_bin} ${KEYSTONE_LIB_EAPP} ${KEYSTONE_LIB_EDGE} "-static")

add_executable(${eapp2_bin} ${eapp2_src})
target_link_libraries(${eapp2_bin} ${KEYSTONE_LIB_EAPP} ${KEYSTONE_LIB_EDGE} "-static")

#set_target_properties(${eapp1_bin} ${eapp2_bin} PROPERTIES LINK_FLAGS "-static -T ${CMAKE_CURRENT_SOURCE_DIR}/app.lds")
set_target_properties(${all_eapps}
  PROPERTIES LINK_FLAGS "-nostdlib -static -T ${CMAKE_CURRENT_SOURCE_DIR}/app.lds")


# add target for Eyrie runtime (see keystone.cmake)

set(eyrie_files_to_copy .options_log eyrie-rt loader.bin)
add_eyrie_runtime(${eapp1_bin}-eyrie
  ${eyrie_plugins}
  ${eyrie_files_to_copy})

add_eyrie_runtime(${eapp2_bin}-eyrie
  ${eyrie_plugins})

# add target for packaging (see keystone.cmake)

add_keystone_package(${app_name}-package
  ${package_name}
  ${package_script}
  ${eyrie_files_to_copy} ${eapp1_bin} ${eapp2_bin} ${host_bin})

add_dependencies(${app_name}-package ${eapp1_bin}-eyrie ${eapp2_bin}-eyrie)

# add package to the top-level target
add_dependencies(examples ${app_name}-package)
