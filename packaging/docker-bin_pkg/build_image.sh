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

if [ $# -lt 2 ] || [ $# -gt 4 ]; then
	{
		echo "${VAR_MYNAME}: invalid number of arguments"
		echo
		echo "Usage: ${VAR_MYNAME} <APP_VERSION> <COMMIT_OR_BRANCH> [lib_xrtxp:<COMMIT_OR_BRANCH>] [lib_ffmpeg:<COMMIT_OR_BRANCH>]"
		echo "Examples: ${VAR_MYNAME} 1.0-SNAPSHOT testing"
		echo "          ${VAR_MYNAME} 1.1.4 ce3991c3"
		echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_xrtxp:a1c2e3g4"
		echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_ffmpeg:8c5d2e6c"
		echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_xrtxp:a1c2e3g4 lib_ffmpeg:8c5d2e6c"
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

LOPT_APP_VERSION="${1}"
shift
LOPT_APP_COMMIT_OR_BRANCH="${2}"
shift
LOPT_LIB_XRTXP_COMMIT_OR_BRANCH=""
LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH=""

while [ $# -gt 0 ]; do
	if ! echo -n "${1}" | grep -q ":"; then
		echo "${VAR_MYNAME}: invalid arg '${1}'" >/dev/stderr
		exit 1
	fi
	TMP_ARG_K="$(echo -n "${1}" | cut -f1 -d:)"
	TMP_ARG_V="$(echo -n "${1}" | cut -f2 -d:)"
	if [ "${TMP_ARG_K}" = "lib_xrtxp" ]; then
		LOPT_LIB_XRTXP_COMMIT_OR_BRANCH="${TMP_ARG_V}"
	elif [ "${TMP_ARG_K}" = "lib_ffmpeg" ]; then
		LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH="${TMP_ARG_V}"
	else
		echo "${VAR_MYNAME}: invalid arg '${1}'" >/dev/stderr
		exit 1
	fi
	shift
done

# ----------------------------------------------------------
# - GraalVM + FFmpeg + JavaCPP base image ------------------
# ----------------------------------------------------------

cd build-ctx-gfj-base || exit 1

LVAR_DEBIAN_DIST="$(_getCpuArch debian_dist)"

LVAR_GRAAL_ARCH="$(_getCpuArch graal)"
LVAR_FFMPEG_VERSION="8.1.2"
LVAR_JAVACPP_VERSION="1.5.14"
LVAR_JAVACPP_ARCH="$(_getCpuArch javacpp)"

LVAR_IMAGE_GFJ_BASE_NAME="app-ts_rtsp_server-gfj_base-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_GFJ_BASE_VER="g25_f${LVAR_FFMPEG_VERSION}_j${LVAR_JAVACPP_VERSION}"

docker image inspect "${LVAR_IMAGE_GFJ_BASE_NAME}":"${LVAR_IMAGE_GFJ_BASE_VER}" >/dev/null 2>&1 || {
	echo -e "\n${VAR_MYNAME}: ---------------------------------------------------------------------------"
	echo -e "${VAR_MYNAME}: building base image '${LVAR_IMAGE_GFJ_BASE_NAME}:${LVAR_IMAGE_GFJ_BASE_VER}'...\n"
	docker build \
		-t "${LVAR_IMAGE_GFJ_BASE_NAME}":"${LVAR_IMAGE_GFJ_BASE_VER}" \
		--build-arg CF_GRAAL_ARCH="${LVAR_GRAAL_ARCH}" \
		--build-arg CF_FFMPEG_VERSION="${LVAR_FFMPEG_VERSION}" \
		--build-arg CF_JAVACPP_VERSION="${LVAR_JAVACPP_VERSION}" \
		--build-arg CF_JAVACPP_ARCH="${LVAR_JAVACPP_ARCH}" \
		. \
		|| exit 1
}

# ----------------------------------------------------------
# - App image ----------------------------------------------
# ----------------------------------------------------------

cd ../build-ctx-app || exit 1

LVAR_IMAGE_APP_NAME="app-ts_rtsp_server-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_APP_VER="${LOPT_APP_VERSION}"

LVAR_USE_CUSTOM_LIBS_FROM_MAVEN="true"
if [ -n "${LOPT_LIB_XRTXP_COMMIT_OR_BRANCH}" ] || [ -n "${LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH}" ]; then
	LVAR_USE_CUSTOM_LIBS_FROM_MAVEN="false"
fi

docker image inspect "${LVAR_IMAGE_APP_NAME}":"latest" >/dev/null 2>&1 && {
	docker image rm "${LVAR_IMAGE_APP_NAME}:latest"
}
docker image inspect "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}" >/dev/null 2>&1 && {
	docker image rm "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}"
}

echo -e "\n${VAR_MYNAME}: ---------------------------------------------------------------------------"
echo -e "${VAR_MYNAME}: building app image '${LVAR_IMAGE_APP_NAME}:${LVAR_IMAGE_APP_VER}'...\n"
docker build \
	-t "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}" \
	--build-arg CF_BASE_IMAGE="${LVAR_IMAGE_GFJ_BASE_NAME}:${LVAR_IMAGE_GFJ_BASE_VER}" \
	--build-arg CF_GRAAL_ARCH="${LVAR_GRAAL_ARCH}" \
	--build-arg CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT="${LOPT_LIB_XRTXP_COMMIT_OR_BRANCH}" \
	--build-arg CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT="${LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH}" \
	--build-arg CF_USE_CUSTOM_LIBS_FROM_MAVEN="${LVAR_USE_CUSTOM_LIBS_FROM_MAVEN}" \
	--build-arg CF_JAVACPP_ARCH="${LVAR_JAVACPP_ARCH}" \
	--build-arg CF_APP_GIT_BRANCH_OR_COMMIT="${LOPT_APP_COMMIT_OR_BRANCH}" \
	. \
	|| exit 1

docker tag "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}" "${LVAR_IMAGE_APP_NAME}":latest
