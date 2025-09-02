# maven-reproducible-build

## Repository structure

The Git repository contains the following top directories:

- **.teamcity** dir contains Kotlin DSL for teamcity server
- **teamcity-server** dir contains the compose-file for teamcity-server
- **src** dir contains simple-servicein java.
- **deployments** dir contains docker-compose for running simple-service

## How to start Teamcity-server

- for starting teamcity server you need to have docker installed on your VMs.

```bash
cd teamcity-server; docker compose -f docker-compose.yml up
```

- then you need to initialize teamcity server, by default teamcity server exposing on <http://localhost:8112>. Navigate on this page and initialize teamcity server.

- Create a VCS Root for a Root project <https://github.com/ksbde/maven-reproducible-build.git> , select "No mirrors" for **Checkout Policy** and provide branch specification "+:refs/heads/*"

- Create a new project and enable **Versioning Settings** and select **VCS Root**

- If you would like to push image into docker registry you need to modify [pipeline](https://github.com/ksbde/maven-reproducible-build/blob/main/.teamcity/settings.kts#L67-L70) and provide correct Docker Hub token with required permission.

You always able to override docker hub repository by modifying next parameters:
    - **docker.registry.repo.host.and.name** parameter
    - and you need to create docker hub repository with name **simple-service**

Keep in mind without these change docker image cannot by pushed in Docker Registry.
Service will be accessible on <http://localhost:8080/hello>

## Run service locally

```bash
cd deployments; docker compose -f docker-compose.yml up
```

## How to check reproducibility

- First of all, You have to download jar-files from teamcity server
- Install **diffoscope** for checking diffs
- Build package locally and put jar-files from teamcity server to reference dir

```bash
mvn clean package
sha256sum <downloaded-jar-files> target/*.jar
diffoscope <downloaded-jar-file> target/<local-jar-file>.jar
```

- Checking with docker

```bash
mvn clean package
sha256sum target/*-with-dependencies.jar > checksums_local.txt

docker build . -t test

docker run -t --entrypoint="" test sh -c "sha256sum *.jar" > checksums_docker.txt

diff -Nu checksums_local.txt checksums_docker.txt
```
