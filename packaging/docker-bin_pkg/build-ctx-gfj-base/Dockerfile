# We use an slightly outdated Debian version here to have some backward compatibility.
# The current Debian's image uses Glibc 2.38, which is not compatible e.g. with Ubuntu 22.04 (which allows for <= 2.35)
FROM debian:12

# Add GraalVM
ARG CF_GRAAL_ARCH=-unknown-

#ADD "files/graalvm-jdk-25_linux-${CF_GRAAL_ARCH}_bin.tar.gz" /opt/

RUN \
	apt update \
	&& apt install --no-install-recommends -y \
		ca-certificates \
		curl

RUN \
	curl -LO "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-${CF_GRAAL_ARCH}_bin.tar.gz" \
	&& tar -xf "graalvm-jdk-25_linux-${CF_GRAAL_ARCH}_bin.tar.gz" -C /opt

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
# Build FFmpeg libraries
# ----------------------------------------------------

WORKDIR /build

ARG CF_FFMPEG_VERSION=-unknown-

#ADD "files/ffmpeg-${CF_FFMPEG_VERSION}.tar.xz" /build/

RUN \
	curl -LO "https://ffmpeg.org/releases/ffmpeg-${CF_FFMPEG_VERSION}.tar.xz" \
	&& tar -xf "ffmpeg-${CF_FFMPEG_VERSION}.tar.xz" \
	&& rm "ffmpeg-${CF_FFMPEG_VERSION}.tar.xz"

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

#ADD "files/javacpp-presets-source-${CF_JAVACPP_VERSION}.tgz" /build/
#RUN \
#	cd javacpp-presets \
#	&& git config --global --add safe.directory /build/javacpp-presets

RUN \
	cd javacpp-presets \
	&& git checkout -b customtemp "${CF_JAVACPP_VERSION}"

RUN \
	cd javacpp-presets/ffmpeg \
	&& mkdir -p "cppbuild/linux-${CF_JAVACPP_ARCH}" \
	&& ln -s /opt/ffmpeg/include "cppbuild/linux-${CF_JAVACPP_ARCH}/include" \
	&& ln -s /opt/ffmpeg/lib "cppbuild/linux-${CF_JAVACPP_ARCH}/lib"

RUN \
	cd javacpp-presets \
	&& mvn install \
		--projects ffmpeg \
		-Djavacpp.cppbuild.skip=true \
		-Djavacpp.platform=linux-${CF_JAVACPP_ARCH}

RUN \
	ln -s "javacpp-${CF_JAVACPP_VERSION}.jar" \
		"/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.jar" \
	&& ln -s "javacpp-${CF_JAVACPP_VERSION}.jar.sha1" \
		"/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.jar.sha1" \
	&& ln -s "javacpp-${CF_JAVACPP_VERSION}.pom" \
		"/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.pom" \
	&& ln -s "javacpp-${CF_JAVACPP_VERSION}.pom.sha1" \
		"/root/.m2/repository/org/bytedeco/javacpp/${CF_JAVACPP_VERSION}/javacpp-${CF_JAVACPP_VERSION}-linux-${CF_JAVACPP_ARCH}.pom.sha1"

# ----------------------------------------------------
# Create symlinks for FFmpeg and JavaCPP shared libraries
# ----------------------------------------------------

# Copy custom JavaCPP build to /opt/javacpp-ffmpeg/
RUN \
	cp -r "javacpp-presets/ffmpeg/target/native/org/bytedeco/ffmpeg/linux-${CF_JAVACPP_ARCH}" /opt/javacpp-ffmpeg

# Create symlinks to custom FFmpeg and JavaCPP shared libraries
RUN \
	TMP_TARGET_DIR="/usr/local/lib" \
	&& for FN in /opt/ffmpeg/lib/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f "${TMP_TARGET_DIR}/$(basename "${FN}")" || \
			ln -s "${FN}" "${TMP_TARGET_DIR}/"; \
		done \
	&& for FN in /opt/javacpp-ffmpeg/*; do \
		test -f "${FN}" -o -h "${FN}" || continue; \
		test -f "${TMP_TARGET_DIR}/$(basename "${FN}")" || \
			ln -s "${FN}" "${TMP_TARGET_DIR}/"; \
		done \
	&& ldconfig
