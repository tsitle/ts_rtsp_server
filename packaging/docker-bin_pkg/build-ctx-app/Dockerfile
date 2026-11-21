ARG CF_TSLIBFFMPEG_IMAGE=none
ARG CF_GRAAL_IMAGE=none

FROM ${CF_TSLIBFFMPEG_IMAGE} AS lib-ts-ffmpeg-base

FROM ${CF_GRAAL_IMAGE} AS build-base

# ----------------------------------------------------
# Install dependencies for GraalVM Native Image building
# ----------------------------------------------------

RUN \
	apt update \
	&& apt install --no-install-recommends -y \
		build-essential \
		yasm \
		nasm \
		pkgconf \
		autoconf \
		automake \
		libtool \
		git \
		tar \
		xz-utils \
		maven \
		zlib1g-dev

# Install en.utf-8 so we can use UTF-8 without restrictions (see 'java.nio.file.Path') in the native image compiled with GraalVM
RUN \
	apt install --no-install-recommends -y \
		locales \
	&& sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
	&& locale-gen

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# ----------------------------------------------------
# Copy the local Maven repository directory for the custom-built version of TsLibFfmpeg (and JavaCPP and FFmpeg)
# ----------------------------------------------------

RUN \
	mkdir -p /root/.m2

COPY --from=lib-ts-ffmpeg-base /root/.m2/repository /root/.m2/repository

# ----------------------------------------------------
# Compile the Java app
# ----------------------------------------------------

ARG CF_GRAAL_ARCH=-unknown-
ARG CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT=-none-
ARG CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT=-none-
ARG CF_APP_GIT_BRANCH_OR_COMMIT=-none-
ARG CF_GRADLE_EXTRA_ARGS=-none-

# Clone the 'TS Lib XRTXP' repository and build the library
RUN \
	if [ -n "${CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT}" ]; then \
		mkdir libs \
		&& cd libs \
		&& git clone https://github.com/tsitle/ts_java_lib_xrtxp.git lib-rtsp_xrtxp \
		&& cd lib-rtsp_xrtxp \
		&& git checkout "${CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT}" \
		&& ./gradlew jar --no-daemon \
		|| exit 1; \
	else echo -n; fi

# Copy the pre-built 'TS Lib FFmpeg' library
#COPY --from=lib-ts-ffmpeg-base /build/lib/ts_lib_ffmpeg-*.jar libs/lib-rtsp_ffmpeg/build/libs/
# --> the pre-built 'TS Lib FFmpeg' library is now in the local Maven repository

# Clone the 'TS Lib FFmpeg' repository and build the library
RUN \
	if [ -n "${CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT}" ]; then \
		# delete the pre-built 'TS Lib FFmpeg' library \
		test -d libs/lib-rtsp_ffmpeg && rm -r libs/lib-rtsp_ffmpeg || echo -n; \
		# build the 'TS Lib FFmpeg' library \
		test -d libs || mkdir libs \
		&& cd libs \
		&& git clone https://github.com/tsitle/ts_java_lib_ffmpeg.git lib-rtsp_ffmpeg \
		&& cd lib-rtsp_ffmpeg \
		&& git checkout "${CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT}" \
		&& ./gradlew shadowJar --no-daemon ${CF_GRADLE_EXTRA_ARGS} \
		|| exit 1; \
	fi

# Clone the app repository
RUN \
	git clone https://github.com/tsitle/ts_rtsp_server.git ts_rtsp_server-src \
	&& cd ts_rtsp_server-src \
	&& git checkout "${CF_APP_GIT_BRANCH_OR_COMMIT}"

# Build native image
RUN \
	cd ts_rtsp_server-src \
	&& cp -r \
		"src/main/resources/META-INF/native-image-linux-${CF_GRAAL_ARCH}" \
		src/main/resources/META-INF/native-image \
	&& export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED" \
	&& ./gradlew nativeCompile --no-daemon ${CF_GRADLE_EXTRA_ARGS}

RUN \
	mkdir app-binary \
	&& cp ts_rtsp_server-src/build/native/nativeCompile/ts_rtsp_server app-binary/

# ----------------------------------------------------
# Generate dummy SSL certificate and key
# ----------------------------------------------------

RUN \
	apt install --no-install-recommends -y \
		openssl

RUN \
	cd ts_rtsp_server-src/data/mq_server_ssl_certs \
	&& ./keygen-EXAMPLE.sh \
	&& cd ../rtsps_ssl_keys \
	&& ./keygen-EXAMPLE.sh


# ----------------------------------------------------------------------------------------------------------------------
# ----------------------------------------------------------------------------------------------------------------------
# ----------------------------------------------------------------------------------------------------------------------
# ----------------------------------------------------------------------------------------------------------------------
# ---- Runtime stage ----
# We use an slightly outdated Debian version here to have some backward compatibility.
# The current Debian's image uses Glibc 2.38, which is not compatible e.g. with Ubuntu 22.04 (which allows for <= 2.35)
FROM debian:12

#
RUN \
	apt update \
	&& apt install --no-install-recommends -y \
		ca-certificates \
		sudo

# Install en.utf-8 so we can use UTF-8 without restrictions (see 'java.nio.file.Path') in the native image compiled with GraalVM
RUN \
	apt install --no-install-recommends -y \
		locales \
	&& sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
	&& locale-gen

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# ----------------------------------------------------
# Add JRE shared libraries
# ----------------------------------------------------

ENV APP_ROOT_PATH=/opt/ts_rtsp_server

RUN \
	mkdir -p "${APP_ROOT_PATH}/libs"

ENV TMP_BUILD_DIR_JAVA_HOME="/opt/graalvm-jdk-25.0.4+7.1"

COPY --from=build-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=build-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt_headless.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=build-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt_xawt.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=build-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libjava.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=build-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libjavajpeg.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=build-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/server/libjvm.so" "${APP_ROOT_PATH}/libs/jre/"

# Create symlinks to JRE shared libraries
RUN \
	TMP_TARGET_DIR="${APP_ROOT_PATH}/bin" \
	&& mkdir "${TMP_TARGET_DIR}" \
	# file permissions for JRE libs \
	&& chmod a+x "${APP_ROOT_PATH}/libs/jre/libjava.so" "${APP_ROOT_PATH}/libs/jre/libjvm.so" \
	# symlinks for JRE libs \
	&& for FN in "${APP_ROOT_PATH}/libs/jre"/*; do \
		test -f "${TMP_TARGET_DIR}/$(basename "${FN}")" || \
			ln -s "../libs/jre/$(basename "${FN}")" "${TMP_TARGET_DIR}/"; \
		done

# ----------------------------------------------------

#
COPY --from=build-base /build/ts_rtsp_server-src/config/ "${APP_ROOT_PATH}/config/"
COPY --from=build-base /build/ts_rtsp_server-src/data/ "${APP_ROOT_PATH}/data/"
COPY --from=build-base /build/app-binary/ts_rtsp_server "${APP_ROOT_PATH}/bin/"
COPY files/ts_rtsp_server-wrapper.sh "${APP_ROOT_PATH}/ts_rtsp_server.sh"

RUN \
	mkdir "${APP_ROOT_PATH}/logs" \
	&& find "${APP_ROOT_PATH}" -name ".gitkeep" -exec rm '{}' \; \
	&& chmod +x "${APP_ROOT_PATH}/ts_rtsp_server.sh" \
	&& chmod +x "${APP_ROOT_PATH}/bin/ts_rtsp_server" \
	&& ln -s "${APP_ROOT_PATH}/ts_rtsp_server.sh" /usr/local/bin/ts_rtsp_server

# ----------------------------------------------------

RUN \
	rm -rf \
		/usr/share/man \
		/usr/share/doc \
		/usr/share/icons \
		/usr/share/poppler \
		/usr/share/mime \
		/var/lib/apt/lists/*

# ----------------------------------------------------

WORKDIR "${APP_ROOT_PATH}"

VOLUME "${APP_ROOT_PATH}/data"
VOLUME "${APP_ROOT_PATH}/config"
VOLUME "${APP_ROOT_PATH}/logs"

# ----------------------------------------------------

COPY files/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
