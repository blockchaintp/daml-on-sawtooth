package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.blockchaintp.sawtooth.daml.processor.LedgerState;
import com.blockchaintp.sawtooth.daml.processor.Namespace;
import com.blockchaintp.sawtooth.daml.protobuf.TransactionSubmission;
import com.blockchaintp.sawtooth.daml.state.protobuf.Contract;
import com.blockchaintp.sawtooth.daml.state.protobuf.DAMLPackage;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.processor.State;
import sawtooth.sdk.processor.TransactionHandler;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.protobuf.TpProcessRequest;
import sawtooth.sdk.protobuf.TransactionHeader;

/**
 * A TransactionHandler implementation which handles DAML.
 *
 * @author scealiontach
 */
public final class DAMLTransactionHandler implements TransactionHandler {

  /**
   * The first six characters of the byte string representation of the familyName.
   */
  private String namespace;

  /**
   * The version of this handler.
   */
  private String version;

  /**
   * The human readable familyName.
   */
  private String familyName;

  /**
   * Constructs a TransactionHandler for DAML Transactions.
   */
  public DAMLTransactionHandler() {
    this.namespace = Namespace.getNameSpace();
    this.version = Namespace.FAMILY_VERSION_1_0;
    this.familyName = Namespace.FAMILY_NAME;
  }

  @Override
  public void apply(final TpProcessRequest tpProcessRequest, final State state)
      throws InvalidTransactionException, InternalError {

    basicRequestChecks(tpProcessRequest);

    LedgerState ledgerState = new DAMLLedgerState(state);
    TransactionSubmission submission;
    try {
      submission = TransactionSubmission.parseFrom(tpProcessRequest.getPayload());
    } catch (InvalidProtocolBufferException e) {
      InvalidTransactionException ite = new InvalidTransactionException(
          String.format("Payload is unparseable, and not a valid TransactionSubmission", e.getMessage().getBytes()));
      ite.initCause(e);
      throw ite;
    }

    // Check for a duplicate command
    boolean duplicate = ledgerState.isDuplicateCommand(submission.getSubmitter(), submission.getApplicationId(),
        submission.getCommandId());
    if (duplicate) {
      throw new InvalidTransactionException(
          String.format("DuplicateCommandRejection: applicationID=%s, commandID=%s is a duplicate command for %s",
              submission.getApplicationId(), submission.getCommandId(), submission.getSubmitter()));
    }

    // 3. Fetch all of the inputs
    // Add all of the contracts to a list of contracts
    // Add all of the templates/packages to a list of templates/packages
    List<String> inputsList = tpProcessRequest.getHeader().getInputsList();
    Collection<String> inputContractAddresses = new ArrayList<>();
    Map<String, Contract> inputContracts = new HashMap<>();
    Map<String, DAMLPackage> inputPackages = new HashMap<>();

    Collection<String> inputPackageAddresses = new ArrayList<>();
    Collection<String> inputDuplicateCommands = new ArrayList<>();

    for (String address : inputsList) {
      if (address.startsWith(Namespace.DUPLICATE_COMMAND_NS)) {
        inputDuplicateCommands.add(address);
      } else if (address.startsWith(Namespace.CONTRACT_NS)) {
        inputContractAddresses.add(address);
        inputContracts.put(address, ledgerState.getContract(address));
      } else if (address.startsWith(Namespace.PACKAGE_NS)) {
        inputPackageAddresses.add(address);
        inputPackages.put(address, ledgerState.getDAMLPackage(address));
      }
    }

    // 2. from the submission compute the transaction deltas ( contracts to be
    // removed in txDelta.inputs, contracts to be added in txDelta.outputs )
    // At this point all contract_ids may be assumed to be absolute, in fact they
    // need to be since all inputs and outputs need
    // to be known before the transaction is sent to the validator.

    // 3. Validate submission with DAML Engine

    validateSubmission(submission, state);

    throw new InvalidTransactionException("Implementation not yet finished");

  }

  /**
   * DAML Specific validation of the transaction.
   *
   * @param submission DAML content
   * @param state      current context of the transaction
   */
  private void validateSubmission(final TransactionSubmission submission, final State state) {
    // TODO Auto-generated method stub

  }

  /**
   * Fundamental checks of the transaction.
   *
   * @param tpProcessRequest the process request
   * @throws InvalidTransactionException if the transaction fails because of a
   *                                     business rule validation error
   * @throws InternalError               if the transaction fails because of a
   *                                     system error
   */
  private void basicRequestChecks(final TpProcessRequest tpProcessRequest)
      throws InvalidTransactionException, InternalError {
    TransactionHeader header = tpProcessRequest.getHeader();
    if (header == null) {
      throw new InvalidTransactionException("Header expected");
    }

    ByteString payload = tpProcessRequest.getPayload();
    if (payload.size() == 0) {
      throw new InvalidTransactionException("Empty payload");
    }

    if (!header.getFamilyName().equals(this.familyName)) {
      throw new InvalidTransactionException("Family name does not match");
    }

    if (!header.getFamilyVersion().contentEquals(this.version)) {
      throw new InvalidTransactionException("Version does not match");
    }

  }

  @Override
  public Collection<String> getNameSpaces() {
    return Arrays.asList(new String[] {this.namespace});
  }

  @Override
  public String getVersion() {
    return this.version;
  }

  @Override
  public String transactionFamilyName() {
    return this.familyName;
  }

}
