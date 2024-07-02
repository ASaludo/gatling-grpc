import com.google.protobuf.gradle.*
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem

plugins {
    id("scala")
    id("com.google.protobuf") version "0.9.2"

    id("io.gatling.gradle") version "3.11.5"

    idea
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.github.phisgr:gatling-grpc:0.18.0")
    implementation("io.gatling:gatling-core-java:3.11.5")
    implementation("com.github.phisgr:gatling-javapb:1.3.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}

val scalapbVersion = "0.11.11"

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.22.2"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.46.0"
        }
        id("scalapb") {
            artifact = if (getCurrentOperatingSystem().isWindows) {
                "com.thesamet.scalapb:protoc-gen-scala:${scalapbVersion}:windows@bat"
            } else {
                "com.thesamet.scalapb:protoc-gen-scala:${scalapbVersion}:unix@sh"
            }
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
                id("scalapb") {
                    option("grpc")
                }
            }
        }
    }
}

//sourceSets {
//    main {
//        scala {
//            srcDirs("${protobuf.protobuf.generatedFilesBaseDir}/main/scalapb")
//        }
//    }
//}
