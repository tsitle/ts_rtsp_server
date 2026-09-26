plugins {
	id("java")
	id("application")
	id("org.beryx.runtime") version "2.0.1"  // see https://badass-runtime-plugin.beryx.org/releases/latest/
	id("com.google.osdetector") version "1.7.3"  // see https://github.com/google/osdetector-gradle-plugin
	// this plugin will filter the JavaCPP and FFmpeg libraries such that only libs for the current CPU architecture will be included
	id("org.bytedeco.gradle-javacpp-platform") version "1.5.10"
	id("org.graalvm.buildtools.native") version "1.1.12"
	id("com.gradleup.shadow") version "9.6.1"  // for Fat JARs
}

group = "org.tsitle.rtsp_server"
version = "1.1.6"

val propProjName = rootProject.name  // from 'settings.gradle.kts'

val confMainClass = "org.tsitle.rtsp_server.RtspServerApp"
val confAppInfoClass = "org.tsitle.rtsp_server.AppInfo"

// output directory for distribution files (launchers and installers)
val confDistPreOutputDir = "distPre"

val jvmMemHeapInit: String = "128m"
val jvmMemHeapMax: String = "128m"
val jvmMemMaxTotalAbs: String = "256m"
//val jvmMemMaxTotalPerc: String = "50"

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

fun getLinuxDistroType() : String {
	if (getOperatingSystemName() != "linux") {
		return "none"
	}
	val tmpRel = osdetector.release
	if (tmpRel.isLike("debian")) {
		return "debian"
	}
	if (tmpRel.isLike("redhat") || tmpRel.isLike("fedora")) {
		return "redhat"
	}
	throw Error("Linux distribution type '${tmpRel}' not supported")
}

val osName: String = getOperatingSystemName()
val cpuArch: String = getCpuArchitecture()
if (osName == "win" && cpuArch != "x64") {
	throw Error("Cannot build for ${osName}-${cpuArch}")
}
val lxDistroType: String = getLinuxDistroType()

println("Host: ${osName}-${cpuArch}")

// ---------------------------------------------------------------------------------------------------------------------

repositories {
	mavenLocal()
	mavenCentral()
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:5.10.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	implementation("org.jspecify:jspecify:1.0.0")

	implementation("com.google.code.gson:gson:2.13.2")  // for JSON deserialization

	implementation("org.zeromq:jeromq:0.6.0")  // for ZeroMQ

	implementation("org.bytedeco:ffmpeg-platform:7.1.1-1.5.12")
	//implementation("org.bytedeco:ffmpeg-platform:8.0.1-1.5.13")  // requires Linux package 'libva-drm2'
	//implementation("org.bytedeco:ffmpeg-platform:8.1.2-1.5.14")  // requires Linux package 'libva-drm2'
}

// ----------------------------------------------------------------

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

tasks.compileJava.configure {
	options.encoding = "UTF-8"
	options.compilerArgs.add("-Xlint:deprecation")
}

// ----------------------------------------------------------------

application {
	mainClass = confMainClass
	applicationDefaultJvmArgs += "-DappVersion=${version}"
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

/*tasks.jar {
	manifest {
		attributes["Main-Class"] = confMainClass
	}
}*/

tasks.shadowJar {
	manifest {
		attributes["Main-Class"] = confMainClass
	}
	archiveClassifier.set("")   // replaces the regular JAR
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
	binaries {
		named("main") {
			imageName = propProjName
			mainClass = confMainClass
			buildArgs.add(
				"-H:ConfigurationFileDirectories=src/main/resources/META-INF/native-image-${cpuArch}"
			)
			if (cpuArch == "aarch64") {
				buildArgs.add("-march=compatibility")  // required for RK3308
			}
			buildArgs.add("--no-fallback")  // fail if native image can't be fully built
			//buildArgs.add("--initialize-at-build-time=com.sun.media.sound.JDK13Services")

			buildArgs.add("-DappVersion=${version}")
			buildArgs.add("--initialize-at-build-time=${confAppInfoClass}")

			buildArgs.add("-DcustomIsNativeImage=true")
			buildArgs.add("-Dorg.bytedeco.javacpp.cachedir=/tmp/.javacpp-rtsp/cache")

			// Default heap settings for the native executable (equivalent of -Xms / -Xmx / -XX:MaxRAM)
			buildArgs.add("-R:MinHeapSize=${jvmMemHeapInit}")
			buildArgs.add("-R:MaxHeapSize=${jvmMemHeapMax}")
			buildArgs.add("-R:MaxRAM=${jvmMemMaxTotalAbs}")
			//buildArgs.add("-R:MaxRAMPercentage=${jvmMemMaxTotalPerc}")  // can only be used together with the G1 garbage collector
			// GC is a build-time decision in Native Image; Serial GC is the default and the lowest-footprint choice
			buildArgs.add("--gc=serial")  // not all Garbage Collectors are supported by GraalVM

			//buildArgs.add("-Os")  // optimize for size
			buildArgs.add("-O2")  // default. Full optimizations for peak performance without size restrictions.

			buildArgs.add("-H:IncludeLocales=en")

			buildArgs.add("--enable-native-access=ALL-UNNAMED")  // for FFmpeg

			buildArgs.add("--static")
			buildArgs.add("--libc=musl")
			buildArgs.add("-H:NativeLinkerOption=-L/opt/ffmpeg/lib")
			buildArgs.add("-H:NativeLinkerOption=-lavcodec")
			buildArgs.add("-H:NativeLinkerOption=-lavdevice")
			buildArgs.add("-H:NativeLinkerOption=-lavfilter")
			buildArgs.add("-H:NativeLinkerOption=-lavformat")
			buildArgs.add("-H:NativeLinkerOption=-lavutil")
			buildArgs.add("-H:NativeLinkerOption=-lswresample")
			buildArgs.add("-H:NativeLinkerOption=-lswscale")
		}
	}
}
