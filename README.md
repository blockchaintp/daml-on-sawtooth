# daml-on-sawtooth

`daml-on-sawtooth` is an integration of the [DAML](https://daml.com/) smart contract runtime engine, created and open sourced by [Digital Asset](https://digitalasset.com/), with Hyperledger Sawtooth blockchain as the backing DLT.

## Contributing to the project

This is an open source project. Anyone is welcome to contribute to the project.

If you wish to contribute, please refer to the guidelines mentioned in [CONTRIBUTING.md](CONTRIBUTING.md)

## Running daml-on-sawtooth

This project uses docker based technologies to build and run daml-on-sawtooth.

Please follow these steps to get a running daml-on-sawtooth

### 1. Prerequisite

* Linux/macOS only
* Docker version 19.03.2
* OpenJDK version 12.0.1
* NodeJS version 10.16.2
* daml SDK

### 2. Build an executable daml-on-sawtooth

You must first ensure you have installed the necessary docker tools and build the daml-on-sawtooth source code.

Please refer to [BUILD.md](./BUILD.md) for further instructions.

### 3. Running and shutting down daml-on-sawtooth

* Open a terminal and `cd` into the project folder (i.e. location where you `git clone the daml-on-sawtooth` project) described in step 2.

* To run up a development copy of daml-on-sawtooth, and at the top level ot the project, run this command `./docker/run.sh start`. This will start-up single node sawtooth environment, running the devmode consensus and a DAML environment.

* To shutdown the application by running the following command `./docker/run.sh stop`.

NOTE: By default, it will start `daml-on-sawtooth` with `AuthService` switched off.

### 4. Interacting with daml-on-sawtooth using daml navigator

4.1 Open a terminal (one where you are not hosting a running daml-on-sawtooth see STEP 3).

4.2 Install [daml sdk](https://docs.daml.com/getting-started/installation.html)

4.3 Run the command `daml ledger navigator --host localhost --port 9000` or `daml ledger navigator --host <url of your daml-on-sawtooth deployment> --port 9000`. When you run this command successfully, you will see the following message

```text
Opening navigator at localhost:9000
   _  __          _           __
  / |/ /__ __  __(_)__ ____ _/ /____  ____
 /    / _ `/ |/ / / _ `/ _ `/ __/ _ \/ __/
/_/|_/\_,_/|___/_/\_, /\_,_/\__/\___/_/
                 /___/
Version 100.13.41
Frontend running at http://localhost:4000.
```

**Note:** Port 9000 is the port where daml-on-sawtooth is opened for communications with the outside world. If you have a custom daml application, you can connect to it via this port.

4.4 Open the browser `http://localhost:4000` (see above) and you will be presented with daml navigator interface. **NOTE:** Port 4000 is the default entry point for daml navigator.

### 5. Inspecting daml-on-sawtooth transactions

This project provides you with a browser based interface to inspect inspect transactions on `daml-on-sawtooth`.  

To inspect transactions, open your browser with this url `http://localhost`.

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

## Authentication service

`daml-on-sawtooth` offers a number of option to activate authentication service.

Please refer to [AUTH.md](./AUTH.md) for futher instruction

## License

daml-on-sawtooth is open source under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Copyright

Copyright @2019 Blockchain Technology Partners
