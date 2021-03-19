/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.collections.cache;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.collections.SynchronizedLimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitStrategy;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;

/**
 * Cache made around LRU-map with fixed size, removing eldest entries (by added) when the space is
 * over
 *
 * @param <K> Keys type
 * @param <V> Values type
 */
public class LRUCache<K, V> implements Cache<K, V> {

  public static <K, V> LRUCache<K, V> create(int capacity) {
    return new LRUCache<>(Collections.emptyMap(), initEntries -> {
      Map<K, V> map = LimitedMap.create(capacity);
      map.putAll(initEntries);
      return map;
    });
  }

  public static <K, V> LRUCache<K, V> createHashMapBacked(int capacity) {
    return new LRUCache<>(Collections.emptyMap(),
        initEntries -> {
          Map<K, V> map = SynchronizedLimitedMap
              .create(capacity, LimitStrategy.DROP_OLDEST_ELEMENT);
          synchronized (initEntries) {
            map.putAll(initEntries);
          }
          return map;
        });
  }

  private final Function<Map<K, V>, Map<K, V>> backStorageCopier;
  private final Map<K, V> cacheData;

  private LRUCache(Map<K, V> initialCachedContent, Function<Map<K, V>, Map<K, V>> backStorageCopier) {

    this.backStorageCopier = backStorageCopier;
    this.cacheData = backStorageCopier.apply(initialCachedContent);
  }

  @Override
  public Cache<K, V> copy() {
    return new LRUCache<>(cacheData, backStorageCopier);
  }

  /**
   * Queries value from the cache. If it's not found there, fallback function is used to calculate
   * value. After calculation result is put in cache and returned.
   *
   * @param key Key to query
   * @param fallback Fallback function for calculation of the result in case of missed cache entry
   * @return expected value result for provided key
   */
  @Override
  public V get(K key, Function<K, V> fallback) {
    V result = cacheData.get(key);

    if (result == null) {
      result = fallback.apply(key);
      if (result != null) {
        cacheData.put(key, result);
      }
    }

    return result;
  }

  @Override
  public Optional<V> getCached(K key) {
    return Optional.ofNullable(cacheData.get(key));
  }

  @Override
  public void invalidate(K key) {
    cacheData.remove(key);
  }

  @Override
  public void clear() {
    cacheData.clear();
  }

  @Override
  public int size() {
    return cacheData.size();
  }
}
