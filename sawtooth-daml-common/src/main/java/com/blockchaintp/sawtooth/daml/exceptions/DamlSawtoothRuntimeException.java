/* Copyright © 2023 Paravela Limited
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
package com.blockchaintp.sawtooth.daml.exceptions;

/**
 * A Sawtooth writer service exception handler to unify exceptions generated by
 * dependencies such as those generated by sawtooth-java-sdk.
 */
public class DamlSawtoothRuntimeException extends RuntimeException {

  private static final long serialVersionUID = 3437463937090384297L;

  /**
   * Construct exception from a throwable exception.
   *
   * @param throwable exception.
   */
  public DamlSawtoothRuntimeException(final Throwable throwable) {
    super(throwable);
  }

  /**
   * Construct exception from a message and a throwable exception.
   *
   * @param message describing the nature of the exception.
   * @param throwable exception.
   */
  public DamlSawtoothRuntimeException(final  String message, final Throwable throwable) {
    super(message, throwable);
  }

  /**
   * Construct an undefined exception class.
   */
  public DamlSawtoothRuntimeException() {
    super("Undefined Exception");
  }

  /**
   * Construct an exception class with a defined message.
   *
   * @param message to be displayed.
   */
  public DamlSawtoothRuntimeException(final String message) {
    super(message);
  }

}
