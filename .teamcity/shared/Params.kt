package shared

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ParameterRef
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.projectFeatures.dockerRegistry
import jetbrains.buildServer.configs.kotlin.toId

class Params {
    companion object {
        const val TEAMCITY_UI_READONLY = "teamcity.ui.settings.readOnly"

        const val PACKAGE_VERSION = "maven.project.version"
        const val PACKAGE_NAME = "maven.project.name"

        const val PACKAGE_RELEASE_NOTES_URL = "release.notes.url"
        const val REQUIRED_JAVA_VERSION = "env.JDK_11_0"

        const val DOCKER_IMAGE_NAME = "docker.image.name"
        const val DOCKER_IMAGE_TAG = "docker.image.tag"
        const val DOCKER_REGISTRY_REPO_HOST_AND_NAME = "docker.registry.repo.host.and.name"
        const val DOCKER_IMAGE_FULL_PATH = "docker.registry.repo.full.path"

        const val TEAMCITY_FORCE_CHECKOUT_ALL_REFS = "teamcity.git.fetchAllHeads"
    }
}

class Artifacts(
    val build: BuildType,
    val pkgName: String,
    val packageVersion: String,
)

fun ref(name: String) = ParameterRef(name).toString()

fun BuildType.configurePackages(
    pkg: Artifacts,
    dockerImage: Artifacts,
) {
    dependencies {
        dependency(pkg.build) {
            snapshot {
                onDependencyCancel = FailureAction.CANCEL
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = true
            }
            artifacts {
                cleanDestination = true
                artifactRules = "**/** => pkg"
            }
        }
        dependency(dockerImage.build) {
            snapshot {
                onDependencyCancel = FailureAction.CANCEL
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = true
            }
        }
    }

    params {
        text(pkg.pkgName, "${pkg.build.depParamRefs[Params.PACKAGE_NAME]}")
        text(pkg.packageVersion, "${pkg.build.depParamRefs[Params.PACKAGE_VERSION]}")
        text(dockerImage.pkgName, "${dockerImage.build.depParamRefs[Params.DOCKER_IMAGE_NAME]}")
        text(dockerImage.packageVersion, "${dockerImage.build.depParamRefs[Params.DOCKER_IMAGE_TAG]}")
    }
}

fun BuildType.depPackage(pkg: Artifacts) {
    dependencies {
        dependency(pkg.build) {
            snapshot {
                onDependencyCancel = FailureAction.CANCEL
                onDependencyFailure = FailureAction.FAIL_TO_START
                synchronizeRevisions = true
            }
        }
    }

    params {
        text(pkg.pkgName, "${pkg.build.depParamRefs[Params.PACKAGE_NAME]}")
        text(pkg.packageVersion, "${pkg.build.depParamRefs[Params.PACKAGE_VERSION]}")
    }
}

fun Project.addDockerRegistry(
    name: String,
    userNameRef: String,
    passwordRef: String,
    url: String,
): String {
    val registryId = "docker-registry-${name.toId()}"

    features {
        dockerRegistry {
            id = registryId
            this.name = name
            userName = userNameRef
            password = passwordRef
            this.url = url
        }
    }

    return registryId
}
