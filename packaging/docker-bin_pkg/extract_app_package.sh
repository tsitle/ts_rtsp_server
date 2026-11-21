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

function printUsage() {
	echo "Usage: ${VAR_MYNAME} [cb_javacpp_ffmpeg [rkmpp]]"
	echo "Examples: ${VAR_MYNAME}"
	echo "          ${VAR_MYNAME} cb_javacpp_ffmpeg"
	echo "          ${VAR_MYNAME} cb_javacpp_ffmpeg rkmpp"
	echo ""
	echo "Options:"
	echo "  'cb_javacpp_ffmpeg': Use ts_lib_ffmpeg version with custom-built JavaCPP/FFmpeg"
	echo "  'rkmpp'            : Use ts_lib_ffmpeg version with custom-built JavaCPP/FFmpeg and ARM64 RKMPP support"
	echo "                       (Rockchip hardware accelerated video en-/decoders)"
}

if [ $# -gt 0 ] && [ "${1}" = "--help" ]; then
	printUsage
	exit 0
fi
if [ $# -gt 2 ]; then
	echo -e "${VAR_MYNAME}: invalid number of arguments\n" >/dev/stderr
	printUsage >/dev/stderr
	exit 1
fi

LOPT_DO_CUSTOM_BUILD_JAVACPP_FFMPEG=false
LOPT_DO_RKMPP=false

while [ $# -gt 0 ]; do
	if [ -z "${1}" ]; then
		shift
		continue
	fi
	#
	if [ "${1}" = "cb_javacpp_ffmpeg" ]; then
		LOPT_DO_CUSTOM_BUILD_JAVACPP_FFMPEG=true
		shift
		continue
	fi
	if [ "${1}" = "rkmpp" ]; then
		LOPT_DO_RKMPP=true
		shift
		continue
	fi
	#
	echo "${VAR_MYNAME}: invalid arg '${1}'" >/dev/stderr
	exit 1
done

# ----------------------------------------------------------

if [ ! -d "../../src" ]; then
	echo "${VAR_MYNAME}: could not find 'src/'" >/dev/stderr
	exit 1
fi

# ----------------------------------------------------------

LCFG_OUTPUT_DIR_DISTPRE="../../distPre"

LCFG_DEBIAN_DIST="$(_getCpuArch debian_dist)"

LVAR_EXTRA_IMAGE_NAME_QUALIFIER=""
[ "${LOPT_DO_CUSTOM_BUILD_JAVACPP_FFMPEG}" = "true" ] && LVAR_EXTRA_IMAGE_NAME_QUALIFIER+="-cb_javacpp_ffmpeg"
[ "${LOPT_DO_RKMPP}" = "true" ] && LVAR_EXTRA_IMAGE_NAME_QUALIFIER+="-rkmpp"

LCFG_IMAGE_NAME="app-ts_rtsp_server${LVAR_EXTRA_IMAGE_NAME_QUALIFIER}-${LCFG_DEBIAN_DIST}"

LCFG_APP_ARCH="$(_getCpuArch app)"

LVAR_APP_VERSION="$(docker run -t --rm "${LCFG_IMAGE_NAME}:latest" --version | grep -i "rtsp")"
LVAR_APP_VERSION="$(echo -n "${LVAR_APP_VERSION}" | cut -f2 -d/ | cut -f1 -d\  | tr -d "[:cntrl:]")"
if [ -z "${LVAR_APP_VERSION}" ]; then
	echo "${VAR_MYNAME}: could not determine app version" >/dev/stderr
	exit 1
fi

docker run \
	-it --rm \
	-d \
	--name app-ts_rtsp_server-temp \
	-e "USER_ID=$(id -u)" \
	-e "GROUP_ID=$(id -g)" \
	-e "TZ=Europe/Berlin" \
	--entrypoint "/bin/sh" \
	"${LCFG_IMAGE_NAME}:latest"

echo -e "\n${VAR_MYNAME}: copying binary package from Docker Image...\n"
docker cp app-ts_rtsp_server-temp:/opt/ts_rtsp_server "ts_rtsp_server${LVAR_EXTRA_IMAGE_NAME_QUALIFIER}-${LVAR_APP_VERSION}-lx-${LCFG_APP_ARCH}-bin"

docker stop app-ts_rtsp_server-temp

LVAR_PKG_BASE="ts_rtsp_server${LVAR_EXTRA_IMAGE_NAME_QUALIFIER}-${LVAR_APP_VERSION}-lx-${LCFG_APP_ARCH}-bin"

echo -e "\n${VAR_MYNAME}: creating binary package TAR ball...\n"
tar czf "${LVAR_PKG_BASE}.tgz" "${LVAR_PKG_BASE}" || exit 1
rm -r "${LVAR_PKG_BASE}"

test -d "${LCFG_OUTPUT_DIR_DISTPRE}" || mkdir "${LCFG_OUTPUT_DIR_DISTPRE}"
test -d "${LCFG_OUTPUT_DIR_DISTPRE}" || {
	echo "${VAR_MYNAME}: directory '${LCFG_OUTPUT_DIR_DISTPRE}' does not exist" >/dev/stderr
	exit 1
}

echo -e "\n${VAR_MYNAME}: moving TAR ball to '${LCFG_OUTPUT_DIR_DISTPRE}/${LVAR_PKG_BASE}.tgz'\n"
mv -i "${LVAR_PKG_BASE}.tgz" "${LCFG_OUTPUT_DIR_DISTPRE}/"

echo -e "\n${VAR_MYNAME}: done."
