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

To start `daml-om-sawtooth` with auth services you will need to modify this script `./docker/compose/daml-local.yaml`:

```
  daml-rpc:
    image: sawtooth-daml-rpc:${ISOLATION_ID}
    container_name: sawtooth-daml-rpc
    expose:
      - 9000
      - 5051
    ports:
      - "9000:9000"
      - "5051:5051"
    entrypoint: "bash -c \"\
      /opt/sawtooth-daml-rpc/entrypoint.sh --port 9000 \
        --connect tcp://validator:4004 \
        --jdbc-url jdbc:postgresql://postgres/postgres?user=postgres \
        --participant-id test-participant \
        --auth \"off\" \
        `ls /opt/sawtooth-daml-rpc/dar/*.dar`\""
    volumes:
      - ../test-dars:/opt/sawtooth-daml-rpc/dar/
      - ../keys:/opt/sawtooth-daml-rpc/keys
    depends_on:
      - validator
      - postgres
```

Set the argment `--auth to \"on\"`

With the authentication service in place, you will need to get appropriate token inject it into your your client such as `daml navigator`.

When you run `daml-on-sawtooth`, run the following command to extract a token:

STEP 1: Run the command to give you access to a `daml-on-sawtooth` cli:
```
  docker exec -it sawtooth-daml-rpc /bin/bash
```

STEP 2: In the `daml-on-sawtooth` cli, run the command:
```
  root@<generated-id>:/opt/sawtooth-daml-rpc# ls -l
```
and you will see the following:
```
-rw-r--r-- 1 root root   211 Dec 20 16:32 claims.json
drwxr-xr-x 2 root root    64 Dec 20 14:51 dar
-rwxr-xr-x 1 root root   655 Dec  9 10:13 entrypoint.sh
-rwxr-xr-x 1 root root   809 Dec 20 14:55 jwtgenerator.sh
drwxr-xr-x 4 root root   128 Dec 20 17:03 keys
drwxr-xr-x 2 root root 12288 Dec 23 15:50 lib
-rw-r--r-- 1 root root 66468 Dec 23 15:50 sawtooth-daml-rpc-0.0.1-SNAPSHOT.jar
```
You will notice that there is a folder named `keys`. This will contain the appropriate key pair.

STEP 3: In the cli, run the command:
```
  ./jwtgenerator.sh -pk ./keys/validator.priv -claim ./claims.json
```
NOTE: `daml-on-sawtooth` comes with default private key and default claims json configuration. Please use this default values for now.

STEP 4: The `daml-on-sawtooth` cli will display a string representation of the Token/
```
root@ee2dcd0c53d1:/opt/sawtooth-daml-rpc# ./jwtgenerator.sh -pk ./keys/validator.priv -claim ./claims.json
eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJsZWRnZXJJZCI6ImFhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJhY3RBcyI6W251bGxdLCJleHAiOjEzMDA4MTkzODAsInJlYWRBcyI6W251bGwsbnVsbF19.Gm9-dXUEpQORss_zGMP3TKQEiiUkDMtJpyftZGD9gLrWWGRKYfDdXa5QWhslff_YQs0UIDvQ2TFapep0UJXMAg
```

STEP 5: Copy the token string and copy the value into a file (e.g. `token.txt`). Ensure the content of the file is this form `Bearer <token string>`. For example:
```
Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJsZWRnZXJJZCI6ImFhYWFhYWFhYS1iYmJiLWNjY2MtZGRkZC1lZWVlZWVlZWVlZWUiLCJhY3RBcyI6W251bGxdLCJleHAiOjEzMDA4MTkzODAsInJlYWRBcyI6W251bGwsbnVsbF19.Gm9-dXUEpQORss_zGMP3TKQEiiUkDMtJpyftZGD9gLrWWGRKYfDdXa5QWhslff_YQs0UIDvQ2TFapep0UJXMAg
```

STEP 6: Load it to daml naviator via this command:
```
  daml ledger navigator --host localhost --port 9000 --access-token-file <path-to-token-file>
```

## License

daml-on-sawtooth is open source under [Apache Software License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Copyright

Copyright @2019 Blockchain Technology Partners
