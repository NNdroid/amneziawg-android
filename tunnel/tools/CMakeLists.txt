# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.

cmake_minimum_required(VERSION 3.4.1)
project("AmneziaWG")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
add_link_options(LINKER:--build-id=none)
add_link_options(-Wl,-z,max-page-size=16384)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
add_compile_options(-Wall -Werror)

add_executable(libwg-quick.so amneziawg-tools/src/wg-quick/android.c ndk-compat/compat.c)
target_compile_options(libwg-quick.so PUBLIC -std=gnu11 -include ${CMAKE_CURRENT_SOURCE_DIR}/ndk-compat/compat.h -DAWG_PACKAGE_NAME=\"${ANDROID_PACKAGE_NAME}\")
target_link_libraries(libwg-quick.so -ldl)

file(GLOB WG_SOURCES amneziawg-tools/src/*.c ndk-compat/compat.c)
add_executable(libwg.so ${WG_SOURCES})
target_include_directories(libwg.so PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg-tools/src/uapi/linux/" "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg-tools/src/")
target_compile_options(libwg.so PUBLIC -std=gnu11 -include ${CMAKE_CURRENT_SOURCE_DIR}/ndk-compat/compat.h -DRUNSTATEDIR=\"/data/data/${ANDROID_PACKAGE_NAME}/cache\")

# Prefer the Go toolchain installed on this machine (the highest version
# available locally, e.g. Go 1.27) so no download is needed. When Go is not
# found (e.g. on CI), the Makefile falls back to a pinned, hash-verified
# download that is patched for Android's CLOCK_BOOTTIME runtime.
find_program(GO_EXECUTABLE go)
set(GO_BUILD_ARG)
if(GO_EXECUTABLE)
    set(GO_BUILD_ARG GO=${GO_EXECUTABLE} GO_FROM_CMAKE=1)
endif()

add_custom_target(libwg-go.so WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libwg-go" COMMENT "Building amneziawg-go" VERBATIM COMMAND "${ANDROID_HOST_PREBUILTS}/bin/make"
    ${GO_BUILD_ARG}
    ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
    ANDROID_PACKAGE_NAME=${ANDROID_PACKAGE_NAME}
    GRADLE_USER_HOME=${GRADLE_USER_HOME}
    CC=${CMAKE_C_COMPILER}
    CFLAGS=${CMAKE_C_FLAGS}
    LDFLAGS=${CMAKE_SHARED_LINKER_FLAGS}
    SYSROOT=${CMAKE_SYSROOT}
    TARGET=${CMAKE_C_COMPILER_TARGET}
    DESTDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
    BUILDDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../generated-src
)

# Strip unwanted ELF sections to prevent DT_FLAGS_1 warnings on old Android versions.
# elf-cleaner is a POSIX-only host tool (it relies on sys/mman.h, unistd.h and mmap()),
# so it can only be built on a Unix host. On a Windows host there is no sys/mman.h
# (neither TDM-GCC nor the NDK's Windows host sysroot provides it), so the tool cannot
# be compiled natively. Skipping it on Windows is safe: it is only an optional
# post-build optimization, and release builds are produced on Linux CI.
if(NOT CMAKE_HOST_WIN32)
    file(GLOB ELF_CLEANER_SOURCES elf-cleaner/*.c elf-cleaner/*.cpp)
    # elf-cleaner is a host tool used to strip the built Android libraries. Use the
    # host C compiler (a plain `cc` works on Linux CI; on Windows the NDK's clang is
    # used, which can target the host).
    find_program(ELF_CLEANER_CC NAMES cc clang gcc)
    if(NOT ELF_CLEANER_CC)
        set(ELF_CLEANER_CC cc)
    endif()
    add_custom_target(elf-cleaner COMMENT "Building elf-cleaner" VERBATIM COMMAND "${ELF_CLEANER_CC}"
            -O2 -DPACKAGE_NAME="elf-cleaner" -DPACKAGE_VERSION="" -DCOPYRIGHT=""
            -o "${CMAKE_CURRENT_BINARY_DIR}/elf-cleaner" ${ELF_CLEANER_SOURCES}
    )
    add_custom_command(TARGET libwg.so POST_BUILD VERBATIM COMMAND "${CMAKE_CURRENT_BINARY_DIR}/elf-cleaner"
            --api-level "${ANDROID_NATIVE_API_LEVEL}" "$<TARGET_FILE:libwg.so>")
    add_dependencies(libwg.so elf-cleaner)
    add_custom_command(TARGET libwg-quick.so POST_BUILD VERBATIM COMMAND "${CMAKE_CURRENT_BINARY_DIR}/elf-cleaner"
            --api-level "${ANDROID_NATIVE_API_LEVEL}" "$<TARGET_FILE:libwg-quick.so>")
    add_dependencies(libwg-quick.so elf-cleaner)
endif()
