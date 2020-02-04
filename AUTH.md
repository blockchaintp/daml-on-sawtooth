# Auth Services

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
        --auth \"wildcard\" \
        `ls /opt/sawtooth-daml-rpc/dar/*.dar`\""
    volumes:
      - ../test-dars:/opt/sawtooth-daml-rpc/dar/
      - ../keys:/opt/sawtooth-daml-rpc/keys
    depends_on:
      - validator
      - postgres
```

You can activate `daml-on-sawtooth` auth services in one of the following modes:

* `wildcard` - set `--auth to \"wildcard\"`
* `hmac256` - set `--auth to \"hmac256=<secret>\"`
* `sawtooth` - set `--auth to \"sawtooth\"`

If `--auth` is not set or set to empty string, `daml-on-sawtooth` will start in `wildcard` mode.

## HMAC256 secrets based

This is based on shared secret string with maximum of size 256-bit(32-byte). 

You enter a secret value that you will be sharing between your daml client (i.e. daml navigator) and `daml-on-sawtooth`. If you were to use the daml cli tool, include the argument in the cli `daml allocate-parties PARTY --host <url to sawtooth> --paort 9000 --access-token-file <path-to-token-file>`

We recommend you use this purely for development mode.

## Sawtooth key based

**IMPORTANT:** This is still under development please avoid using this for now.

With the authentication service in place, you will need to get appropriate (also known as `sawtooth`-based) token inject it into your your client such as `daml navigator`.

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
