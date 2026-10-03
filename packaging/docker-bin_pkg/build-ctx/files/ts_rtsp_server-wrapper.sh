#!/usr/bin/env bash

#
# Wrapper for instantiating the mighty 'TS RTSP Server'
#
# by TS, Sep 2026
#

# ----------------------------------------------------------

# @param string $1 Path
# @param int $2 Recursion level
#
# @return string Absolute path
realpath_osx() {
	local TMP_RP_OSX_RES
	[[ ${1} = /* ]] && TMP_RP_OSX_RES="${1}" || TMP_RP_OSX_RES="${PWD}/${1#./}"

	if [ -h "${TMP_RP_OSX_RES}" ]; then
		TMP_RP_OSX_RES="$(readlink "${TMP_RP_OSX_RES}")"
		# possible infinite loop...
		local TMP_RP_OSX_RECLEV
		TMP_RP_OSX_RECLEV=${2}
		[ -z "${TMP_RP_OSX_RECLEV}" ] && TMP_RP_OSX_RECLEV=0
		TMP_RP_OSX_RECLEV=$(( TMP_RP_OSX_RECLEV + 1 ))
		if [ ${TMP_RP_OSX_RECLEV} -gt 20 ]; then
			# too much recursion
			TMP_RP_OSX_RES="--error--"
		else
			TMP_RP_OSX_RES="$(realpath_osx "${TMP_RP_OSX_RES}" ${TMP_RP_OSX_RECLEV})"
		fi
	fi
	echo "${TMP_RP_OSX_RES}"
}

# @param string $1 Path
#
# @return string Absolute path
realpath_poly() {
	case "${OSTYPE}" in
		linux*) realpath "${1}" ;;
		darwin*) realpath_osx "${1}" ;;
		*) echo "${VAR_MYNAME}: Error: Unknown OSTYPE '${OSTYPE}'" >/dev/stderr; echo -n "$1" ;;
	esac
}

# ----------------------------------------------------------

TMP_REALPATH="$(realpath_poly "$0")"
TMP_REALPATH="$(dirname "${TMP_REALPATH}")"

"${TMP_REALPATH}/bin/ts_rtsp_server" "$@"
