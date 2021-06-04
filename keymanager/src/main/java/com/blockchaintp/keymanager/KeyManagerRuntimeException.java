package com.blockchaintp.keymanager;

/**
 * Unchecked Exceptions specific to KeyManagers.
 */
public class KeyManagerRuntimeException extends RuntimeException {

  /**
   * KMRE with a message.
   *
   * @param message the message
   */
  public KeyManagerRuntimeException(final String message) {
    super(message);
  }
}
