# Build

## Using the docker based toolchain
The basic build uses docker to pull down its toolchain and compile.
1. Install Docker [https://docs.docker.com/install/]
1. Install docker-compose [https://docs.docker.com/compose/install/]

1. Clone the repository.

```$ git clone git@github.com:blockchaintp/daml-on-sawtooth.git```

4. Set export the build identifier environment variable.  This is used to distinguish different variations of builds on the same machine.

```$ export ISOLATION_ID=my-local-build```

5. Execute the local build script. This will compile and package all of the java, as well as prepare docker images for local execution.

```$ bin/build.sh```

## Developing in an IDE

While the final build should be tested using the docker toolchain, it can be easier to develop the project using an IDE of your choice.  The project is a conventional maven project, with one caveat

The project uses google protocol buffers and we have seen that in some cases (particularly on MacOS) that the proper version of the `protoc` binary does not download automatically. In this case a slight change to your maven `settings.xml` is necessary.

```xml
<settings>
    <profiles>
        <profile>
            <id>osdetect</id>
            <properties>
                <!-- linux-x86_64 or osx-x86_64 as is appropriate for your platform -->
                <os.detected.classifier>osx-x86_64</os.detected.classifier>
            </properties>
        </profile>
    </profiles>
    ...
</settings>
```
_Please note that this profile should not be active by default otherwise it will interfere with the bin/build.sh described above!_

This will pre-populate the os.detected.classifer property and allow the correct `protoc` binary to be downloaded by the maven plugin.  Your maven commands and IDE environment will need to activate this profile in order for the build to complete successfully, as in the following command:

```$ mvn package -P osdetect ```
