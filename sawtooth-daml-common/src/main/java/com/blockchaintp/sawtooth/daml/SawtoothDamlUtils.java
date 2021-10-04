package com.blockchaintp.sawtooth.daml;

import java.util.Collection;
import java.util.List;

import com.google.protobuf.ByteString;
import com.blockchaintp.sawtooth.SawtoothClientUtils;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperation;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.keymanager.KeyManager;
import sawtooth.sdk.protobuf.Transaction;
import scala.Tuple4;

/**
 * A collection of useful functions for DAML and Sawtooth interactions.
 */
public final class SawtoothDamlUtils {

  private SawtoothDamlUtils() {
  }

  /**
   * Given a DamlOperatinBatch create the Sawtooth Transaction.
   *
   * @param keyManager the keyManager to sign the transaction
   * @param batch the DamlOperationBatch
   * @param inputAddresses the sawtooth input addresses
   * @param outputAddresses the sawtooth output addresses
   * @param dependentTransactions any dependent transactions
   * @return the encoded transaction
   */
  public static Transaction makeSawtoothTransaction(final KeyManager keyManager,
      final DamlOperationBatch batch, final Collection<String> inputAddresses,
      final Collection<String> outputAddresses, final List<String> dependentTransactions) {
    return SawtoothClientUtils.makeSawtoothTransaction(keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, inputAddresses, outputAddresses, dependentTransactions,
        batch.toByteString());
  }

  /**
   * Transform the provided DamlOperationBatch into a tuple suitable for
   * use in DAML apis.
   *
   * @param batch the daml operation batch
   * @return the tuple
   */
   public static Tuple4<String, String, ByteString, DamlSubmissionBatch> damlOperationBatchToDamlSubmissionBatch(
       final DamlOperationBatch batch) {
     final DamlSubmissionBatch.Builder builder = DamlSubmissionBatch.newBuilder();
     boolean hasTx = false;
     String participantId = null;
     String correlationId = null;
     ByteString lastLogEntryId = null;
     for (final DamlOperation op : batch.getOperationsList()) {
       participantId = op.getSubmittingParticipant();
       correlationId = op.getCorrelationId();
       if (op.hasTransaction()) {
         hasTx = true;
         final ByteString envelope = op.getTransaction().getSubmission();
         lastLogEntryId = op.getTransaction().getLogEntryId();
         final CorrelatedSubmission correlatedSubmission = DamlSubmissionBatch.CorrelatedSubmission
            .newBuilder().setCorrelationId(op.getCorrelationId()).setSubmission(envelope).build();
        builder.addSubmissions(correlatedSubmission);
      }
    }
    if (hasTx) {
      return Tuple4.apply(participantId, correlationId, lastLogEntryId, builder.build());
    } else {
      return null;
    }
  }
}
