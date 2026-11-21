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

function printUsage() {
	echo "Usage: ${VAR_MYNAME} <APP_VERSION> <COMMIT_OR_BRANCH>"
	echo "           [lib_xrtxp:<COMMIT_OR_BRANCH>] [lib_ffmpeg:<COMMIT_OR_BRANCH>|localMaven]"
	echo "           [cb_javacpp_ffmpeg [rkmpp]]"
	echo "Examples: ${VAR_MYNAME} 1.0-SNAPSHOT testing"
	echo "          ${VAR_MYNAME} 1.1.4 ce3991c3"
	echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_xrtxp:a1c2e3g4"
	echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_ffmpeg:8c5d2e6c"
	echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_ffmpeg:localMaven cb_javacpp_ffmpeg rkmpp"
	echo "          ${VAR_MYNAME} 1.1.4 ce3991c3 lib_xrtxp:a1c2e3g4 lib_ffmpeg:8c5d2e6c"
	echo ""
	echo "Options:"
	echo "  'lib_xrtxp:<COMMIT_OR_BRANCH>' : build ts_lib_xrtxp from source code (instead of using Maven Central Repository version)"
	echo "  'lib_ffmpeg:<COMMIT_OR_BRANCH>': build ts_lib_ffmpeg from source code (instead of using Maven Central/Local Repository version)"
	echo "  'lib_ffmpeg:localMaven'        : use ts_lib_ffmpeg version from the local Maven Repository"
	echo "  'cb_javacpp_ffmpeg'            : Use ts_lib_ffmpeg version with custom-built JavaCPP/FFmpeg"
	echo "  'rkmpp'                        : Use ts_lib_ffmpeg version with custom-built JavaCPP/FFmpeg and ARM64 RKMPP support"
	echo "                                   (Rockchip hardware accelerated video en-/decoders)"
}

if [ $# -gt 0 ] && [ "${1}" = "--help" ]; then
	printUsage
	exit 0
fi
if [ $# -lt 2 ] || [ $# -gt 6 ]; then
	echo -e "${VAR_MYNAME}: invalid number of arguments\n" >/dev/stderr
	printUsage >/dev/stderr
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
LOPT_APP_COMMIT_OR_BRANCH="${1}"
shift
LOPT_LIB_XRTXP_COMMIT_OR_BRANCH=""
LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH=""
LOPT_LIB_FFMPEG_FROM_MAVEN_LOCAL=false
LOPT_DO_CUSTOM_BUILD_JAVACPP_FFMPEG=false
LOPT_DO_RKMPP=false

while [ $# -gt 0 ]; do
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
	if ! echo -n "${1}" | grep -q ":"; then
		echo "${VAR_MYNAME}: invalid arg '${1}'" >/dev/stderr
		exit 1
	fi
	TMP_ARG_K="$(echo -n "${1}" | cut -f1 -d:)"
	TMP_ARG_V="$(echo -n "${1}" | cut -f2 -d:)"
	if [ "${TMP_ARG_K}" = "lib_xrtxp" ]; then
		LOPT_LIB_XRTXP_COMMIT_OR_BRANCH="${TMP_ARG_V}"
	elif [ "${TMP_ARG_K}" = "lib_ffmpeg" ]; then
		if [ "${TMP_ARG_V}" = "localMaven" ]; then
			LOPT_LIB_FFMPEG_FROM_MAVEN_LOCAL=true
		else
			LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH="${TMP_ARG_V}"
		fi
	else
		echo "${VAR_MYNAME}: invalid arg '${1}'" >/dev/stderr
		exit 1
	fi
	shift
done

# ----------------------------------------------------------
# - TsLibFfmpeg base image ---------------------------------
# ----------------------------------------------------------

LVAR_DEBIAN_DIST="$(_getCpuArch debian_dist)"

LVAR_GRAAL_ARCH="$(_getCpuArch graal)"
LVAR_GRAAL_VERSION_MAJOR="25"

LVAR_IMAGE_BASE_GRAAL_NAME="lib-ts_ffmpeg-gfj_base-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_BASE_GRAAL_VER="g${LVAR_GRAAL_VERSION_MAJOR}"

LVAR_EXTRA_IMAGE_NAME_QUALIFIER=""
[ "${LOPT_DO_CUSTOM_BUILD_JAVACPP_FFMPEG}" = "true" ] && LVAR_EXTRA_IMAGE_NAME_QUALIFIER+="-cb_javacpp_ffmpeg"
[ "${LOPT_DO_RKMPP}" = "true" ] && LVAR_EXTRA_IMAGE_NAME_QUALIFIER+="-rkmpp"

LVAR_IMAGE_LIB_TS_FFMPEG_NAME="lib-ts_ffmpeg${LVAR_EXTRA_IMAGE_NAME_QUALIFIER}-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_LIB_TS_FFMPEG_VER="latest"

docker image inspect "${LVAR_IMAGE_BASE_GRAAL_NAME}":"${LVAR_IMAGE_BASE_GRAAL_VER}" >/dev/null 2>&1 || {
	echo -e "${VAR_MYNAME}: base image '${LVAR_IMAGE_BASE_GRAAL_NAME}:${LVAR_IMAGE_BASE_GRAAL_VER}' not found" >/dev/stderr
	exit 1
}
docker image inspect "${LVAR_IMAGE_LIB_TS_FFMPEG_NAME}":"${LVAR_IMAGE_LIB_TS_FFMPEG_VER}" >/dev/null 2>&1 || {
	echo -e "${VAR_MYNAME}: TsLibFfmpeg image '${LVAR_IMAGE_LIB_TS_FFMPEG_NAME}:${LVAR_IMAGE_LIB_TS_FFMPEG_VER}' not found" >/dev/stderr
	exit 1
}

# ----------------------------------------------------------
# - App image ----------------------------------------------
# ----------------------------------------------------------

cd build-ctx-app || exit 1

LVAR_IMAGE_APP_NAME="app-ts_rtsp_server${LVAR_EXTRA_IMAGE_NAME_QUALIFIER}-${LVAR_DEBIAN_DIST}"
LVAR_IMAGE_APP_VER="${LOPT_APP_VERSION}"

docker image inspect "${LVAR_IMAGE_APP_NAME}":"latest" >/dev/null 2>&1 && {
	docker image rm "${LVAR_IMAGE_APP_NAME}:latest"
}
docker image inspect "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}" >/dev/null 2>&1 && {
	docker image rm "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}"
}

echo -e "\n${VAR_MYNAME}: ---------------------------------------------------------------------------"
echo -e "${VAR_MYNAME}: building app image '${LVAR_IMAGE_APP_NAME}:${LVAR_IMAGE_APP_VER}'...\n"

TMP_GRADLE_EXTRA_ARGS=""
test -z "${LOPT_LIB_XRTXP_COMMIT_OR_BRANCH}" && TMP_GRADLE_EXTRA_ARGS+=" -PuseCustomLibFromMavenCentral_xrtxp"
if [ "${LOPT_LIB_FFMPEG_FROM_MAVEN_LOCAL}" = "true" ]; then
	TMP_GRADLE_EXTRA_ARGS+=" -PuseCustomLibFromMavenLocal_ffmpeg"
elif [ -z "${LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH}" ]; then
	TMP_GRADLE_EXTRA_ARGS+=" -PuseCustomLibFromMavenCentral_ffmpeg"
fi
test "${LOPT_DO_CUSTOM_BUILD_JAVACPP_FFMPEG}" = "true" && TMP_GRADLE_EXTRA_ARGS+=" -PuseCustomBuiltJavaCppAndFfmpeg"
test "${LOPT_DO_RKMPP}" = "true" && TMP_GRADLE_EXTRA_ARGS+=" -PwithRkmpp"

docker build \
	-t "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}" \
	--build-arg CF_GRAAL_IMAGE="${LVAR_IMAGE_BASE_GRAAL_NAME}:${LVAR_IMAGE_BASE_GRAAL_VER}" \
	--build-arg CF_GRAAL_ARCH="${LVAR_GRAAL_ARCH}" \
	--build-arg CF_TSLIBFFMPEG_IMAGE="${LVAR_IMAGE_LIB_TS_FFMPEG_NAME}:${LVAR_IMAGE_LIB_TS_FFMPEG_VER}" \
	--build-arg CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT="${LOPT_LIB_XRTXP_COMMIT_OR_BRANCH}" \
	--build-arg CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT="${LOPT_LIB_FFMPEG_COMMIT_OR_BRANCH}" \
	--build-arg CF_APP_GIT_BRANCH_OR_COMMIT="${LOPT_APP_COMMIT_OR_BRANCH}" \
	--build-arg CF_GRADLE_EXTRA_ARGS="${TMP_GRADLE_EXTRA_ARGS}" \
	. \
	|| exit 1

docker tag "${LVAR_IMAGE_APP_NAME}":"${LVAR_IMAGE_APP_VER}" "${LVAR_IMAGE_APP_NAME}":latest
