package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.processor.Namespace;
import com.blockchaintp.sawtooth.daml.protobuf.AcceptedTransaction;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;

/**
 * An implementation of LedgerState for DAML.
 * @author scealiontach
 *
 */
public final class DAMLLedgerState implements LedgerState {

  /**
   * The state which this class wraps and delegates to.
   */
  private State state;

  /**
   *
   * @param aState the State class which this object wraps.
   */
  public DAMLLedgerState(final State aState) {
    this.state = aState;
  }

  @Override
  public boolean isDuplicateCommand(final String submitter, final String applicationId, final String commandId) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void setDuplicateCommand(final String submitter, final String applicationID, final String commandID,
      final boolean status) {
    // TODO Auto-generated method stub

  }

  @Override
  public Contract getContract(final String contractId) throws InternalError, InvalidTransactionException {
    String address = Namespace.makeContractAddress(contractId);
    Map<String, ByteString> vals = state.getState(Arrays.asList(address));
    ByteString c = vals.get(address);
    try {
      Contract retContract = Contract.parseFrom(c);
      return retContract;
    } catch (InvalidProtocolBufferException e) {
      throw new InternalError(String.format("Contract data is corrupt for contractId=% at address=%s, msg=%s",
          contractId, address, e.getMessage()));
    }
  }

  @Override
  public void setContract(final Contract contract) throws InternalError, InvalidTransactionException {
    Map<String, ByteString> contracts = new HashMap<>();
    contracts.put(Namespace.makeContractAddress(contract.getContractId()), contract.toByteString());
    state.setState(contracts.entrySet());
  }

  @Override
  public boolean isActiveContract(final String contractId) throws InternalError, InvalidTransactionException {
    Contract c = getContract(contractId);
    if (null == c) {
      return false;
    } else {
      return !c.getArchived();
    }
  }

  @Override
  public void setActiveContract(final String contractId, final boolean active) {
    // TODO Auto-generated method stub

  }

  @Override
  public void addLedgerSyncEvent(final AcceptedTransaction acceptedTraction) {
    // TODO Auto-generated method stub

  }

  @Override
  public DAMLPackage getDAMLPackage(final String packageId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setDAMLPackage(final DAMLPackage damlPackage, final String packageId) {
    // TODO Auto-generated method stub

  }

}
