#!/usr/bin/env bash

#
# by TS, Sep 2026
#

VAR_MYNAME="$(basename "$0")"

# ----------------------------------------------------------

# Outputs CPU architecture string
#
# @param string $1 debian_rootfs|debian_dist
#
# @return int EXITCODE
function _getCpuArch() {
	case "$(uname -m)" in
		x86_64*)
			echo -n ""
			if [ "$1" = "debian_dist" ]; then
				echo -n "amd64"
			elif [ "$1" = "app" ]; then
				echo -n "x64"
			else
				echo "${VAR_MYNAME}: Error: invalid arg '$1'" >/dev/stderr
				return 1
			fi
			;;
		aarch64*)
			if [ "$1" = "debian_dist" ] || [ "$1" = "app" ]; then
				echo -n "arm64"
			else
				echo "${VAR_MYNAME}: Error: invalid arg '$1'" >/dev/stderr
				return 1
			fi
			;;
		*)
			echo "${VAR_MYNAME}: Error: Unknown CPU architecture '$(uname -m)'" >/dev/stderr
			return 1
			;;
	esac
	return 0
}

_getCpuArch debian_dist >/dev/null || exit 1

# ----------------------------------------------------------

LVAR_DEBIAN_DIST="$(_getCpuArch debian_dist)"

LVAR_GRAAL_VERSION_MAJOR="25"
LVAR_FFMPEG_VERSION="8.1.2"
LVAR_JAVACPP_VERSION="1.5.14"

LVAR_IMAGE_GFJ_BASE_NAME="app-ts_rtsp_server-gfj_base-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_GFJ_BASE_VER="g${LVAR_GRAAL_VERSION_MAJOR}_f${LVAR_FFMPEG_VERSION}_j${LVAR_JAVACPP_VERSION}"

docker run \
	-it --rm \
	--name app-ts_rtsp_server-gfj_base \
	"${LVAR_IMAGE_GFJ_BASE_NAME}:${LVAR_IMAGE_GFJ_BASE_VER}" \
	/bin/bash
