// Copyright (c) 2019 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.quickstart.iou;


import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.javaapi.data.*;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Iou {
    private final static Logger logger = LoggerFactory.getLogger(Iou.class);

    private final String issuer;
    private final String owner;
    private final String currency;
    private final BigDecimal amount;
    private final List<String> observers;

    static final int ISSUER = 0;
    static final int OWNER = 1;
    static final int CURRENCY = 2;
    static final int AMOUNT = 3;
    static final int OBSERVERS = 4;

    public Iou(
            String issuer,
            String owner,
            String currency,
            BigDecimal amount,
            List<String> observers
    ) {
        this.issuer = issuer;
        this.owner = owner;
        this.currency = currency;
        this.amount = amount;
        this.observers = ImmutableList.copyOf(observers);
    }

    public static Iou fromCreated(CreatedContract c) {
        logger.debug("toIou({}, {})", c.getCreateArguments(), c.getContext());
        return fromRecord(c.getCreateArguments());
    }

    public static Iou fromRecord(Record record) {
        Record.Field issuerField = record.getFields().get(Iou.ISSUER);
        Record.Field ownerField = record.getFields().get(Iou.OWNER);
        Record.Field currencyField = record.getFields().get(Iou.CURRENCY);
        Record.Field amountField = record.getFields().get(Iou.AMOUNT);
        Record.Field observersField = record.getFields().get(Iou.OBSERVERS);
        String issuer = issuerField.getValue().asParty().orElseThrow(() ->
            new IllegalStateException("Issuer should be of type Party, found "
                + amountField.toString())).getValue();
        String owner = ownerField.getValue().asParty().orElseThrow(() ->
            new IllegalStateException("Issuer should be of type Party, found "
                + amountField.toString())).getValue();
        String currency = currencyField.getValue().asText().orElseThrow(() ->
            new IllegalStateException("Currency should be of type Text, found "
                + amountField.toString())).getValue();
        BigDecimal amount = amountField.getValue().asDecimal().orElseThrow(() ->
            new IllegalStateException("Amount should be of type Decimal, found "
                + amountField.toString())).getValue();
        DamlList<Value> observerList = observersField.getValue().<Value>asList().orElseThrow(() ->
                new IllegalStateException("Observers should be of type List, found "
                        + amountField.toString()));
        List<String> observers = observerList.getValues()
        .stream().map(v -> v.asParty().orElseThrow(() ->
                new IllegalStateException("Observer should be of type Party, found "
                                + amountField.toString())).getValue())
        .collect(Collectors.toList());
        return new Iou(issuer, owner, currency, amount, observers);
    }

    public String getIssuer() {
        return issuer;
    }

    public String getOwner() {
        return owner;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public List<String> getObservers() {
        return ImmutableList.copyOf(observers);
    }

    @Override
    public String toString() {
        return "Iou{" +
                "issuer=" + issuer +
                ", owner=" + owner +
                ", currency=" + currency +
                ", amount=" + amount +
                ", observers=[" + String.join(", ", observers) + "]" + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Iou that = (Iou) o;
        return Objects.equals(issuer, that.issuer) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(currency, that.currency) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(observers, that.observers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, owner, currency, amount);
    }

    public Record record(Identifier templateId) {
        return new Record (
                templateId,
                new Record.Field("issuer", new Party(issuer)),
                new Record.Field("owner", new Party(owner)),
                new Record.Field("currency", new Text(currency)),
                new Record.Field("amount", new Decimal(amount)),
                new Record.Field("observers", new DamlList(
                        observers.stream().map(Party::new).collect(Collectors.toList())
                ))
        );
    }

    public CreateCommand createCommand(Identifier templateId) {
        return new CreateCommand(templateId, record(templateId));
    }
}
