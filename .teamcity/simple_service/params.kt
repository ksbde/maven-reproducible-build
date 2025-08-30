package simple_service

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ParameterRef

class Params {
    companion object {
        const val teamcityUiReadOnly = "teamcity.ui.settings.readOnly"

        const val packageVersion = "maven.project.version"
        const val packageName = "maven.project.name"

        const val packageReleasenotesUrl = "package.releasenotes"
        const val requiredJavaVersion = "env.JDK_11_0"

        const val dockerRepoHostAndName = "docker.repohost.and.name"
        const val dockerRepoSpace = "docker.repo.space"

        const val dockerRepoAndNamespace = "docker.name.and.namespace"
        const val dockerImageFullPath = "docker.image.full.path"

        const val dockerImage = "docker.image"
        const val dockerImageName = "docker.image.name"
        const val dockerImageTag = "docker.image.tag"

        const val teamcityCheckoutDir = "teamcity.build.checkoutDir"
        const val teamcityForceCheckoutAllRefs = "teamcity.git.fetchAllHeads"
        const val teamcityUseGitAlternates = "teamcity.git.useAlternates"

    }
}

fun ref(name: String) = ParameterRef(name).toString()

class MavenPackage(val build: BuildType, val packageVersion: String, val versionParam: String)

fun BuildType.configurePackage(pkg: MavenPackage) {
    dependencies {
        dependency(pkg.build) {
            snapshot {
                onDependencyCancel = FailureAction.CANCEL
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = true
            }
            artifacts {
                cleanDestination = true
                artifactRules = "**/*.jar => pkg"
            }
        }
    }

}