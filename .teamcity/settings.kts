import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.toId
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.version
import shared.Artifacts
import shared.Params
import shared.addDockerRegistry
import shared.configurePackages
import shared.depPackage
import shared.ref


version = "2025.07"

project {

    params {
        text(Params.TEAMCITY_UI_READONLY, "true")

        text(
            Params.DOCKER_IMAGE_NAME,
            ref(Params.PACKAGE_NAME),
            "Docker Image Name",
        )
        text(
            Params.DOCKER_IMAGE_TAG,
            ref(Params.PACKAGE_VERSION),
            "Docker Image Rag",
        )

        text(
            Params.DOCKER_REGISTRY_REPO_HOST_AND_NAME,
            "whitelokki/",
            "Docker Repo Host",
        )

        text(
            Params.DOCKER_IMAGE_FULL_PATH,
            ref(Params.DOCKER_REGISTRY_REPO_HOST_AND_NAME) +
                ref(Params.DOCKER_IMAGE_NAME) + ":" +
                ref(Params.DOCKER_IMAGE_TAG),
        )

        text(
            Params.PACKAGE_RELEASE_NOTES_URL,
            "https://raw.githubusercontent.com/kubernetes/kubernetes/refs/heads/master/README.md",
            "Package Release Notes",
        )
    }

    subProject {
        this.id("Build")
        this.name = "Build"

        val dockerHubId =
            addDockerRegistry(
                name = "DockerHubPersonal",
                userNameRef = "whitelokki@gmail.com",
                passwordRef = "credentialsJSON:e01258ea-5518-406c-9ed1-e843713207ec",
                url = "https://docker.io",
            )

        val jarPackage =
            Artifacts(
                buildOnHost("Build Package"),
                Params.PACKAGE_NAME,
                Params.PACKAGE_VERSION,
            )

        val dockerImage =
            Artifacts(
                buildInDocker("Dockerfile", dockerHubId, jarPackage),
                Params.DOCKER_IMAGE_NAME,
                Params.DOCKER_IMAGE_TAG,
            )

        collectArtifacts("Artifacts", jarPackage, dockerImage)
    }
}

fun Project.buildInDocker(
    id: String,
    dockerHubId: String,
    jarPackage: Artifacts,
) = BuildType {
    this.id(id)
    this.name = "Build"

    params {
        text(Params.TEAMCITY_FORCE_CHECKOUT_ALL_REFS, "true")
    }

    depPackage(jarPackage)

    requirements {
        equals("docker.server.osType", "linux")
        equals("teamcity.agent.jvm.os.name", "Linux")
    }

    buildDockerImage(id, dockerHubId)
}.also { buildType(it) }

fun BuildType.buildDockerImage(
    buildConfigurationId: String,
    dockerHubId: String,
) {
    this.id(buildConfigurationId)
    this.name = "Build Docker Image"

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
    }

    features {
        dockerRegistryConnections {
            enabled = true
            loginToRegistry =
                on {
                    dockerRegistryId = dockerHubId
                }
        }
    }
    steps {
        dockerCommand {
            name = "Build docker image"
            commandType =
                build {
                    source =
                        file {
                            path = "Dockerfile"
                        }
                    platform = DockerCommandStep.ImagePlatform.Linux
                    namesAndTags = ref(Params.DOCKER_IMAGE_FULL_PATH)
                }
        }
        dockerCommand {
            name = "Push Docker Image"
            commandType =
                push {
                    namesAndTags = ref(Params.DOCKER_IMAGE_FULL_PATH)
                }
        }
    }
}

fun Project.buildOnHost(id: String) =
    BuildType {
        this.id(id.toId())
        this.name = id

        buildOnHost(id)
    }.also { buildType(it) }

fun BuildType.buildOnHost(id: String) {
    this.id(id.toId())
    this.name = id

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
    }

    steps {
        maven {
            name = "Build package"
            goals = "clean package"
            runnerArgs =
                """
                -D${'$'}{Params.PACKAGE_RELEASE_NOTES_URL}=${'$'}{ref(Params.PACKAGE_RELEASE_NOTES_URL)}"
                """.trimIndent()
            jdkHome = ref(Params.REQUIRED_JAVA_VERSION)
        }
    }

    artifactRules =
        """
        **/**.jar
        pom.xml
        **/release-notes/** => 
        **/site/** => javadoc_${ref(Params.PACKAGE_VERSION)}.zip
        target/** => release_${ref(Params.PACKAGE_VERSION)}.tar.gz
        """.trimIndent()
}

fun Project.collectArtifacts(
    id: String,
    jarPackage: Artifacts,
    dockerImage: Artifacts,
) = BuildType {
    this.id(id.toId())
    this.name = id

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
    }

    triggers {
        vcs {
            branchFilter = "+:*"
        }
    }

    configurePackages(jarPackage, dockerImage)

    artifactRules =
        """
        pkg => pkg
        """.trimIndent()
}.also { buildType(it) }

// -Darguments="-DskipDeploy"
//    <scm.username>${env.GIT_USERNAME}</scm.username>
//    <scm.password>${env.GIT_TOKEN}</scm.password>