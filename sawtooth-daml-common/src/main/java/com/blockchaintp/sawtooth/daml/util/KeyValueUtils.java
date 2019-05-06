package com.blockchaintp.sawtooth.daml.util;

import java.util.List;
import java.util.Map;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmission;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * A class providing utility static methods for use with Sawtooth and DAML
 * KeyValue participant-state.
 */
public final class KeyValueUtils {

  /**
   * Take a DamlSubmission and return the mapping of the DamlStateKeys to sawtooth
   * addresses which will be used as state input to this submission.
   * @param submission the DamlSubmission to be analyzed
   * @param keyCase    the particular type of input key we are interested in. If
   *                   null, all keys.
   * @return a mapping of DamlStateKey to address
   */
  public static Map<DamlStateKey, String> submissionToDamlStateAddress(final DamlSubmission submission,
      final DamlStateKey.KeyCase keyCase) {
    List<DamlStateKey> inputDamlStateList = submission.getInputDamlStateList();
    BiMap<DamlStateKey, String> inputKeys = HashBiMap.create();
    for (DamlStateKey k : inputDamlStateList) {
      if (keyCase == null || k.getKeyCase().equals(keyCase)) {
        inputKeys.put(k, Namespace.makeAddressForType(k));
      }
    }
    return inputKeys;
  }

  /**
   * Take a DamlSubmission and return the mapping of the DamlStateKeys to sawtooth
   * addresses which will be used as state input to this submission.
   * @param submission the DamlSubmission to be analyzed
   * @return a mapping of DamlStateKey to address
   */
  public static Map<DamlStateKey, String> submissionToDamlStateAddress(final DamlSubmission submission) {
    return submissionToDamlStateAddress(submission, null);
  }

  /**
   * Take a DamlSubmission and return the mapping of the DamlLogEntryId to
   * sawtooth addresses which will be used as input to this submission.
   * @param submission the DamlSubmission to be analyzed
   * @return a mapping of DamlLogEntryId to address
   */
  public static Map<DamlLogEntryId, String> submissionToLogAddressMap(final DamlSubmission submission) {
    List<DamlLogEntryId> inputLogEntriesList = submission.getInputLogEntriesList();
    BiMap<DamlLogEntryId, String> inputLogEntryKeys = HashBiMap.create();
    for (DamlLogEntryId id : inputLogEntriesList) {
      inputLogEntryKeys.put(id, Namespace.makeDamlLogEntryAddress(id));
    }
    return inputLogEntryKeys;
  }

  private KeyValueUtils() {
  }

}
