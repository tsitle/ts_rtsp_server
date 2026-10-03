#!/usr/bin/env bash

#
# Exercise the RTSP server's functionality to help generating
# the 'Reachability Metadata' for building the native image using GraalVM.
#
# Run
#   './prepare_for_native_build.sh'
# in one terminal and then run this script in another terminal.
#
# by TS, Sep 2026
#

VAR_MYNAME="$(basename "$0")"

# ----------------------------------------------------------

LCFG_HOST_LH_NOSSL="127.0.0.1:1554"
LCFG_HOST_LH_SSL="127.0.0.1:1322"

LCFG_UADM_USER="admin"
LCFG_UADM_PASS="ABCDEFGH"

# ----------------------------------------------------------

command -v ffplay >/dev/null 2>&1 || {
	echo "${VAR_MYNAME}: could not find 'ffplay' executable" >/dev/stderr
	exit 1
}

# ----------------------------------------------------------

LCFG_STREAMS=""

# rawfile
#LCFG_STREAMS+=" sample-raw-aac.stream"
#LCFG_STREAMS+=" sample-raw-pcma.stream"
#LCFG_STREAMS+=" sample-raw-pcmu.stream"
##LCFG_STREAMS+=" sample-raw-lpcm08u.stream"  # not supported over RTSP by FFmpeg
#LCFG_STREAMS+=" sample-raw-lpcm16s_le.stream"
#LCFG_STREAMS+=" sample-raw-lpcm16s_be.stream"
#LCFG_STREAMS+=" sample-raw-mp3.stream"
LCFG_STREAMS+=" sample-raw-h265_and_aac.stream"
#LCFG_STREAMS+=" sample-raw-mjpeg_and_ac3.stream"

# DMX filecontainer
LCFG_STREAMS+=" sample-mkv_with_h264_and_aac.stream"
#LCFG_STREAMS+=" sample-mov_with_h264_and_lpcm16s.stream"
LCFG_STREAMS+=" sample-mp4_with_h265_and_ac3.stream"
LCFG_STREAMS+=" sample-mkv_with_mjpeg_and_mp3.stream"
LCFG_STREAMS+=" sample-webm_with_vp8_and_opus.stream"
#LCFG_STREAMS+=" sample-aac.stream"
#LCFG_STREAMS+=" sample-ac3.stream"
#LCFG_STREAMS+=" sample-mp2.stream"
#LCFG_STREAMS+=" sample-mp3.stream"
#LCFG_STREAMS+=" sample-opus.stream"
#LCFG_STREAMS+=" sample-wav_with_pcma.stream"
#LCFG_STREAMS+=" sample-wav_with_pcmu.stream"
##LCFG_STREAMS+=" sample-wav_with_lpcm08u.stream"  # not supported over RTSP by FFmpeg
#LCFG_STREAMS+=" sample-wav_with_lpcm16s.stream"

# DMX jukebox
LCFG_STREAMS+=" sample-jukebox.stream"

# DMX rtsp-input
#LCFG_STREAMS+=" sample-garden_camera.stream"  # RTSP input stream needs to be configured first

# ----------------------------------------------------------

# params: $1=stream
call_ffmpeg() {
	local LTMP_URL="rtsps://${LCFG_UADM_USER}:${LCFG_UADM_PASS}@${LCFG_HOST_LH_SSL}/${1}?srtp=1"
	#local LTMP_URL="rtsp://${LCFG_UADM_USER}:${LCFG_UADM_PASS}@${LCFG_HOST_LH_NOSSL}/${1}?srtp=1"

	local LTMP_TIMEOUT=6
	echo -n "${1}" | grep -q "\-jukebox" && LTMP_TIMEOUT=30

	# works - w/ SRTP and RTSPS
	timeout ${LTMP_TIMEOUT} \
		ffplay \
			-hide_banner -t $(( LTMP_TIMEOUT - 1 )) -autoexit \
			-protocol_whitelist "udp,tcp,rtsp,rtp,srtp,rtcp,srtcp,tls" \
			-fflags nobuffer -flags low_delay -framedrop \
			"${LTMP_URL}"
}

for TMP_STREAM in ${LCFG_STREAMS}; do
	call_ffmpeg "${TMP_STREAM}"
done

# this will gracefully shutdown the server if it was launched with '--agent-test'
pkill --signal USR1 -f "ts_rtsp_server/build/native"
