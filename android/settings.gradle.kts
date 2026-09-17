// Phone Mirror Android 工程模块与依赖仓库配置。
pluginManagement {
    repositories {
        // 服务器位于国内网络环境，先用镜像减少插件解析超时；官方仓库保留作兜底。
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("android\\.arch\\..*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("android\\.arch\\..*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Android 与 JVM 依赖优先走国内镜像，避免长时间无输出阻塞生产服务器。
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("android\\.arch\\..*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("android\\.arch\\..*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "PhoneMirror"
include(":app")
