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
package com.blockchaintp.sawtooth.daml.rpc.events;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.blockchaintp.sawtooth.daml.rpc.ZmqEventHandler;

import org.junit.Test;
import org.reactivestreams.Publisher;

import sawtooth.sdk.messaging.Stream;

public class ZmqEventHandlerTest {

  @Test
  public void testGetPublisher() {
    Stream stream = mock(Stream.class);
    ZmqEventHandler dleh = new ZmqEventHandler(stream);
    assertTrue(dleh.makePublisher() instanceof Publisher);
  }

}
