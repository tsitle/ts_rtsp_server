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
			if [ "$1" = "debian_rootfs" ] || [ "$1" = "debian_dist" ]; then
				echo -n "amd64"
			elif [ "$1" = "graal" ]; then
				echo -n "x64"
			elif [ "$1" = "javacpp" ]; then
				echo -n "x86_64"
			else
				echo "${VAR_MYNAME}: Error: invalid arg '$1'" >/dev/stderr
				return 1
			fi
			;;
		aarch64*)
			if [ "$1" = "debian_rootfs" ]; then
				echo -n "arm64v8"
			elif [ "$1" = "debian_dist" ] || [ "$1" = "javacpp" ]; then
				echo -n "arm64"
			elif [ "$1" = "graal" ]; then
				echo -n "aarch64"
			else
				echo "${VAR_MYNAME}: Error: invalid arg '$1'" >/dev/stderr
				return 1
			fi
			;;
		armv7*)
			if [ "$1" = "debian_rootfs" ]; then
				echo -n "arm32v7"
			elif [ "$1" = "debian_dist" ]; then
				echo -n "armhf"
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

if [ $# -ne 2 ]; then
	{
		echo "${VAR_MYNAME}: invalid number of arguments"
		echo
		echo "Usage: ${VAR_MYNAME} <APP_VERSION> <COMMIT_OR_BRANCH>"
		echo "Examples: ${VAR_MYNAME} 1.0-SNAPSHOT testing"
		echo "          ${VAR_MYNAME} 1.1.4 ce3991c3"
	} >/dev/stderr
	exit 1
fi
if [ -z "${1}" ]; then
	echo "${VAR_MYNAME}: empty <APP_VERSION>" >/dev/stderr
	exit 1
fi
if [ -z "${2}" ]; then
	echo "${VAR_MYNAME}: empty <COMMIT_OR_BRANCH>" >/dev/stderr
	exit 1
fi

# ----------------------------------------------------------

cd build-ctx || exit 1

# ----------------------------------------------------------

LVAR_DEBIAN_DIST="$(_getCpuArch debian_dist)"

LVAR_GRAAL_ARCH="$(_getCpuArch graal)"
LVAR_FFMPEG_VERSION="8.1.2"
LVAR_JAVACPP_VERSION="1.5.14"
LVAR_JAVACPP_ARCH="$(_getCpuArch javacpp)"

LVAR_IMAGE_NAME="app-ts_rtsp_server-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_VER="${1}"

LVAR_APP_GIT_BRANCH_OR_COMMIT="${2}"

docker build \
	-t "${LVAR_IMAGE_NAME}":"${LVAR_IMAGE_VER}" \
	--build-arg CF_GRAAL_ARCH="${LVAR_GRAAL_ARCH}" \
	--build-arg CF_FFMPEG_VERSION="${LVAR_FFMPEG_VERSION}" \
	--build-arg CF_JAVACPP_VERSION="${LVAR_JAVACPP_VERSION}" \
	--build-arg CF_JAVACPP_ARCH="${LVAR_JAVACPP_ARCH}" \
	--build-arg CF_APP_GIT_BRANCH_OR_COMMIT="${LVAR_APP_GIT_BRANCH_OR_COMMIT}" \
	. \
	|| exit 1

docker tag "${LVAR_IMAGE_NAME}":"${LVAR_IMAGE_VER}" "${LVAR_IMAGE_NAME}":latest
