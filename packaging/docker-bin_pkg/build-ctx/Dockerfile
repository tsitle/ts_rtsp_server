# We use an slightly outdated Debian version here to have some backward compatibility.
# The current Debian's image uses Glibc 2.38, which is not compatible e.g. with Ubuntu 22.04 (which allows for <= 2.35)
FROM debian:12 AS ffmpeg-build

# Add GraalVM
ARG CF_GRAAL_ARCH=-unknown-

RUN \
	apt update \
	&& apt install --no-install-recommends -y \
		ca-certificates \
		curl

RUN \
	curl -LO https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-${CF_GRAAL_ARCH}_bin.tar.gz \
	&& tar -xf graalvm-jdk-25_linux-${CF_GRAAL_ARCH}_bin.tar.gz -C /opt

ENV PATH="${PATH}:/opt/graalvm-jdk-25.0.4+7.1/bin"
ENV JAVA_HOME="/opt/graalvm-jdk-25.0.4+7.1"

RUN \
	java --version

# ----------------------------------------------------

# Install other dependencies
RUN \
	apt install --no-install-recommends -y \
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
		maven

# ----------------------------------------------------
# Build FFmpeg libraries
# ----------------------------------------------------

WORKDIR /build

ARG CF_FFMPEG_VERSION=-unknown-

#ADD files/ffmpeg-${CF_FFMPEG_VERSION}.tar.xz /build/

RUN \
	curl -LO https://ffmpeg.org/releases/ffmpeg-${CF_FFMPEG_VERSION}.tar.xz \
	&& tar -xf ffmpeg-${CF_FFMPEG_VERSION}.tar.xz \
	&& rm ffmpeg-${CF_FFMPEG_VERSION}.tar.xz

RUN \
	cd ffmpeg-${CF_FFMPEG_VERSION} \
	&& ./configure \
		--prefix=/opt/ffmpeg \
		--disable-static \
		--enable-shared \
		--disable-programs \
		--disable-doc \
		--disable-debug \
	&& make -j$(getconf _NPROCESSORS_ONLN) \
	&& make install

# ----------------------------------------------------
# Build JavaCPP
# ----------------------------------------------------

ARG CF_JAVACPP_VERSION=-unknown-
ARG CF_JAVACPP_ARCH=-unknown-

RUN \
	git clone https://github.com/bytedeco/javacpp-presets.git

#ADD files/javacpp-presets-source-${CF_JAVACPP_VERSION}.tgz /build/
#RUN \
#	cd javacpp-presets \
#	&& git config --global --add safe.directory /build/javacpp-presets

RUN \
	cd javacpp-presets \
	&& git checkout -b customtemp ${CF_JAVACPP_VERSION}

RUN \
	cd javacpp-presets/ffmpeg \
	&& mkdir -p cppbuild/linux-${CF_JAVACPP_ARCH} \
	&& ln -s /opt/ffmpeg/include cppbuild/linux-${CF_JAVACPP_ARCH}/include \
	&& ln -s /opt/ffmpeg/lib cppbuild/linux-${CF_JAVACPP_ARCH}/lib

RUN \
	cd javacpp-presets \
	&& mvn install \
		--projects ffmpeg \
		-Djavacpp.cppbuild.skip=true \
		-Djavacpp.platform=linux-${CF_JAVACPP_ARCH}

RUN \
	ln -s javacpp-${CF_JAVACPP_VERSION}.jar \
		/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.jar \
	&& ln -s javacpp-${CF_JAVACPP_VERSION}.jar.sha1 \
		/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.jar.sha1 \
	&& ln -s javacpp-${CF_JAVACPP_VERSION}.pom \
		/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.pom \
	&& ln -s javacpp-${CF_JAVACPP_VERSION}.pom.sha1 \
		/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.pom.sha1

# ----------------------------------------------------
# Create symlinks for FFmpeg and JavaCPP shared libraries
# ----------------------------------------------------

# Copy custom JavaCPP build to /opt/javacpp-ffmpeg/
RUN \
	cp -r javacpp-presets/ffmpeg/target/native/org/bytedeco/ffmpeg/linux-${CF_JAVACPP_ARCH} /opt/javacpp-ffmpeg

# Create symlinks to custom FFmpeg and JavaCPP shared libraries
RUN \
	TMP_TARGET_DIR="/usr/local/lib" \
	&& for FN in /opt/ffmpeg/lib/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f ${TMP_TARGET_DIR}/$(basename "${FN}") || \
			ln -s ${FN} ${TMP_TARGET_DIR}/; \
		done \
	&& for FN in /opt/javacpp-ffmpeg/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f ${TMP_TARGET_DIR}/$(basename "${FN}") || \
			ln -s ${FN} ${TMP_TARGET_DIR}/; \
		done \
	&& ldconfig

# ----------------------------------------------------
# Compile the Java app
# ----------------------------------------------------

ARG CF_APP_GIT_BRANCH_OR_COMMIT=-none-

#
RUN \
	apt install --no-install-recommends -y \
		zlib1g-dev

#
RUN \
	git clone https://github.com/tsitle/ts_rtsp_server.git ts_rtsp_server-src \
	&& cd ts_rtsp_server-src \
	&& git checkout "${CF_APP_GIT_BRANCH_OR_COMMIT}"

# Build native image
RUN \
	cd ts_rtsp_server-src \
	&& cp -r src/main/resources/META-INF/native-image-linux-ff${CF_FFMPEG_VERSION}-${CF_GRAAL_ARCH} src/main/resources/META-INF/native-image \
	&& export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED" \
	&& ./gradlew nativeCompile -PFFMPEG_VERSION=${CF_FFMPEG_VERSION}

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
	cd ts_rtsp_server-src/data/rtsps_ssl_keys \
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

ENV APP_ROOT_PATH=/opt/ts_rtsp_server

RUN \
	mkdir -p "${APP_ROOT_PATH}/libs"

# ----------------------------------------------------
# Add JRE, custom FFmpeg and custom JavaCPP shared libraries
# ----------------------------------------------------

ENV TMP_BUILD_DIR_JAVA_HOME="/opt/graalvm-jdk-25.0.4+7.1"
ENV TMP_BUILD_DIR_FFMPEG="/opt/ffmpeg"
ENV TMP_BUILD_DIR_JAVACPP="/build/javacpp-presets/ffmpeg/target/native/org/bytedeco/ffmpeg/linux-${CF_JAVACPP_ARCH}"

COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt.so ${APP_ROOT_PATH}/libs/jre/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt_headless.so ${APP_ROOT_PATH}/libs/jre/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVA_HOME}/lib/libawt_xawt.so ${APP_ROOT_PATH}/libs/jre/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVA_HOME}/lib/libjava.so ${APP_ROOT_PATH}/libs/jre/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVA_HOME}/lib/libjavajpeg.so ${APP_ROOT_PATH}/libs/jre/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVA_HOME}/lib/server/libjvm.so ${APP_ROOT_PATH}/libs/jre/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_FFMPEG}/lib/ ${APP_ROOT_PATH}/libs/ffmpeg/
COPY --from=ffmpeg-build ${TMP_BUILD_DIR_JAVACPP}/ ${APP_ROOT_PATH}/libs/javacpp-ffmpeg/

# Create symlinks to custom FFmpeg and JavaCPP shared libraries
RUN \
	TMP_TARGET_DIR="${APP_ROOT_PATH}/bin" \
	&& mkdir "${TMP_TARGET_DIR}" \
	# file permissions for JRE libs \
	&& chmod a+x ${APP_ROOT_PATH}/libs/jre/libjava.so ${APP_ROOT_PATH}/libs/jre/libjvm.so \
	# symlinks for JRE libs \
	&& for FN in ${APP_ROOT_PATH}/libs/jre/*; do \
		test -f ${TMP_TARGET_DIR}/$(basename "${FN}") || \
			ln -s "../libs/jre/$(basename "${FN}")" ${TMP_TARGET_DIR}/; \
		done \
	# symlinks for FFmpeg libs \
	&& rm -r ${APP_ROOT_PATH}/libs/ffmpeg/pkgconfig \
	&& for FN in ${APP_ROOT_PATH}/libs/ffmpeg/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f ${TMP_TARGET_DIR}/$(basename "${FN}") || \
			ln -s "../libs/ffmpeg/$(basename "${FN}")" ${TMP_TARGET_DIR}/; \
		done \
	# delete copies of FFmpeg libs in JavaCPP directory \
	&& for FN in ${APP_ROOT_PATH}/libs/javacpp-ffmpeg/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f "${APP_ROOT_PATH}/libs/ffmpeg/$(basename "${FN}")" && rm "${FN}" || echo -y; \
		done \
	# symlinks for JavaCPP libs \
	&& for FN in ${APP_ROOT_PATH}/libs/javacpp-ffmpeg/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f ${TMP_TARGET_DIR}/$(basename "${FN}") || \
			ln -s "../libs/javacpp-ffmpeg/$(basename "${FN}")" ${TMP_TARGET_DIR}/; \
		done

# ----------------------------------------------------

#
COPY --from=ffmpeg-build /build/ts_rtsp_server-src/config/ ${APP_ROOT_PATH}/config/
COPY --from=ffmpeg-build /build/ts_rtsp_server-src/data/ ${APP_ROOT_PATH}/data/
COPY --from=ffmpeg-build /build/app-binary/ts_rtsp_server ${APP_ROOT_PATH}/bin/
COPY files/ts_rtsp_server-wrapper.sh ${APP_ROOT_PATH}/ts_rtsp_server.sh

RUN \
	mkdir ${APP_ROOT_PATH}/logs \
	&& find ${APP_ROOT_PATH} -name ".gitkeep" -exec rm '{}' \; \
	&& chmod +x ${APP_ROOT_PATH}/ts_rtsp_server.sh \
	&& chmod +x ${APP_ROOT_PATH}/bin/ts_rtsp_server \
	&& ln -s ${APP_ROOT_PATH}/ts_rtsp_server.sh /usr/local/bin/ts_rtsp_server

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

WORKDIR ${APP_ROOT_PATH}

VOLUME ${APP_ROOT_PATH}/data
VOLUME ${APP_ROOT_PATH}/config
VOLUME ${APP_ROOT_PATH}/logs

# ----------------------------------------------------

COPY files/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
