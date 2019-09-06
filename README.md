# daml-on-sawtooth

`daml-on-sawtooth` is an integration of the [DAML](https://daml.com/) smart contract runtime engine, created and open sourced by [Digital Asset](https://digitalasset.com/), with Hyperledger Sawtooth blockchain as the backing DLT.

Contribution guildines in [CONTRIBUTING.md](CONTRIBUTING.md)

## Running daml-on-sawtooth locally

The following steps describes the steps needed to get `daml-on-sawtooth` to run locally

### Prerequisite

1. Linux/macOS only
1. Docker version 19.03.2
1. OpenJDK version 12.0.1
1. NodeJS version 10.16.2

### Build an executable daml-on-sawtooth

1. Clone the repository. `$ git clone git@github.com:blockchaintp/daml-on-sawtooth.git`
1. Set export the build identifier environment variable.  This is used to distinguish different variations of builds on the same machine. `$ export ISOLATION_ID=my-local-buid`
1. Execute the local build script. This will compile and package all of the java, as well as prepare docker images for local execution. `$ bin/build.sh`

### Running and shutting down daml-on-sawtooth

1. Run up a development copy of daml-on-sawtooth.  This contains a single node sawtooth environment, running the devmode consensus and a DAML environment. `$ docker-compose -f ./docker/compose/daml-local.yaml up`
1. In order to restart this environment from scratch, be sure to down the docker-compose environment. `$ docker-compose -f ./docker/compose/daml-local.yaml down`

### Inspecting daml-on-sawtooth transactions

To inspect transactions on `daml-on-sawtooth`, start your browser and open the url `http://localhost`.

**NOTE:**

`daml-on-sawtooth` transaction inspector uses port 80 by default. If you have mission critical local app using port 80, edit this file `./docker/compose/daml-local.yaml` modify the "port" key values in this section:

```yaml  
router:
    image: binocarlos/noxy
    ports:
      - <port of your choice>:80 # change the left value to a port to avoid clashing
    depends_on:
      - daml-rpc
      - tracer-ui
    environment:
      - NOXY_DEFAULT_HOST=tracer-ui
      - NOXY_DEFAULT_PORT=8080
      - NOXY_DEFAULT_WS=1
      - NOXY_API_FRONT=/transactions
      - NOXY_API_HOST=sawtooth-daml-rpc
      - NOXY_API_PORT=50511
```

You will now need to start your browser with this url `http://localhost:{port-of-your-choice}`.

## License

daml-on-sawtooth is open source under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Copyright

Copyright @2019 Blockchain Technology Partners
