#!/usr/bin/env bash

#
# Generate the 'Reachability Metadata' for building the native image using GraalVM
#
# Run this script in one terminal and then run
#   './agent_test-localhost.sh'
# in another terminal.
#
# by TS, Sep 2026
#

VAR_MYNAME="$(basename "$0")"

# ----------------------------------------------------------

_getCpuArch() {
	case "$(uname -m)" in
		x86_64*)
			echo -n ""
			if [ "$1" = "debian_dist" ]; then
				echo -n "amd64"
			elif [ "$1" = "graal" ]; then
				echo -n "x64"
			else
				echo "${VAR_MYNAME}: Error: invalid arg '$1'" >/dev/stderr
				return 1
			fi
			;;
		aarch64*)
			if [ "$1" = "debian_dist" ]; then
				echo -n "arm64"
			elif [ "$1" = "graal" ]; then
				echo -n "aarch64"
			else
				echo "${VAR_MYNAME}: Error: invalid arg '$1'" >/dev/stderr
				return 1
			fi
			;;
		armv7*)
			if [ "$1" = "debian_dist" ]; then
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

_getOsType() {
	case "${OSTYPE}" in
		linux*) echo -n "linux";;
		darwin*) echo -n "macos";;
		*)
			echo "${VAR_MYNAME}: Error: Unknown OSTYPE '${OSTYPE}'" >/dev/stderr
			return 1
			;;
	esac
	return 0
}

_getOsType >/dev/null || exit 1

# ----------------------------------------------------------

LVAR_OS="$(_getOsType)"

LVAR_GRAAL_ARCH="$(_getCpuArch graal)"

# ----

LCFG_JAVA_HOME="/opt/graalvm-jdk-25.0.4-${LVAR_OS}_${LVAR_GRAAL_ARCH}"

LCFG_FFMPEG_VERSION="8.1.2"

# ----------------------------------------------------------

if [ ! -d "src" ]; then
	cd ..
	if [ ! -d "src" ]; then
		echo "${VAR_MYNAME}: Could not find 'src/'" >/dev/stderr
		exit 1
	fi
fi

if [ ! -d "${LCFG_JAVA_HOME}" ]; then
	echo "${VAR_MYNAME}: Could not find GraalVM home directory '${LCFG_JAVA_HOME}'" >/dev/stderr
	exit 1
fi

#
export JAVA_HOME="${LCFG_JAVA_HOME}"

export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
./gradlew clean

echo "-----------------------------------------------------"
echo "-----------------------------------------------------"

export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
./gradlew -PFFMPEG_VERSION=${LCFG_FFMPEG_VERSION} -Pagent collectNativeImageMetadata
