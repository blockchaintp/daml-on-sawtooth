package com.blockchaintp.sawtooth.daml;

import java.util.Collection;
import java.util.List;
import com.blockchaintp.utils.SawtoothClientUtils;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmissionBatch;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmissionBatch.CorrelatedSubmission;
import com.google.protobuf.ByteString;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperation;
import com.blockchaintp.sawtooth.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.utils.KeyManager;
import sawtooth.sdk.protobuf.Transaction;
import scala.Tuple4;

public final class SawtoothDamlUtils {

  private SawtoothDamlUtils() {
  }

  public static Transaction makeSawtoothTransaction(final KeyManager keyManager,
      final DamlOperationBatch batch, final Collection<String> inputAddresses,
      final Collection<String> outputAddresses, final List<String> dependentTransactions) {
    return SawtoothClientUtils.makeSawtoothTransaction(keyManager, Namespace.DAML_FAMILY_NAME,
        Namespace.DAML_FAMILY_VERSION_1_0, inputAddresses, outputAddresses, dependentTransactions,
        batch.toByteString());
  }

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
