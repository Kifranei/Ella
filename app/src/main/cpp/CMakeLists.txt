cmake_minimum_required(VERSION 3.22.1)

project(ella_oboe)

# Oboe is pulled in as a prefab AAR (com.google.oboe:oboe) via `buildFeatures { prefab = true }`.
find_package(oboe REQUIRED CONFIG)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -fomit-frame-pointer")

add_library(ella_oboe SHARED oboe_sink.cpp)

target_link_libraries(ella_oboe
        oboe::oboe
        android
        log)
