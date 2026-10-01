# Building a Native Image with GraalVM

## Preparing the Reachability Metadata

### Prerequisites

- Docker
- GraalVM 25 JDK
- FFplay (from the FFmpeg package)

### Generating the Reachability Metadata

If the 'reachability metadata' for the configured FFmpeg version and target system (e.g., Linux x64)  
already exists in `src/main/resources/META-INF/native-image-<TARGET_OS>-ff<FFMPEG_VERSION>-<TARGET_ARCH>` you can skip this step.

Otherwise, we need to run the Native Image Agent. The agent will generate and store the required 'reachability metadata':

```
$ cd packaging
$ ./prepare_for_native_build.sh
```

In another terminal, we need to exercise the application:

```
$ cd packaging
$ ./agent_test-localhost.sh
```

Once the 'agent test' has completed, the server application will automatically be shut down.

**Note:** the `prepare_for_native_build.sh` script expects the GraalVM JDK in a specific directory.  
It will not check `JAVA_HOME` for that. If your GraalVM JDK is located elsewhere, you need to adjust the script accordingly.

## Building a Binary Package

### Prerequisites

- Docker

### Building the Native Image

```
$ cd packaging/docker-bin_pkg
$ ./build_image.sh <APP_VERSION> <COMMIT_OR_BRANCH>

# e.g. ./build_image.sh 1.1.4 ce3991c3
```

### Extracting the Binary Package from the Docker Image

```
$ cd packaging/docker-bin_pkg
$ ./extract_app_package.sh
```

This will create a TAR ball in `distPre/`.
