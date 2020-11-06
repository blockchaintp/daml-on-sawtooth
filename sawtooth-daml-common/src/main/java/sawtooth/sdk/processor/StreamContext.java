/* Copyright 2016, 2017 Intel Corporation
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
------------------------------------------------------------------------------*/

package sawtooth.sdk.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import sawtooth.sdk.messaging.Future;
import sawtooth.sdk.messaging.Stream;
import sawtooth.sdk.processor.exceptions.InternalError;
import sawtooth.sdk.processor.exceptions.InvalidTransactionException;
import sawtooth.sdk.processor.exceptions.ValidatorConnectionError;
import sawtooth.sdk.protobuf.Event;
import sawtooth.sdk.protobuf.Event.Attribute;
import sawtooth.sdk.protobuf.Event.Builder;
import sawtooth.sdk.protobuf.Message;
import sawtooth.sdk.protobuf.TpEventAddRequest;
import sawtooth.sdk.protobuf.TpEventAddResponse;
import sawtooth.sdk.protobuf.TpReceiptAddDataRequest;
import sawtooth.sdk.protobuf.TpReceiptAddDataResponse;
import sawtooth.sdk.protobuf.TpStateDeleteRequest;
import sawtooth.sdk.protobuf.TpStateDeleteResponse;
import sawtooth.sdk.protobuf.TpStateEntry;
import sawtooth.sdk.protobuf.TpStateGetRequest;
import sawtooth.sdk.protobuf.TpStateGetResponse;
import sawtooth.sdk.protobuf.TpStateSetRequest;
import sawtooth.sdk.protobuf.TpStateSetResponse;

/**
 * Client state that interacts with the context manager through Stream
 * networking.
 */
public class StreamContext implements Context {

  /**
   * The stream networking for this class.
   */
  private Stream stream;

  /**
   * The id for a specific context.
   */
  private String contextId;

  /**
   * How long to wait for a networking response.
   */
  private static final int TIME_OUT = 10;

  /**
   * The constructor for this class.
   * @param myStream    a networking stream
   * @param myContextId a context id
   */
  public StreamContext(final Stream myStream, final String myContextId) {
    this.stream = myStream;
    this.contextId = myContextId;
  }

  /**
   * Make a Get request on a specific context specified by contextId.
   * @param addresses a collection of address Strings
   * @return Map where the keys are addresses, values Bytestring
   * @throws InternalError               something went wrong processing
   *                                     transaction
   * @throws InvalidTransactionException an invalid transaction was encountered
   */
  @Override
  public final Map<String, ByteString> getState(final Collection<String> addresses)
      throws InternalError, InvalidTransactionException {
    TpStateGetRequest getRequest = TpStateGetRequest.newBuilder().addAllAddresses(addresses)
        .setContextId(this.contextId).build();
    Future future = stream.send(Message.MessageType.TP_STATE_GET_REQUEST, getRequest.toByteString());
    TpStateGetResponse getResponse = null;
    try {
      getResponse = TpStateGetResponse.parseFrom(future.getResult(TIME_OUT));
    } catch (InterruptedException iee) {
      throw new InternalError(iee.toString());
    } catch (InvalidProtocolBufferException ipbe) {
      // server didn't respond with a GetResponse
      throw new InternalError(ipbe.toString());
    } catch (ValidatorConnectionError vce) {
      throw new InternalError(vce.toString());
    } catch (Exception e) {
      throw new InternalError(e.toString());
    }
    Map<String, ByteString> results = new HashMap<String, ByteString>();
    if (getResponse != null) {
      if (getResponse.getStatus() == TpStateGetResponse.Status.AUTHORIZATION_ERROR) {
        throw new InvalidTransactionException("Tried to get unauthorized address " + addresses.toString());
      }
      for (TpStateEntry entry : getResponse.getEntriesList()) {
        results.put(entry.getAddress(), entry.getData());
      }
    }

    return results;
  }

  /**
   * Make a Set request on a specific context specified by contextId.
   * @param addressValuePairs A collection of Map.Entry's
   * @return addressesThatWereSet, A collection of address Strings that were set
   * @throws InternalError               something went wrong processing
   *                                     transaction
   * @throws InvalidTransactionException an invalid transaction was encountered
   */
  @Override
  public final Collection<String> setState(final Collection<java.util.Map.Entry<String, ByteString>> addressValuePairs)
      throws InternalError, InvalidTransactionException {
    ArrayList<TpStateEntry> entryArrayList = new ArrayList<TpStateEntry>();
    for (Map.Entry<String, ByteString> entry : addressValuePairs) {
      TpStateEntry ourTpStateEntry = TpStateEntry.newBuilder().setAddress(entry.getKey()).setData(entry.getValue())
          .build();
      entryArrayList.add(ourTpStateEntry);
    }
    TpStateSetRequest setRequest = TpStateSetRequest.newBuilder().addAllEntries(entryArrayList)
        .setContextId(this.contextId).build();
    Future future = stream.send(Message.MessageType.TP_STATE_SET_REQUEST, setRequest.toByteString());
    TpStateSetResponse setResponse = null;
    try {
      setResponse = TpStateSetResponse.parseFrom(future.getResult(TIME_OUT));
    } catch (InterruptedException iee) {
      throw new InternalError(iee.toString());

    } catch (InvalidProtocolBufferException ipbe) {
      // server didn't respond with a SetResponse
      throw new InternalError(ipbe.toString());
    } catch (ValidatorConnectionError vce) {
      throw new InternalError(vce.toString());
    } catch (Exception e) {
      throw new InternalError(e.toString());
    }
    ArrayList<String> addressesThatWereSet = new ArrayList<String>();
    if (setResponse != null) {
      if (setResponse.getStatus() == TpStateSetResponse.Status.AUTHORIZATION_ERROR) {
        throw new InvalidTransactionException("Tried to set unauthorized address " + addressValuePairs.toString());
      }
      for (String address : setResponse.getAddressesList()) {
        addressesThatWereSet.add(address);
      }
    }

    return addressesThatWereSet;
  }

  @Override
  public final Collection<String> deleteState(final Collection<String> addresses)
      throws InternalError, InvalidTransactionException {
    TpStateDeleteRequest delRequest = TpStateDeleteRequest.newBuilder().addAllAddresses(addresses)
        .setContextId(this.contextId).build();
    Future future = stream.send(Message.MessageType.TP_STATE_DELETE_REQUEST, delRequest.toByteString());
    TpStateDeleteResponse delResponse = null;
    try {
      delResponse = TpStateDeleteResponse.parseFrom(future.getResult(TIME_OUT));
    } catch (InterruptedException iee) {
      throw new InternalError(iee.toString());
    } catch (InvalidProtocolBufferException ipbe) {
      // server didn't respond with a DeleteResponse
      throw new InternalError(ipbe.toString());
    } catch (ValidatorConnectionError vce) {
      throw new InternalError(vce.toString());
    } catch (Exception e) {
      throw new InternalError(e.toString());
    }
    List<String> results = new ArrayList<>();
    if (delResponse != null) {
      if (delResponse.getStatus() == TpStateDeleteResponse.Status.AUTHORIZATION_ERROR) {
        throw new InvalidTransactionException("Tried to delete unauthorized address " + addresses.toString());
      }
      for (String entry : delResponse.getAddressesList()) {
        results.add(entry);
      }
    }

    return results;
  }

  @Override
  public final void addReceiptData(final ByteString data) throws InternalError {
    TpReceiptAddDataRequest addDataRequest = TpReceiptAddDataRequest.newBuilder().setContextId(contextId).setData(data)
        .build();
    Future future = stream.send(Message.MessageType.TP_RECEIPT_ADD_DATA_REQUEST, addDataRequest.toByteString());
    TpReceiptAddDataResponse addDataResponse = null;
    try {
      addDataResponse = TpReceiptAddDataResponse.parseFrom(future.getResult(TIME_OUT));
    } catch (InterruptedException iee) {
      throw new InternalError(iee.toString());
    } catch (InvalidProtocolBufferException ipbe) {
      // server didn't respond with a ReceiptAddResponse
      throw new InternalError(ipbe.toString());
    } catch (ValidatorConnectionError vce) {
      throw new InternalError(vce.toString());
    } catch (Exception e) {
      throw new InternalError(e.toString());
    }
    if (addDataResponse != null) {
      if (addDataResponse.getStatus() == TpReceiptAddDataResponse.Status.ERROR) {
        throw new InternalError(String.format("Failed to add receipt data %s", data));
      }
    }
  }

  @Override
  public final void addEvent(final String eventType, final Collection<Entry<String, String>> attributes,
      final ByteString data) throws InternalError {
    List<Attribute> attList = new ArrayList<>();
    for (Map.Entry<String, String> entry : attributes) {
      Attribute att = Attribute.newBuilder().setKey(entry.getKey()).setValue(entry.getValue()).build();
      attList.add(att);
    }
    Builder evtBuilder = Event.newBuilder().addAllAttributes(attList).setEventType(eventType);
    if (data != null) {
      evtBuilder.setData(data);
    }
    Event evt = evtBuilder.build();
    TpEventAddRequest evtAddRequest = TpEventAddRequest.newBuilder().setContextId(contextId).setEvent(evt).build();

    Future future = stream.send(Message.MessageType.TP_EVENT_ADD_REQUEST, evtAddRequest.toByteString());
    TpEventAddResponse evtAddResponse = null;
    try {
      evtAddResponse = TpEventAddResponse.parseFrom(future.getResult(TIME_OUT));
    } catch (InterruptedException iee) {
      throw new InternalError(iee.toString());
    } catch (InvalidProtocolBufferException ipbe) {
      // server didn't respond with a EventAddResponse
      throw new InternalError(ipbe.toString());
    } catch (ValidatorConnectionError vce) {
      throw new InternalError(vce.toString());
    } catch (Exception e) {
      throw new InternalError(e.toString());
    }
    if (evtAddResponse != null) {
      if (evtAddResponse.getStatus() == TpEventAddResponse.Status.ERROR) {
        throw new InternalError(String.format("Failed to add event %s, %s, %s", eventType, attributes, data));
      }
    }
  }

}
