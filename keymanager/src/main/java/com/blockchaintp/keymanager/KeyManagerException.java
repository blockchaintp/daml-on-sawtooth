package com.blockchaintp.keymanager;

/**
 * Checked Exceptions specific to KeyManagers.
 */
public class KeyManagerException extends Exception {

  /**
   * KME with a message.
   *
   * @param message the message
   */
  public KeyManagerException(final String message) {
    super(message);
  }
}
