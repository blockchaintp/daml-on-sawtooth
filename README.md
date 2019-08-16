# Introduction

This is a project to integrate Sawtooth with DAML

# Prerequisite

JDK version: 1.8
Maven version: 3.6.0

## Preparation for local development and/or execution

STEP 1. Install maven
   a) macOS use homebrew `brew install maven`
   b) Linux use `apt-get` or equivalement

STEP 2. Clone this repository.

STEP 3. In the command shell of your choice, navigate the local folder of this repository and run the command `mvn dependency:analyze -V`.
   a) This will download the necessary dependencies to enable you to build an executable version of this application.
   b) The build is sensitive to the platform where the application is built. The problem is likely to occur in the module: `daml-on-sawtooth-protobufs`. If you encounter this error, you will need to modify the configuration file in `~/.m2/settings.xml`.

STEP 4. Set in your command shell an environmental variable `export ISOLATION_ID=<a string value of your choice>`.

STEP 5. Run the command `./bin/build.sh` to ensure you have all the necessary artefacts built locally

STEP 6. Run the command `docker-compose -f ./docker/compose/daml-local.yaml up`

STEP 7. Open the browser and reference the url `localhost`.

## Fixing ./m2/settings.xml

```
<settings>
    <profiles>
        <profile>
            <id>osdetect</id>
            <properties>
                <os.detected.classifier>[linux-x86_64] | [osx-x86_64]</os.detected.classifier>
            </properties>
        </profile>
    </profiles>
	<activeProfiles>
		<activeProfile>osdetect</activeProfile>
	</activeProfiles>
</settings>
```

For os.detected.classifier attribute use linux-x86_64 if you are working on Linux platform or osx-x86_64 for mac. Setting should enable maven to download protobuf code generator to download a version appropriate for your platform from maven repositories.

However, there may be times where maven is unable to download appropriate protobuf generator from maven repositories. In which case, remove or comment out the <activeProfile> tag. This will rely on maven to detect appropriate protobuf generator installed on locally to generate appropriate source from the module `daml-on-sawtooth-protobufs`. You will need to have a protobuf generator installed locally. On macOS use Homebrew `brew install protobuf` and on Linux use equivalent.

# License

daml-on-sawtooth is open source under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

# Copyright

Copyright @2019 Blockchain Technology Partners
