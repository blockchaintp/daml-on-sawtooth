package com.blockchaintp.sawtooth.daml.processor.impl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of a basic LRU Cache.
 * @param <K> Type of the Key
 * @param <V> Type of the value
 */
public final class LRUCache<K, V> extends LinkedHashMap<K, V> {

  private static final long serialVersionUID = 5685102208717817328L;
  private static final float DEFAULT_FACTOR = 0.75f;
  private int size;

  /**
   * Construct an LRU Cache of the given capacity.
   * @param capacity the maximum size of the cache
   */
  public LRUCache(final int capacity) {
    super(capacity, DEFAULT_FACTOR, true);
    this.size = capacity;
  }

  @Override
  protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
    return size() > this.size;
  }
}
