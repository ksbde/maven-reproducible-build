import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import simple_service.MavenPackage
import simple_service.Params
import simple_service.configurePackage
import simple_service.ref


version = "2025.07"

// Root project
project {
    params {
        text(Params.teamcityUiReadOnly, "true")
        text(Params.dockerImageName, ref(Params.packageName), "Docker Image Name")
        text(Params.dockerImageTag, ref(Params.packageVersion), "Docker Image Rag")
        text(Params.packageReleasenotesUrl,
            "https://raw.githubusercontent.com/kubernetes/kubernetes/refs/heads/master/README.md",
        "Package Release Notes")
    }


    // Subprojects
    subProject {
        this.id("Build")
        this.name = "Build"


        val makePackage = MavenPackage(
            buildOnHost("Build Package"),
            Params.packageVersion,
            Params.packageVersion
        )
        val build = buildInDocker("Dockerfile", makePackage)

//        buildTypesOrder = listOf(makePackage, build)
    }
    subProject {
        this.id("Deploy")
        this.name = "Deploy"
    }


}

fun Project.buildInDocker(id: String, packageName: MavenPackage) = BuildType {
    this.id(id)
    this.name = "Build"
    requirements {
        equals("docker.server.osType", "linux")
        equals("teamcity.agent.jvm.os.name", "Linux")
    }

    buildDockerImage(id, packageName)
}.also { buildType(it) }

fun BuildType.buildDockerImage(buildConfigurationId: String, packageName: MavenPackage) {

    this.id(buildConfigurationId)
    this.name = "Build Docker Image"

    configurePackage(packageName)
    vcs {
        root(DslContext.settingsRoot, ". => build_dir")
        cleanCheckout = true
    }

    triggers {
        vcs {
            branchFilter = "+:*"
        }
    }

    steps {
        dockerCommand {
            name = "build docker"
            id = "DockerCommand"
            commandType = build {
                source = file {
                    path = "Dockerfile"
                }
                contextDir = "build_dir"
                platform = DockerCommandStep.ImagePlatform.Linux
                namesAndTags = ref(Params.packageName)+":"+ref(Params.packageVersion)
            }
        }
    }

}


fun Project.buildOnHost(id: String) = BuildType {
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

    buildNumberPattern = ref(Params.packageVersion)

    steps {
        maven {
            name = "Build package"
            goals = "clean package"
            runnerArgs = "-Drelease.notes.url=${ref(Params.packageReleasenotesUrl)}"
            jdkHome = ref(Params.requiredJavaVersion)
        }
        script {
            name = "Publish checksums"
            scriptContent = """
                sha256sum */*.jar > checksums.txt
            """.trimIndent()
        }
    }

    artifactRules = """
        **/**.jar
        **/checksums.txt => ${ref(Params.packageVersion)}_checksums.txt
        **/release-notes/** => 
        **/site/** => javadoc_${ref(Params.packageVersion)}.zip
        target/** => release_${ref(Params.packageVersion)}.tar.gz
    """.trimIndent()
}