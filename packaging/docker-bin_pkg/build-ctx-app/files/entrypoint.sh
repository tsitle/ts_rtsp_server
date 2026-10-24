#!/usr/bin/env bash

#
# Entrypoint for Debian-based image
#
# by TS, Sep 2026
#

set -e

USER_ID="${USER_ID:-1000}"
GROUP_ID="${GROUP_ID:-1000}"

# Create group if it doesn't exist
if ! getent group "$GROUP_ID" >/dev/null 2>&1; then
	groupadd -g "$GROUP_ID" rtsp
else
	GROUP_NAME="$(getent group "$GROUP_ID" | cut -d: -f1)"
fi

# If the group was just created, use its name
GROUP_NAME="${GROUP_NAME:-rtsp}"

# Create user if it doesn't exist
if ! getent passwd "$USER_ID" >/dev/null 2>&1; then
	useradd \
		-m \
		-u "$USER_ID" \
		-g "$GROUP_NAME" \
		rtsp
else
	USER_NAME="$(getent passwd "$USER_ID" | cut -d: -f1)"
fi

USER_NAME="${USER_NAME:-rtsp}"

# ----------------------------------------------------

# set up timezone
TMP_TZ="${TZ:-Europe/Berlin}"
ln -snf "/usr/share/zoneinfo/${TMP_TZ}" /etc/localtime
echo "${TMP_TZ}" > /etc/timezone

# ----------------------------------------------------

mkdir -p /tmp/.javacpp-rtsp/cache
chown -R "${USER_NAME}:${GROUP_NAME}" \
	/tmp/.javacpp-rtsp/ \
	/opt/ts_rtsp_server/data \
	/opt/ts_rtsp_server/config \
	/opt/ts_rtsp_server/logs

# ----------------------------------------------------

exec sudo -u "$USER_NAME" /usr/local/bin/ts_rtsp_server "$@"
