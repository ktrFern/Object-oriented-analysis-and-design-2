cmake_minimum_required(VERSION 3.15)
project(PhotoLab)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(PhotoLab main.cpp)

target_link_libraries(PhotoLab ws2_32)