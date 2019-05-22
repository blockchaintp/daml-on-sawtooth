package com.blockchaintp.sawtooth.daml.rpc.rest;

import static spark.Spark.get;
import static spark.Spark.port;

/**
 * An implementation to capture RPC transaction and server it through a REST interface.
 *
 */
public final class TraceTransactions {

  private TraceTransactions() {
  }

  public static void setPort(int portNumber) {
	  port(portNumber);
  }
  
  /**
   * Get hello endpoint.
   */
   public static void getHello() {
      get("/hello", (req, res) -> "Hello World");
   }
}
