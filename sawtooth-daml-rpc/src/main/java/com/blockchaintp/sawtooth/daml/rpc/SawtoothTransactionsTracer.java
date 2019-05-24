package com.blockchaintp.sawtooth.daml.rpc;

import static spark.Spark.get;
import static spark.Spark.port;

import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation to capture RPC transaction and server it through a REST
 * interface.
 */
public final class SawtoothTransactionsTracer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothTransactionsTracer.class);

  private final ConcurrentLinkedDeque<String> writeTransactionsBuffer;
  private final ConcurrentLinkedDeque<String> readTransactionsBuffer;

  private String takeWriteTransactionsBufferInJson() {
    // TO-DO: This is only a test. We should try to take a batch of tracer and
    // then send to UI as this implementation will mean we have to multiple calls.
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    String item = this.writeTransactionsBuffer.poll();
    int itemCount = 0;
    while (item != null) {
      if (itemCount > 0) {
        sb.append(", ");
      }
      sb.append(item);
      itemCount++;
      item = this.writeTransactionsBuffer.poll();
    }
    sb.append("]");
    return sb.toString();

  }

  private String takeReadTransactionsBufferInJson() {
    // TO-DO: This is only a test. We should try to take a batch of tracer and
    // then send to UI as this implementation will mean we have to multiple calls.
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    String item = this.readTransactionsBuffer.poll();
    int itemCount = 0;
    while (item != null) {
      if (itemCount > 0) {
        sb.append(", ");
      }
      sb.append(item);
      itemCount++;
      item = this.readTransactionsBuffer.poll();
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Construct transaction tracer with a port number.
   * @param portNumber for the RestFul server.
   */
  public SawtoothTransactionsTracer(final int portNumber) {
    LOGGER.info("RESTFul server starts with Port: {}", portNumber);
    writeTransactionsBuffer = new ConcurrentLinkedDeque<String>();
    readTransactionsBuffer = new ConcurrentLinkedDeque<String>();
    port(portNumber);
    initializeRestEndpoints();
  }

  /**
   * Initialising RESTful end points.
   */
  private void initializeRestEndpoints() {
    get("/writeTxns", (req, res) -> {
      String text = this.takeWriteTransactionsBufferInJson();
      res.body(text);
      res.type("application/json");
      return text;
    });
    get("/readTxns", (req, res) -> {
      String text = this.takeReadTransactionsBufferInJson();
      res.body(text);
      res.type("application/json");
      return text;
    });
  }

  /**
   * Put write transactions to the back of the buffer.
   * @param writeTxn element.
   */
  public void putWriteTransactions(final String writeTxn) {
    LOGGER.info("Adding item to write tracer: {}", writeTxn);
    this.writeTransactionsBuffer.offer(writeTxn);
  }

  /**
   * Add to read transactions to buffer.
   * @param readTxn element.
   */
  public void putReadTransactions(final String readTxn) {
    LOGGER.info("Adding item to read tracer: {}", readTxn);
    this.readTransactionsBuffer.offer(readTxn);
  }
}
