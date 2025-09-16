import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.toId
import jetbrains.buildServer.configs.kotlin.triggers.VcsTrigger
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

        password(
            Params.DOCKER_REGISTRY_USER,
            "credentialsJSON:2ebee0ec-063e-434d-bc82-a5d94a34dbfe",
            display = ParameterDisplay.HIDDEN,
        )
        password(
            Params.DOCKER_REGISTRY_PASS,
            "credentialsJSON:c9dc5ede-f626-4586-8732-3c5279a1f3d5",
            display = ParameterDisplay.HIDDEN,
        )
    }

    subProject {
        this.id("Build")
        this.name = "Build"

        val dockerHubId =
            addDockerRegistry(
                name = "DockerHubPersonal",
                userNameRef = ref(Params.DOCKER_REGISTRY_USER),
                passwordRef = ref(Params.DOCKER_REGISTRY_PASS),
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

        params {
            password(
                Params.NEXUS_USER,
                "credentialsJSON:bde4f8e7-bf32-4480-8fcc-b55094e85b47",
                display = ParameterDisplay.HIDDEN
            )
            password(
                Params.NEXUS_PASSWORD,
                "credentialsJSON:bde4f8e7-bf32-4480-8fcc-b55094e85b47",
                display = ParameterDisplay.HIDDEN
            )

            password(
                Params.GIT_USER,
                "credentialsJSON:dac7ff18-e71a-4128-adcb-3f5910c05418",
                display = ParameterDisplay.HIDDEN
            )
            password(
                Params.GIT_PASS,
                "credentialsJSON:7ce5905a-e9ae-47af-855d-aa3a70c70fb1",
                display = ParameterDisplay.HIDDEN
            )
        }

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
        script {
            scriptContent = """
                git config user.name "TeamcityBuild"
                git config user.email "TeamcityBuild@localhost"
            """.trimIndent()
        }

        maven {
            name = "Prepare release"
            goals = "release:clean release:prepare"
            userSettingsSelection = "userSettingsSelection:byPath"
            userSettingsPath = "settings.xml"
            runnerArgs =
                """
                -D${'$'}{Params.PACKAGE_RELEASE_NOTES_URL}=${'$'}{ref(Params.PACKAGE_RELEASE_NOTES_URL)}"
                """.trimIndent()
            jdkHome = ref(Params.REQUIRED_JAVA_VERSION)
        }
        maven {
            conditions {
                equals(Params.TEAMCITY_BUILD_BRANCH_IS_DEFAULT, "true")
            }
            name = "Perform release"
            goals = "release:perform"
            userSettingsSelection = "userSettingsSelection:byPath"
            userSettingsPath = "settings.xml"
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

    configurePackages(jarPackage, dockerImage)

    artifactRules =
        """
        pkg => pkg
        """.trimIndent()
}.also { buildType(it) }
