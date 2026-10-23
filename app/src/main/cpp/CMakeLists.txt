cmake_minimum_required(VERSION 3.22.1)

project("stealth_engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/vfs
    ${CMAKE_CURRENT_SOURCE_DIR}/hook
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
)

add_library(
    stealth_engine
    SHARED
    stealth_engine.cpp
    vfs/vfs_core.cpp
    hook/syscall_hook.cpp
)

find_library(log-lib log)

target_link_libraries(
    stealth_engine
    ${log-lib}
    dl
)
