ARG CF_BASE_IMAGE=none

FROM ${CF_BASE_IMAGE} AS graalvm-ffmpeg-javacpp-base

# ----------------------------------------------------
# Compile the Java app
# ----------------------------------------------------

ARG CF_GRAAL_ARCH=-unknown-
ARG CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT=-none-
ARG CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT=-none-
ARG CF_USE_CUSTOM_LIBS_FROM_MAVEN=-invalidArg
ARG CF_APP_GIT_BRANCH_OR_COMMIT=-none-

# Clone the 'TS Lib XRTXP' repository and build the library
RUN \
	if [ "${CF_USE_CUSTOM_LIBS_FROM_MAVEN}" != "true" ]; then \
		mkdir libs \
		&& cd libs \
		&& git clone https://github.com/tsitle/ts_java_lib_xrtxp.git lib-rtsp_xrtxp \
		&& cd lib-rtsp_xrtxp \
		|| exit 1; \
		if [ -n "${CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT}" ]; then \
			git checkout "${CF_TS_LIB_XRTXP_GIT_BRANCH_OR_COMMIT}" || exit 1; \
		else echo -n; fi; \
		./gradlew jar \
		|| exit 1; \
	else echo -n; fi

# Clone the 'TS Lib FFmpeg' repository and build the library
RUN \
	if [ "${CF_USE_CUSTOM_LIBS_FROM_MAVEN}" != "true" ]; then \
		test -d libs || mkdir libs \
		&& cd libs \
		&& git clone https://github.com/tsitle/ts_java_lib_ffmpeg.git lib-rtsp_ffmpeg \
		&& cd lib-rtsp_ffmpeg \
		|| exit 1; \
		if [ -n "${CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT}" ]; then \
			git checkout "${CF_TS_LIB_FFMPEG_GIT_BRANCH_OR_COMMIT}" || exit 1; \
		else echo -n; fi; \
		./gradlew shadowJar \
		|| exit 1; \
	else echo -n; fi

# Clone the app repository
RUN \
	git clone https://github.com/tsitle/ts_rtsp_server.git ts_rtsp_server-src \
	&& cd ts_rtsp_server-src \
	&& git checkout "${CF_APP_GIT_BRANCH_OR_COMMIT}"

# Build native image
RUN \
	TMP_GRADLE_ARG_UCLFM="" \
	test "${CF_USE_CUSTOM_LIBS_FROM_MAVEN}" = "true" && TMP_GRADLE_ARG_UCLFM="-PuseCustomLibsFromMaven" || echo -n; \
	cd ts_rtsp_server-src \
	&& cp -r \
		"src/main/resources/META-INF/native-image-linux-${CF_GRAAL_ARCH}" \
		src/main/resources/META-INF/native-image \
	&& export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED" \
	&& ./gradlew nativeCompile ${TMP_GRADLE_ARG_UCLFM}

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
FROM debian

ARG CF_JAVACPP_ARCH=-unknown-

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

#
ENV APP_ROOT_PATH=/opt/ts_rtsp_server

RUN \
	mkdir -p "${APP_ROOT_PATH}/libs"

# ----------------------------------------------------
# Add JRE, custom FFmpeg and custom JavaCPP shared libraries
# ----------------------------------------------------

ENV TMP_BUILD_DIR_JAVA_HOME="/opt/graalvm-jdk-25.0.4+7.1"
ENV TMP_BUILD_DIR_FFMPEG="/opt/ffmpeg"
ENV TMP_BUILD_DIR_JAVACPP="/build/javacpp-presets/ffmpeg/target/native/org/bytedeco/ffmpeg/linux-${CF_JAVACPP_ARCH}"

COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt_headless.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt_xawt.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libjava.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/libjavajpeg.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVA_HOME}/lib/server/libjvm.so" "${APP_ROOT_PATH}/libs/jre/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_FFMPEG}/lib/" "${APP_ROOT_PATH}/libs/ffmpeg/"
COPY --from=graalvm-ffmpeg-javacpp-base "${TMP_BUILD_DIR_JAVACPP}/" "${APP_ROOT_PATH}/libs/javacpp-ffmpeg/"

# Create symlinks to custom FFmpeg and JavaCPP shared libraries
RUN \
	TMP_TARGET_DIR="${APP_ROOT_PATH}/bin" \
	&& mkdir "${TMP_TARGET_DIR}" \
	# file permissions for JRE libs \
	&& chmod a+x "${APP_ROOT_PATH}/libs/jre/libjava.so" "${APP_ROOT_PATH}/libs/jre/libjvm.so" \
	# symlinks for JRE libs \
	&& for FN in "${APP_ROOT_PATH}/libs/jre"/*; do \
		test -f "${TMP_TARGET_DIR}/$(basename "${FN}")" || \
			ln -s "../libs/jre/$(basename "${FN}")" "${TMP_TARGET_DIR}/"; \
		done \
	# symlinks for FFmpeg libs \
	&& rm -r "${APP_ROOT_PATH}/libs/ffmpeg/pkgconfig" \
	&& for FN in "${APP_ROOT_PATH}/libs/ffmpeg"/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f "${TMP_TARGET_DIR}/$(basename "${FN}")" || \
			ln -s "../libs/ffmpeg/$(basename "${FN}")" "${TMP_TARGET_DIR}/"; \
		done \
	# delete copies of FFmpeg libs in JavaCPP directory \
	&& for FN in "${APP_ROOT_PATH}/libs/javacpp-ffmpeg"/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f "${APP_ROOT_PATH}/libs/ffmpeg/$(basename "${FN}")" && rm "${FN}" || echo -y; \
		done \
	# symlinks for JavaCPP libs \
	&& for FN in "${APP_ROOT_PATH}/libs/javacpp-ffmpeg"/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f "${TMP_TARGET_DIR}/$(basename "${FN}")" || \
			ln -s "../libs/javacpp-ffmpeg/$(basename "${FN}")" "${TMP_TARGET_DIR}/"; \
		done

# ----------------------------------------------------

#
COPY --from=graalvm-ffmpeg-javacpp-base /build/ts_rtsp_server-src/config/ "${APP_ROOT_PATH}/config/"
COPY --from=graalvm-ffmpeg-javacpp-base /build/ts_rtsp_server-src/data/ "${APP_ROOT_PATH}/data/"
COPY --from=graalvm-ffmpeg-javacpp-base /build/app-binary/ts_rtsp_server "${APP_ROOT_PATH}/bin/"
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
