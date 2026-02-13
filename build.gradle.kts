plugins {
	id("java")
	id("application")
}

group = "org.tsitle.rtsp"
version = "1.0"

val propProjName = "rtsp_server"

// ---------------------------------------------------------------------------------------------------------------------

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:5.10.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("org.jspecify:jspecify:1.0.0")
	implementation("com.google.code.gson:gson:2.13.2")  // for JSON deserialization
}

tasks.test {
	useJUnitPlatform()
}

application {
	mainClass = "org.tsitle.rtsp.RtspServerApp"
}

tasks.jar {
	manifest {
		attributes["Main-Class"] = "org.tsitle.rtsp.RtspServerApp"
	}
}
