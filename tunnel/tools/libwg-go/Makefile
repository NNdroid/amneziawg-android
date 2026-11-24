# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.

BUILDDIR ?= $(CURDIR)/build
DESTDIR ?= $(CURDIR)/out

NDK_GO_ARCH_MAP_x86 := 386
NDK_GO_ARCH_MAP_x86_64 := amd64
NDK_GO_ARCH_MAP_arm := arm
NDK_GO_ARCH_MAP_arm64 := arm64
NDK_GO_ARCH_MAP_mips := mipsx
NDK_GO_ARCH_MAP_mips64 := mips64x

comma := ,
CLANG_FLAGS := --target=$(TARGET) --sysroot=$(SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(subst -mthumb,-marm,$(CFLAGS))
export CGO_LDFLAGS := $(CLANG_FLAGS) $(patsubst -Wl$(comma)--build-id=%,-Wl$(comma)--build-id=none,$(LDFLAGS)) -Wl,-soname=libwg-go.so
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

# The preferred Go toolchain is the one installed on this machine (the highest
# version available locally, e.g. Go 1.27). When it is present and at least the
# desired version, the build uses it directly and no download is needed.
#
# When the CMake build finds Go on PATH it passes GO=<path> and
# GO_FROM_CMAKE=1; we then trust it (the path may be a Windows path that cannot
# be executed from make's $(shell)). Otherwise we detect Go on PATH here, and
# if there is none (e.g. on CI) we fall back to a pinned, hash-verified download
# patched for Android's CLOCK_BOOTTIME runtime.
GO ?= go
ifeq ($(GO_FROM_CMAKE),1)
SYSTEM_GO_OK := 1
else
GO_SYS_VER := $(shell $(GO) version 2>/dev/null)
GO_SYS_MJMN := $(patsubst go%,%,$(word 3,$(GO_SYS_VER)))
GO_SYS_MAJOR := $(word 1,$(subst ., ,$(GO_SYS_MJMN)))
GO_SYS_MINOR := $(word 2,$(subst ., ,$(GO_SYS_MJMN)))
SYSTEM_GO_OK := $(shell if [ -n "$(GO_SYS_MAJOR)" ] && { [ "$(GO_SYS_MAJOR)" -gt 1 ] || { [ "$(GO_SYS_MAJOR)" -eq 1 ] && [ "$(GO_SYS_MINOR)" -ge 27 ]; }; }; then echo 1; fi)
endif

# Fallback toolchain: a pinned, hash-verified Go that is downloaded and patched
# for Android's CLOCK_BOOTTIME runtime. This is used by CI, where Go may not be
# preinstalled.
GO_VERSION := 1.27.4
GO_PLATFORM := $(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m))
GO_TARBALL := go$(GO_VERSION).$(GO_PLATFORM).tar.gz
GO_HASH_darwin-amd64 := 2b799377704ef12db39bd254995d5c348c9cc8d86562ca817724390e56c38f43
GO_HASH_darwin-arm64 := 91a44a2b8a716600ad94a4f143e156116620db8828086a6e3602291be9bd0bb4
GO_HASH_linux-amd64 := 7226736c2339719cda28fbf3a54d71aa63bcefe376e98cef00034268dab24840

ifeq ($(SYSTEM_GO_OK),1)
GO_BIN := $(GO)
GO_PREPARED :=
else
GO_BIN := $(BUILDDIR)/go-$(GO_VERSION)/bin/go
GO_PREPARED := $(BUILDDIR)/go-$(GO_VERSION)/.prepared
export PATH := $(BUILDDIR)/go-$(GO_VERSION)/bin/:$(PATH)
endif

default: $(DESTDIR)/libwg-go.so

$(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL):
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	curl -o "$@.tmp" "https://dl.google.com/go/$(GO_TARBALL)" && \
	echo "$(GO_HASH_$(GO_PLATFORM))  $@.tmp" | sha256sum -c && \
	mv "$@.tmp" "$@"'

$(BUILDDIR)/go-$(GO_VERSION)/.prepared: $(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL)
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	tar -C "$(dir $@)" --strip-components=1 -xzf "$^" && \
	patch -p1 -f -N -r- -d "$(dir $@)" < goruntime-boottime-over-monotonic.diff && \
	touch "$@"'

$(DESTDIR)/libwg-go.so: $(GO_PREPARED) go.mod
	"$(GO_BIN)" build -tags linux -ldflags="-X github.com/amnezia-vpn/amneziawg-go/ipc.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/amneziawg -buildid=" -v -trimpath -buildvcs=false -o "$@" -buildmode c-shared

.DELETE_ON_ERROR:
