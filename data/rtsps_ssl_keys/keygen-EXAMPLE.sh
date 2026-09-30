#!/usr/bin/env bash

#
# Generate a simple RSA Private Key and Certificate
#
# by TS, Sep 2026
#

VAR_MYNAME="$(basename "$0")"

# -------------------------------------------------------------------------

LCFG_SERVER_HOST="YOUR_HOSTNAME"  # put your hostname here

# ===== Certificate identity =====
LCFG_CERTID_C="DE"  # put your country here
LCFG_CERTID_ST="nowhere"  # put your street here
LCFG_CERTID_L="Your city"  # put your city here
LCFG_CERTID_O="Your org"  # put your organization here
LCFG_CERTID_OU="IT"  # put your organization unit here
LCFG_CERTID_CN="${LCFG_SERVER_HOST}"
LCFG_CERTID_EMAIL="admin@example.local"  # put your email here

# ===== Subject Alternative Names =====
LCFG_SAN_HOSTNAME="${LCFG_SERVER_HOST}"
LCFG_SAN_IP="192.168.1.1"  # put your IP address here

# -------------------------------------------------------------------------

if [ -z "${LCFG_SERVER_HOST}" ]; then
	echo "${VAR_MYNAME}: Empty hostname" >/dev/stderr
	exit 1
fi
if [ -z "${LCFG_SAN_IP}" ]; then
	echo "${VAR_MYNAME}: Empty IP address" >/dev/stderr
	exit 1
fi

# -------------------------------------------------------------------------

LTMP_FN_CA_CRT="${LCFG_SERVER_HOST}-ca.crt"
LTMP_FN_CA_SRL="${LCFG_SERVER_HOST}-ca.srl"
LTMP_FN_CA_PRIVKEY="${LCFG_SERVER_HOST}-ca-private.key"
LTMP_FN_SERVER_CRT="${LCFG_SERVER_HOST}-server.crt"
LTMP_FN_SERVER_PRIVKEY="${LCFG_SERVER_HOST}-server-private.key"

test -f "${LTMP_FN_SERVER_PRIVKEY}" && rm "${LTMP_FN_SERVER_PRIVKEY}"
test -f "${LTMP_FN_SERVER_CRT}" && rm "${LTMP_FN_SERVER_CRT}"
test -f "${LTMP_FN_CA_PRIVKEY}" && rm "${LTMP_FN_CA_PRIVKEY}"
test -f "${LTMP_FN_CA_CRT}" && rm "${LTMP_FN_CA_CRT}"
test -f "${LTMP_FN_CA_SRL}" && rm "${LTMP_FN_CA_SRL}"
test -f temp-server-internal.csr && rm temp-server-internal.csr
test -f temp-params-server.cnf && rm temp-params-server.cnf
test -f temp-params-ca.cnf && rm temp-params-ca.cnf
test -f temp-san-internal.cnf && rm temp-san-internal.cnf

# -------------------------------------------------------------------------

{
	echo "[ req ]"
	echo "prompt = no"
	echo "distinguished_name = dn"
	echo "req_extensions = v3_req"
	echo "[ dn ]"
	echo "C = ${LCFG_CERTID_C}"
	echo "ST = ${LCFG_CERTID_ST}"
	echo "L = ${LCFG_CERTID_L}"
	echo "O = ${LCFG_CERTID_O}"
	echo "OU = ${LCFG_CERTID_OU}"
	echo "CN = ${LCFG_CERTID_CN}"
	echo "emailAddress = ${LCFG_CERTID_EMAIL}"
	echo "[ v3_ca ]"
	echo "basicConstraints = critical,CA:TRUE"
	echo "keyUsage = critical,keyCertSign,cRLSign"
	echo "[ v3_server ]"
	echo "basicConstraints = CA:FALSE"
	echo "keyUsage = digitalSignature,keyEncipherment"
	echo "extendedKeyUsage = serverAuth"
	echo "subjectAltName = @alt_names"
	echo "[ v3_req ]"
	echo "subjectAltName = @alt_names"
	echo "[ alt_names ]"
	echo "DNS.1 = ${LCFG_SAN_HOSTNAME}"
	#echo "DNS.2 = ..."
	echo "IP.1 = ${LCFG_SAN_IP}"
} > temp-params-server.cnf

{
	echo "[ req ]"
	echo "prompt = no"
	echo "distinguished_name = dn"
	echo "req_extensions = v3_req"
	echo "[ dn ]"
	echo "C = ${LCFG_CERTID_C}"
	echo "ST = ${LCFG_CERTID_ST}"
	echo "L = ${LCFG_CERTID_L}"
	echo "O = ${LCFG_CERTID_O}"
	echo "OU = ${LCFG_CERTID_OU}"
	echo "CN = CA for ${LCFG_CERTID_CN}"
	echo "emailAddress = ${LCFG_CERTID_EMAIL}"
	echo "[ v3_ca ]"
	echo "basicConstraints = critical,CA:TRUE"
	echo "keyUsage = critical,keyCertSign,cRLSign"
	echo "[ v3_server ]"
	echo "basicConstraints = CA:FALSE"
	echo "keyUsage = digitalSignature,keyEncipherment"
	echo "extendedKeyUsage = serverAuth"
	echo "subjectAltName = @alt_names"
	echo "[ v3_req ]"
	echo "subjectAltName = @alt_names"
	echo "[ alt_names ]"
	echo "DNS.1 = ${LCFG_SAN_HOSTNAME}"
	#echo "DNS.2 = ..."
	echo "IP.1 = ${LCFG_SAN_IP}"
} > temp-params-ca.cnf

# -------------------------------------------------------------------------

# Create Private Key and Public Certificate
echo "${VAR_MYNAME}: -------------------------------------------------"
echo "${VAR_MYNAME}: Creating Private Key and Public Certificate..."
echo "${VAR_MYNAME}: -------------------------------------------------"
openssl req -x509 -newkey rsa:3072 -nodes \
	-keyout "${LTMP_FN_SERVER_PRIVKEY}" \
	-out "${LTMP_FN_SERVER_CRT}" \
	-days 36500 \
	-extensions v3_server -config temp-params-server.cnf \
	|| exit 1

# Create a CA Private Key
echo "${VAR_MYNAME}: -------------------------------------------------"
echo "${VAR_MYNAME}: Creating a CA Private Key..."
echo "${VAR_MYNAME}: -------------------------------------------------"
openssl genrsa -out "${LTMP_FN_CA_PRIVKEY}" 4096 || exit 1

# Create the CA Certificate (self-signed)
echo "${VAR_MYNAME}: -------------------------------------------------"
echo "${VAR_MYNAME}: Creating the CA Certificate (self-signed)..."
echo "${VAR_MYNAME}: -------------------------------------------------"
openssl req -x509 -new -nodes \
	-key "${LTMP_FN_CA_PRIVKEY}" \
	-sha256 -days 3650 \
	-out "${LTMP_FN_CA_CRT}" \
	-config temp-params-ca.cnf -extensions v3_ca \
	|| exit 1

# Create a Cerver Certificate Signing Request (CSR)
#   Common Name must match the hostname used in the RTSP URL
echo "${VAR_MYNAME}: -------------------------------------------------"
echo "${VAR_MYNAME}: Creating a Cerver Certificate Signing Request..."
echo "${VAR_MYNAME}: -------------------------------------------------"
openssl req -new \
	-key "${LTMP_FN_SERVER_PRIVKEY}" \
	-out temp-server-internal.csr \
	-config temp-params-server.cnf \
	|| exit 1

# Create a Subject Alternative Name (SAN) config
{
	echo "subjectAltName = @alt_names"
	echo ""
	echo "[alt_names]"
	echo "DNS.1 = ${LCFG_SAN_HOSTNAME}"
	#echo "DNS.2 = ..."
	echo "IP.1  = ${LCFG_SAN_IP}"
} > temp-san-internal.cnf

# Sign the Server Certificate with the CA
echo "${VAR_MYNAME}: -------------------------------------------------"
echo "${VAR_MYNAME}: Signing the Server Certificate with the CA..."
echo "${VAR_MYNAME}: -------------------------------------------------"
openssl x509 -req \
	-in temp-server-internal.csr \
	-CA "${LTMP_FN_CA_CRT}" \
	-CAkey "${LTMP_FN_CA_PRIVKEY}" \
	-CAcreateserial \
	-out "${LTMP_FN_SERVER_CRT}" \
	-extfile temp-san-internal.cnf \
	-days 3650 \
	-sha256 \
	|| exit 1

rm temp-san-internal.cnf temp-server-internal.csr temp-params-server.cnf temp-params-ca.cnf
