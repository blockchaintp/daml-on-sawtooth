package com.blockchaintp.timekeeper.util;
import static org.junit.Assert.*;

import org.junit.Test;

import com.blockchaintp.sawtooth.timekeeper.util.Namespace;

public class NamespaceTest {

  @Test
  public void testMakeAddress() {
    String testString=Namespace.makeAddress(Namespace.getNameSpace(), "somerandomString");
    assertTrue(testString.length()==70);
  }

}
