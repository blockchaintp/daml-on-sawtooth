# daml-on-sawtooth

`daml-on-sawtooth` is an integration of the [DAML](https://daml.com/) smart contract runtime engine, created and open sourced by [Digital Asset](https://digitalasset.com/), with Hyperledger Sawtooth blockchain as the backing DLT.

Contribution guildines in [CONTRIBUTING.md](CONTRIBUTING.md)

## Running daml-on-sawtooth locally

See [BUILD.md](BUILD.md) for instruction to build and run `daml-on-sawtooth`.

#### Inspecting daml-on-sawtooth

To inspect transactions on `daml-on-sawtooth`, start your browser and open the url `http://localhost`.

**NOTE:**

`daml-on-sawtooth` transaction inspector uses port 80 by default. If you have mission critical local app using port 80, modify
`./docker/compose/daml-local.yaml` modify the "port" key values in this section:

```  
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
