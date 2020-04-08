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

package tech.pegasys.artemis.storage.server.rocksdb.core;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import tech.pegasys.artemis.storage.server.rocksdb.schema.RocksDbColumn;

class RocksDbIterator<TKey, TValue> implements Iterator<ColumnEntry<TKey, TValue>>, AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  private final RocksDbColumn<TKey, TValue> column;
  private final RocksIterator rocksIt;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private RocksDbIterator(final RocksDbColumn<TKey, TValue> column, final RocksIterator rocksIt) {
    this.column = column;
    this.rocksIt = rocksIt;
  }

  public static <K, V> RocksDbIterator<K, V> create(
      final RocksDbColumn<K, V> column, final RocksIterator rocksIt) {
    return new RocksDbIterator<>(column, rocksIt);
  }

  @Override
  public boolean hasNext() {
    return rocksIt.isValid();
  }

  @Override
  public ColumnEntry<TKey, TValue> next() {
    try {
      rocksIt.status();
    } catch (final RocksDBException e) {
      LOG.error("RocksDbEntryIterator encountered a problem while iterating.", e);
    }
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    final TKey key = column.getKeySerializer().deserialize(rocksIt.key());
    final TValue value = column.getValueSerializer().deserialize(rocksIt.value());
    final ColumnEntry<TKey, TValue> entry = ColumnEntry.create(key, value);
    rocksIt.next();
    return entry;
  }

  public Stream<ColumnEntry<TKey, TValue>> toStream() {
    final Spliterator<ColumnEntry<TKey, TValue>> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false).onClose(this::close);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      rocksIt.close();
    }
  }
}
