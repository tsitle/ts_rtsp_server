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

LCFG_DEBIAN_DIST="$(_getCpuArch debian_dist)"

LCFG_IMAGE_NAME="app-ts_rtsp_server-${LCFG_DEBIAN_DIST}"

docker run \
	-it --rm \
	--name app-ts_rtsp_server \
	-e "USER_ID=$(id -u)" \
	-e "GROUP_ID=$(id -g)" \
	-e "TZ=America/Bogota" \
	--entrypoint "/bin/sh" \
	"${LCFG_IMAGE_NAME}:latest"
