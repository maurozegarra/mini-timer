import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

// Nota: el bloque pluginManagement se evalúa antes que el resto del script,
// así que el token y la config de repos deben ir inline dentro de cada bloque.

pluginManagement {
    val token = java.io.File(System.getProperty("user.home"), ".npmrc")
        .takeIf { it.exists() }
        ?.readLines()?.firstOrNull { it.contains("_authToken=") }
        ?.substringAfter("_authToken=")?.trim() ?: ""
    repositories {
        listOf("scp-gradle-public").forEach { repo ->
            maven {
                url = uri("https://gluonlatam.jfrog.io/artifactory/$repo")
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "Bearer $token"
                }
                authentication { create<HttpHeaderAuthentication>("header") }
            }
        }
    }
}
dependencyResolutionManagement {
    val token = java.io.File(System.getProperty("user.home"), ".npmrc")
        .takeIf { it.exists() }
        ?.readLines()?.firstOrNull { it.contains("_authToken=") }
        ?.substringAfter("_authToken=")?.trim() ?: ""
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        listOf("scp-gradle-public").forEach { repo ->
            maven {
                url = uri("https://gluonlatam.jfrog.io/artifactory/$repo")
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "Bearer $token"
                }
                authentication { create<HttpHeaderAuthentication>("header") }
            }
        }
    }
}

rootProject.name = "MiniTimer"
include(":app")
