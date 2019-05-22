package com.blockchaintp.sawtooth.daml.rpc;

import static spark.Spark.get;
import static spark.Spark.port;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation to capture RPC transaction and server it through a REST
 * interface.
 *
 */
public final class SawtoothTransactionsTracer {

   private static final Logger LOGGER = LoggerFactory.getLogger(SawtoothTransactionsTracer.class);

   private static BlockingQueue<String> writeTransactionsBuffer = new LinkedBlockingQueue<String>();
   private static BlockingQueue<String> readTransactionsBuffer = new LinkedBlockingQueue<String>();

   private static String takeWriteTransactionsBufferInJson() {
      // TO-DO: This is only a test. We should try to take a batch of tracer and
      // then send to UI as this implementation will mean we have to multiple calls.
      try {
         String item = SawtoothTransactionsTracer.writeTransactionsBuffer.take();
         return "{\"Item\":" + item + "}";
      } catch (InterruptedException e) {
         return "{\"Item\":null}";
      }
   }

   private static String takeReadTransactionsBufferInJson() {
      // TO-DO: This is only a test. We should try to take a batch of tracer and
      // then send to UI as this implementation will mean we have to multiple calls.
      try {
         String item = SawtoothTransactionsTracer.readTransactionsBuffer.take();
         return "{\"Item\":" + item + "}";
      } catch (InterruptedException e) {
         return "{\"Item\":null}";
      }
   }

   /**
    * Construct transaction tracer with a port number.
    * @param portNumber for the RestFul server.
    */
   public SawtoothTransactionsTracer(final int portNumber) {
      LOGGER.info("RESTFul server starts with Port: " + String.valueOf(portNumber));
      port(portNumber);
   }

   /**
    * Initialising RESTful end points.
    */
   public void initializeRestEndpoints() {
      get("/writeTxns", (req, res) -> {
         return SawtoothTransactionsTracer.takeWriteTransactionsBufferInJson();
      });
      get("/readtxns", (req, res) -> {
         return SawtoothTransactionsTracer.takeReadTransactionsBufferInJson();
      });
   }

   /**
    * Put write transactions to the back of the buffer.
    * @param writeTxn element.
    */
   public void putWriteTransactions(final String writeTxn) {
      try {
         SawtoothTransactionsTracer.writeTransactionsBuffer.put(writeTxn);
      } catch (InterruptedException e) {
         // Can't do anything about it
      }
   }

   /**
    * Add to read transactions to buffer.
    * @param readTxn element.
    */
   public void putReadTransactions(final String readTxn) {
      try {
         SawtoothTransactionsTracer.readTransactionsBuffer.put(readTxn);
      } catch (InterruptedException e) {
         // Can't do anything about it
      }
   }
}
