cmake_minimum_required(VERSION 3.22.1)
project(nfsbridge LANGUAGES CXX)

# The launcher's JNI bridge (see bridge.cpp). The game library itself is not
# built here; it ships in the release zip and is loaded at run time.
add_library(nfsbridge SHARED bridge.cpp)

target_compile_features(nfsbridge PRIVATE cxx_std_17)
target_compile_options(nfsbridge PRIVATE -O2 -fvisibility=hidden -Wall -Wextra)
target_include_directories(nfsbridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
# dlopen/dlsym resolve from libc on bionic; only log is needed explicitly.
target_link_libraries(nfsbridge log)
