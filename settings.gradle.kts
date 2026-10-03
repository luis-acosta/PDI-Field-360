pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        mavenLocal()

        maven {
            url = uri("https://maven.aliyun.com/repository/public/")
        }

        maven {
            url = uri("https://jitpack.io")
        }

        // Repositorio oficial Insta360
        maven {
            url = uri("https://androidsdk.insta360.com/repository/maven-public/")

            credentials {
                username = "insta360guest"
                password = "EXMSjSo8OeOrjU7d"
            }
        }
    }
}

rootProject.name = "PDI-Field-360"
include(":app")
