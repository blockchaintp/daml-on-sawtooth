// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.quickstart.iou;

import com.daml.ledger.rxjava.DamlLedgerClient;
import com.daml.ledger.rxjava.LedgerClient;
import com.daml.ledger.rxjava.PackageClient;
import com.daml.ledger.javaapi.data.*;
import com.digitalasset.daml_lf.DamlLf;
import com.digitalasset.daml_lf.DamlLf1;
import com.google.common.collect.BiMap;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class IouMain {

    private final static Logger logger = LoggerFactory.getLogger(IouMain.class);

    // application id used for sending commands
    public static final String APP_ID = "IouApp";

    public static void main(String[] args) {
        // Extract host and port from arguments
        if (args.length < 4) {
            System.err.println("Usage: LEDGER_HOST LEDGER_PORT PARTY REST_PORT");
            System.exit(-1);
        }
        String ledgerhost = args[0];
        int ledgerport = Integer.valueOf(args[1]);
        String party = args[2];
        int restport = Integer.valueOf(args[3]);

        // create a client object to access services on the ledger
        DamlLedgerClient client = DamlLedgerClient.forHostWithLedgerIdDiscovery(ledgerhost, ledgerport, Optional.empty());

        // Connects to the ledger and runs initial validation
        client.connect();

        // inspect the packages on the ledger and extract the package id of the package containing the Iou module
        // this is helpful during development when the package id changes a lot due to frequent changes to the DAML code
        String packageId = detectIouPackageId(client);

        String ledgerId = client.getLedgerId();

        logger.info("ledger-id: {}", ledgerId);

        Identifier iouIdentifier = new Identifier(packageId, "Iou", "Iou");
        Identifier transferId = new Identifier(packageId, "Iou", "Iou_Transfer");
        TransactionFilter iouFilter = filterFor(iouIdentifier, party);

        AtomicLong idCounter = new AtomicLong(0);
        ConcurrentHashMap<Long, Iou> contracts = new ConcurrentHashMap<>();
        BiMap<Long, String> idMap = Maps.synchronizedBiMap(HashBiMap.create());
        AtomicReference<LedgerOffset> acsOffset = new AtomicReference<>(LedgerOffset.LedgerBegin.getInstance());

        client.getActiveContractSetClient().getActiveContracts(iouFilter, true)
                .blockingForEach(response -> {
                    response.getOffset().ifPresent(offset -> acsOffset.set(new LedgerOffset.Absolute(offset)));
                    for (CreatedEvent event : response.getCreatedEvents()) {
                        long id = idCounter.getAndIncrement();
                        contracts.put(id, Iou.fromRecord(event.getArguments()));
                        idMap.put(id, event.getContractId());
                    }
                });

        Disposable ignore = client.getTransactionsClient().getTransactions(acsOffset.get(), iouFilter, true)
                .forEach(t -> {
                    for (Event event : t.getEvents()) {
                        if (event instanceof CreatedEvent) {
                            CreatedEvent createdEvent = (CreatedEvent)event;
                            long id = idCounter.getAndIncrement();
                            contracts.put(id, Iou.fromRecord(createdEvent.getArguments()));
                            idMap.put(id, createdEvent.getContractId());
                        } else if (event instanceof ArchivedEvent) {
                            ArchivedEvent archivedEvent = (ArchivedEvent)event;
                            long id = idMap.inverse().get(archivedEvent.getContractId());
                            contracts.remove(id);
                            idMap.remove(id);
                        }
                    }
                });

        Gson g = new Gson();
        Spark.port(restport);
        Spark.get("/iou", "application/json", (req, res) -> g.toJson(contracts));
        Spark.get("/iou/:id", "application/json", (req, res) ->
                g.toJson(contracts.getOrDefault(Long.parseLong(req.params("id")), null))
        );
        Spark.put("/iou", (req, res) -> {
            Iou iou = g.fromJson(req.body(), Iou.class);
            submit(client, party, iou.createCommand(iouIdentifier));
            return "Iou creation submitted.";
        }, g::toJson);
        Spark.post("/iou/:id/transfer", "application/json", (req, res) -> {
            Map m = g.fromJson(req.body(), Map.class);
            submit(
                client,
                party,
                new ExerciseCommand(
                    iouIdentifier,
                    idMap.get(Long.parseLong(req.params("id"))),
                    "Iou_Transfer",
                    new Record(transferId, new Record.Field("newOwner", new Party(m.get("newOwner").toString())))
                )
            );
            return "Iou transfer submitted.";
        }, g::toJson);

        // Run until user terminates
        while (true)
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    private static Empty submit (LedgerClient client, String party, Command c) {
        return client.getCommandSubmissionClient().submit(
            UUID.randomUUID().toString(),
            "IouApp",
            UUID.randomUUID().toString(),
            party,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(10),
            Collections.singletonList(c))
            .blockingGet();
    }

    private static TransactionFilter filterFor(Identifier templateId, String party) {
        InclusiveFilter inclusiveFilter = new InclusiveFilter(Collections.singleton(templateId));
        Map<String, Filter> filter = Collections.singletonMap(party, inclusiveFilter);
        return new FiltersByParty(filter);
    }

    /**
     * Inspects all DAML packages that are registered on the ledger and returns the id of the package that contains the PingPong module.
     * This is useful during development when the DAML model changes a lot, so that the package id doesn't need to be updated manually
     * after each change.
     *
     * @param client the initialized client object
     * @return the package id of the example DAML module
     */
    private static String detectIouPackageId(LedgerClient client) {
        PackageClient packageService = client.getPackageClient();

        // fetch a list of all package ids available on the ledger
        Flowable<String> packagesIds = packageService.listPackages();

        // fetch all packages and find the package that contains the PingPong module
        String packageId = packagesIds
                .flatMap(p -> packageService.getPackage(p).toFlowable())
                .filter(IouMain::containsIouModule)
                .map(GetPackageResponse::getHash)
                .firstElement().blockingGet();

        if (packageId == null) {
            // No package on the ledger contained the PingPong module
            throw new RuntimeException("Module Iou is not available on the ledger");
        }
        return packageId;
    }

    private static boolean containsIouModule(GetPackageResponse getPackageResponse) {
        try {
            // parse the archive payload
            DamlLf.ArchivePayload payload = DamlLf.ArchivePayload.parseFrom(getPackageResponse.getArchivePayload());
            // get the DAML LF package
            DamlLf1.Package lfPackage = payload.getDamlLf1();
            // check if the PingPong module is in the current package package
            Optional<DamlLf1.Module> iouModule = lfPackage.getModulesList().stream()
                    .filter(m -> m.getName().getSegmentsList().contains("Iou")).findFirst();

            if (iouModule.isPresent())
                return true;

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return false;
    }
}