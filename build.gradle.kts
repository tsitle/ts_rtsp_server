plugins {
	id("java")
	id("application")
	id("org.beryx.runtime") version "2.0.1"  // see https://badass-runtime-plugin.beryx.org/releases/latest/
	// this plugin will filter the JavaCPP and FFmpeg libraries such that only libs for the current CPU architecture will be included
	id("org.graalvm.buildtools.native") version "1.1.12"
	id("com.gradleup.shadow") version "9.6.1"  // for Fat JARs
}

group = "org.tsitle.rtsp_server"
version = project.findProperty("appVersion")!! as String

val propProjName = rootProject.name  // from 'settings.gradle.kts'

val confMainClass = "org.tsitle.rtsp_server.RtspServerApp"
val confAppInfoClass = "org.tsitle.rtsp_server.AppInfo"

// output directory for distribution files (launchers and installers)
val confDistPreOutputDir = "distPre"

val jvmMemHeapInit: String = "128m"
val jvmMemHeapMax: String = "128m"
val jvmMemMaxTotalAbs: String = "256m"
//val jvmMemMaxTotalPerc: String = "50"

// do agent test?
var tmpArgProvider = providers.gradleProperty("agent")  // returns null if not provided
val doAgentTest = tmpArgProvider.isPresent

// do 'private' agent test? (uses a config file that is not included in the Git repository)
tmpArgProvider = providers.gradleProperty("usePrivConf")  // returns null if not provided
val usePrivConf = tmpArgProvider.isPresent

// use custom libraries from Maven repository?
tmpArgProvider = providers.gradleProperty("useCustomLibsFromMaven")  // returns null if not provided
val doUseCustomLibsFromMaven: Boolean = tmpArgProvider.isPresent

//
val depsVersionTsLibXrtxp = project.findProperty("depsVersionTsLibXrtxp")!! as String
val depsVersionTsLibFfmpeg = project.findProperty("depsVersionTsLibFfmpeg")!! as String

// ---------------------------------------------------------------------------------------------------------------------

fun getOperatingSystemName() : String {
	return if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
		"macos"
	} else if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
		"linux"
	} else if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
		"win"
	} else {
		throw Error("Operating System not supported")
	}
}

fun getCpuArchitecture() : String {
	return when (System.getProperty("os.arch")) {
		"x86_64", "x64", "amd64" -> "x64"
		/*Linux/macOS:*/"aarch64" -> "aarch64"
		else -> throw Error("CPU Architecture not supported")
	}
}

val osName: String = getOperatingSystemName()
val cpuArch: String = getCpuArchitecture()
if (osName == "win" && cpuArch != "x64") {
	throw Error("Cannot build for ${osName}-${cpuArch}")
}

println("Host: ${osName}-${cpuArch}")
if (doAgentTest) { println("--> Do agent test") }

// ---------------------------------------------------------------------------------------------------------------------

val gitCommitHashProvider = if (! layout.projectDirectory.dir(".git").asFile.isDirectory) {
		providers.provider { "unknown" }
	} else {
		providers.exec {
			commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
			isIgnoreExitValue = true
		}.standardOutput.asText.map { output ->
			output.trim().ifEmpty { "unknown" }
		}
	}

// ---------------------------------------------------------------------------------------------------------------------

java {
	/*
	 * Show auto-detectable JDKs:
	 *   $ ./gradlew -q javaToolchains
	 */

	sourceCompatibility = JavaVersion.VERSION_25
	/*toolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
	}*/
}

// ---------------------------------------------------------------------------------------------------------------------

repositories {
	mavenLocal()  // for the locally built JavaCPP library (see Docker base image)
	mavenCentral()
	if (! doUseCustomLibsFromMaven) {
		// for local development:
		flatDir { dirs(
				"../libs/lib-rtsp_xrtxp/build/libs",
				"../libs/lib-rtsp_ffmpeg/build/libs"
			) }
	}
}

dependencies {
	// for Unit Tests
	val depsVersionOrgJunitJunitBom = project.findProperty("depsVersionOrgJunitJunitBom")!! as String
	testImplementation(platform("org.junit:junit-bom:${depsVersionOrgJunitJunitBom}"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	//
	implementation("org.jspecify:jspecify:1.0.0")

	implementation("com.google.code.gson:gson:2.13.2")  // for JSON deserialization

	implementation("org.zeromq:jeromq:0.6.0")  // for ZeroMQ

	// custom libraries
	if (! doUseCustomLibsFromMaven) {
		// for local development
		implementation(":ts_lib_xrtxp:${depsVersionTsLibXrtxp}")
		implementation(":ts_lib_ffmpeg:${depsVersionTsLibFfmpeg}-full-${osName}-${cpuArch}")
	} else {
		// use official release
		implementation("io.github.tsitle:ts_lib_xrtxp:${depsVersionTsLibXrtxp}")
		implementation("io.github.tsitle:ts_lib_ffmpeg:${depsVersionTsLibFfmpeg}-${osName}-${cpuArch}")
	}
}

// ----------------------------------------------------------------

tasks.compileJava.configure {
	options.encoding = "UTF-8"
	options.compilerArgs.add("-Xlint:deprecation")
}

/*
 * see https://docs.gradle.org/current/userguide/java_testing.html
 */
tasks.test {
	useJUnitPlatform()

	jvmArgs("--enable-native-access=ALL-UNNAMED")  // for FFmpeg

	testLogging {
		events("passed")
	}
}

// ----------------------------------------------------------------

application {
	mainClass = confMainClass
	applicationDefaultJvmArgs += "-DappVersion=${version}"
	applicationDefaultJvmArgs += "-DgitCommit=${gitCommitHashProvider.get()}"
	//applicationDefaultJvmArgs += "-Djavax.net.debug=all"  // to enable full SSL debug output
	applicationDefaultJvmArgs += "-Xms${jvmMemHeapInit}"   // initial heap size
	applicationDefaultJvmArgs += "-Xmx${jvmMemHeapMax}"   // maximum heap size
	applicationDefaultJvmArgs += "-XX:MaxRAM=${jvmMemMaxTotalAbs}"  // maximum total JVM memory size
	//applicationDefaultJvmArgs += "-XX:MaxRAMPercentage=${jvmMemMaxTotalPerc}"  // maximum total JVM memory size
	applicationDefaultJvmArgs += "-XX:+UseZGC"  // available since Java 15
	//applicationDefaultJvmArgs += "-XX:+ZGenerational"  // this flag was removed in JDK 24
	applicationDefaultJvmArgs += "--enable-native-access=ALL-UNNAMED"  // for FFmpeg
}

// ----------------------------------------------------------------

tasks.jar {
	manifest {
		attributes["Main-Class"] = confMainClass
	}
	archiveClassifier.set("slim")  // adds the suffix '-slim'
}

tasks.shadowJar {
	dependsOn("jar")
	archiveClassifier.set("full-${osName}-${cpuArch}")  // an empty classifier replaces the regular JAR
}

tasks.installShadowDist {
	dependsOn("jar")
}

// ----------------------------------------------------------------

distributions {
	main {
		distributionBaseName = "${propProjName}-${osName}"  // the version will autom. be appended
	}
}

// org.beryx.runtime: Creates an image containing the application, a custom JRE, and appropriate start scripts
runtime {
	// output directory and ZIP filename for the launcher image
	imageDir = File(layout.buildDirectory.get().toString(), "${propProjName}-${osName}-${cpuArch}-${version}")
	imageZip = File(layout.buildDirectory.get().toString(), "${propProjName}-${osName}-${cpuArch}-${version}.zip")

	// reduce the size of the launcher image and very importantly, add the SunEC module
	options.set(listOf(
		"--strip-debug",
		"--compress",
		"zip-6",
		"--no-header-files",
		"--no-man-pages",
		"--add-modules", "jdk.crypto.ec"
	))
}

// org.beryx.runtime: Creates a ZIP archive of the custom runtime image including the JRE
tasks.runtimeZip {
	doFirst {
		if (osName != "win") {
			throw Exception("Do not use 'runtimeZip' under macOS/Linux. Use 'runtimeTar' instead")
		}
	}

	doLast {
		val tmpFileObjOrg: File = imageZip.asFile
		if (! tmpFileObjOrg.exists()) {
			throw Exception("File '${tmpFileObjOrg}' not found")
		}
		val tmpFileObjTrg = File(confDistPreOutputDir, tmpFileObjOrg.name)
		println("Renaming '${tmpFileObjOrg}' to '${tmpFileObjTrg}'")
		tmpFileObjOrg.renameTo(tmpFileObjTrg)
	}
}

tasks {
	register<Copy>("runtimeTarPreCopy")
	named<Copy>("runtimeTarPreCopy") {
		dependsOn(":runtime")

		doFirst {
			if (osName == "win") {
				throw Exception("Do not use 'runtimeTar' under MS Windows. Use 'runtimeZip' instead")
			}
		}

		/*
		 * We need to copy the launcher dir into a new subdir first,
		 * otherwise the resulting TAR-ball would not contain a parent directory
		 */
		val tmpPreCopyDir = layout.buildDirectory.dir("runtimeTarPreCopy").get().toString()
		val tmpBaseFilenTrg = runtime.get().imageDirAsFile.name
		from(runtime.get().imageDir)
		into(File(tmpPreCopyDir, tmpBaseFilenTrg))
	}

	register<Tar>("runtimeTarMain")
	named<Tar>("runtimeTarMain") {
		dependsOn(":runtimeTarPreCopy")

		doFirst {
			if (osName == "win") {
				throw Exception("Do not use 'runtimeTar' under MS Windows. Use 'runtimeZip' instead")
			}
			if (! File(confDistPreOutputDir).exists()) {
				throw Exception("Directory '${confDistPreOutputDir}' does not exist")
			}
		}

		val tmpPreCopyDir = layout.buildDirectory.dir("runtimeTarPreCopy").get().toString()
		val tmpBaseFilenTrg = runtime.get().imageDirAsFile.name
		println("Creating '${confDistPreOutputDir}/${tmpBaseFilenTrg}.tgz'...")
		from(File(tmpPreCopyDir))
		archiveFileName.set("${tmpBaseFilenTrg}.tgz")
		destinationDirectory.set(File(confDistPreOutputDir))
		compression = Compression.GZIP
	}

	register<DefaultTask>("runtimeTarCleanUp")
	named<DefaultTask>("runtimeTarCleanUp") {
		dependsOn(":runtimeTarMain")

		doFirst {
			if (osName == "win") {
				throw Exception("Do not use 'runtimeTar' under MS Windows. Use 'runtimeZip' instead")
			}
		}

		doLast {
			val tmpPreCopyDir = layout.buildDirectory.dir("runtimeTarPreCopy").get().toString()
			if (! File(tmpPreCopyDir).exists()) {
				throw Exception("Directory '${tmpPreCopyDir}' does not exist")
			}
			File(tmpPreCopyDir).deleteRecursively()
		}
	}

	// Creates a TAR archive of the custom runtime image including the JRE
	register<DefaultTask>("runtimeTar")
	named<DefaultTask>("runtimeTar") {
		dependsOn(":runtimeTarCleanUp")

		doFirst {
			if (osName == "win") {
				throw Exception("Do not use 'runtimeTar' under MS Windows. Use 'runtimeZip' instead")
			}
		}
	}
}

// Creates a TAR archive of the project distribution without the JRE
tasks.distTar {
	compression = Compression.GZIP
	archiveExtension = "tgz"

	doFirst {
		if (osName == "win") {
			throw Exception("Do not use 'distTar' under MS Windows. Use 'distZip' instead")
		}
		if (! File(confDistPreOutputDir).exists()) {
			throw Exception("Directory '${confDistPreOutputDir}' does not exist")
		}
	}

	doLast {
		val tmpFilenOrg = "${distributions.main.get().distributionBaseName.get()}-${version}.tgz"
		val tmpFileObjOrg = File(
				layout.buildDirectory.dir("distributions").get().toString(),
				tmpFilenOrg
			)
		if (! tmpFileObjOrg.exists()) {
			throw Exception("File '${tmpFileObjOrg}' not found")
		}
		val tmpFilenTrg = "${distributions.main.get().distributionBaseName.get()}-${version}-launcher-no_jre.tgz"
		val tmpFileObjTrg = File(confDistPreOutputDir, tmpFilenTrg)
		println("Renaming '${tmpFileObjOrg}' to '${tmpFileObjTrg}'")
		tmpFileObjOrg.renameTo(tmpFileObjTrg)
	}
}

// Creates a ZIP archive of the project distribution without the JRE
tasks.distZip {
	doFirst {
		if (osName != "win") {
			throw Exception("Do not use 'distZip' under macOS/Linux. Use 'distTar' instead")
		}
	}

	doLast {
		val tmpFilenOrg = "${distributions.main.get().distributionBaseName.get()}-${version}.zip"
		val tmpFileObjOrg = File(
				layout.buildDirectory.dir("distributions").get().toString(),
				tmpFilenOrg
			)
		if (! tmpFileObjOrg.exists()) {
			throw Exception("File '${tmpFileObjOrg}' not found")
		}
		val tmpFilenTrg = "${distributions.main.get().distributionBaseName.get()}-${version}-launcher-no_jre.zip"
		val tmpFileObjTrg = File(confDistPreOutputDir, tmpFilenTrg)
		println("Renaming '${tmpFileObjOrg}' to '${tmpFileObjTrg}'")
		tmpFileObjOrg.renameTo(tmpFileObjTrg)
	}
}

// ----------------------------------------------------------------

graalvmNative {
	agent {
		enabled.set(doAgentTest)

		defaultMode.set("standard")

		/*
		 * After all individual agent runs have been completed, the resulting reachability metadata files
		 * need to be merged using '$ ./gradlew metadataCopy'.
		 * The custom task 'collectNativeImageMetadata' will run all individual agent runs and then
		 * execute 'metadataCopy'.
		 */
		metadataCopy {
			if (usePrivConf) {
				inputTaskNames.addAll(
						"agentPrivate"
					)
			} else {
				inputTaskNames.addAll(
						"agentDefault"
					)
			}

			/*
			 * Note that when compiling the native image, the output directory for the reachability metadata
			 * must first be copied to 'src/main/resources/META-INF/native-image/'
			 */
			outputDirectories.add(
				"src/main/resources/META-INF/native-image-${osName}-${cpuArch}"
			)

			//mergeWithExisting.set(true)
		}
	}

	binaries {
		named("main") {
			imageName = propProjName
			mainClass = confMainClass
			if (cpuArch == "aarch64") {
				buildArgs.add("-march=compatibility")  // required for RK3308
			}
			buildArgs.add("--no-fallback")  // fail if native image can't be fully built
			//buildArgs.add("--initialize-at-build-time=com.sun.media.sound.JDK13Services")

			buildArgs.add("-DappVersion=${version}")
			buildArgs.add("-DgitCommit=${gitCommitHashProvider.get()}")
			buildArgs.add("--initialize-at-build-time=${confAppInfoClass}")

			buildArgs.add("-DcustomIsNativeImage=true")

			// Default heap settings for the native executable (equivalent of -Xms / -Xmx / -XX:MaxRAM)
			buildArgs.add("-R:MinHeapSize=${jvmMemHeapInit}")
			buildArgs.add("-R:MaxHeapSize=${jvmMemHeapMax}")
			buildArgs.add("-R:MaxRAM=${jvmMemMaxTotalAbs}")
			//buildArgs.add("-R:MaxRAMPercentage=${jvmMemMaxTotalPerc}")  // can only be used together with the G1 garbage collector
			// GC is a build-time decision in Native Image; Serial GC is the default and the lowest-footprint choice
			buildArgs.add("--gc=serial")  // not all Garbage Collectors are supported by GraalVM

			//buildArgs.add("-Os")  // optimize for size
			buildArgs.add("-O2")  // default. Full optimizations for peak performance without size restrictions.

			buildArgs.add("-Dfile.encoding=UTF-8")
			buildArgs.add("-Dstdout.encoding=UTF-8")  // Java 17+
			buildArgs.add("-Dstderr.encoding=UTF-8")  // Java 17+
			buildArgs.add("-H:IncludeLocales=en")

			buildArgs.add("--enable-native-access=ALL-UNNAMED")  // for FFmpeg

			buildArgs.add("--libc=glibc")
			//buildArgs.add("--libc=musl")

			/*
			 * Compile at least the JRE libs statically into the native executable:
			 *  - libawt.so
			 *  - libawt_headless.so
			 *  - libawt_xawt.so
			 *  - libjava.so
			 *  - libjavajpeg.so
			 *  - libjvm.so
			 */
			//buildArgs.add("--static")  // building static executable images is only supported with musl libc ('--libc=musl')

			/*buildArgs.add("-H:NativeLinkerOption=-L/opt/ffmpeg/lib")
			buildArgs.add("-H:NativeLinkerOption=-lavcodec")
			buildArgs.add("-H:NativeLinkerOption=-lavdevice")
			buildArgs.add("-H:NativeLinkerOption=-lavfilter")
			buildArgs.add("-H:NativeLinkerOption=-lavformat")
			buildArgs.add("-H:NativeLinkerOption=-lavutil")
			buildArgs.add("-H:NativeLinkerOption=-lswresample")
			buildArgs.add("-H:NativeLinkerOption=-lswscale")*/
		}
	}
}

tasks.register<JavaExec>("agentDefault") {
	group = "native-image"
	description = "Collect Native Image metadata for the app's functionality - specifically the JNI calls for FFmpeg"

	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set(application.mainClass)

	/*
	 * Note that we need to terminate the running application gracefully.
	 * If we simply terminate the app using CTRL-C, the agent won't write the reachability metadata file.
	 * The CLI arg '--agent-test' will make the application listen for SIGUSR1
	 * and then shutdown itself down once the signal has been received.
	 */
	args(
		"--agent-test",
		"config/sample-config-with_ssl.json"
	)
}

tasks.register<JavaExec>("agentPrivate") {
	group = "native-image"
	description = "Collect Native Image metadata for the app's functionality - specifically the JNI calls for FFmpeg"

	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set(application.mainClass)

	args(
		"--agent-test",
		"config/sample-config-with_ssl-PRIV.json"
	)
}

tasks.register("collectNativeImageMetadata") {
	group = "native-image"
	description = "Collect all Native Image reachability metadata"

	if (usePrivConf) {
		dependsOn("agentPrivate")
	} else {
		dependsOn("agentDefault")
	}
	finalizedBy("metadataCopy")
}
