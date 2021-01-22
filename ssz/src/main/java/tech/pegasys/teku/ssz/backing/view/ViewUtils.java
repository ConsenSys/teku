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

package tech.pegasys.teku.ssz.backing.view;

import com.google.common.collect.Streams;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewWrite;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.sos.SszReader;

/** Handy view tool methods */
public class ViewUtils {

  public static <C, V extends ViewRead> ListViewRead<V> toListView(
      ListViewType<V> type, Iterable<C> list, Function<C, V> converter) {
    return toListView(type, Streams.stream(list).map(converter).collect(Collectors.toList()));
  }

  public static <V extends ViewRead> ListViewRead<V> toListView(
      ListViewType<V> type, Iterable<V> list) {
    ListViewWrite<V> ret = type.getDefault().createWritableCopy();
    list.forEach(ret::append);
    return ret.commitChanges();
  }

  public static <C, V extends ViewRead> VectorViewRead<V> toVectorView(
      VectorViewType<V> type, Iterable<C> list, Function<C, V> converter) {
    return toVectorView(type, Streams.stream(list).map(converter).collect(Collectors.toList()));
  }

  public static <V extends ViewRead> VectorViewRead<V> toVectorView(
      VectorViewType<V> type, Iterable<V> list) {
    VectorViewWrite<V> ret = type.getDefault().createWritableCopy();
    int idx = 0;
    for (V v : list) {
      if (idx >= type.getLength()) {
        throw new IllegalArgumentException("List size exceeds vector size");
      }
      ret.set(idx, v);
    }
    return ret.commitChanges();
  }

  /** Creates immutable vector of bytes with size `bytes.size()` from {@link Bytes} value */
  public static VectorViewRead<ByteView> createVectorFromBytes(Bytes bytes) {
    VectorViewType<ByteView> type = new VectorViewType<>(BasicViewTypes.BYTE_TYPE, bytes.size());
    return type.sszDeserialize(SszReader.fromBytes(bytes));
  }

  /** Retrieve bytes from vector of bytes to a {@link Bytes} instance */
  public static Bytes getAllBytes(VectorViewRead<ByteView> vector) {
    return vector.sszSerialize();
  }

  /**
   * Creates immutable list of bits with size `bitlist.size()` and maxSize = `bitlist.getMaxSize()`
   * from {@link Bitlist} value
   */
  public static ListViewRead<BitView> createBitlistView(Bitlist bitlist) {
    ListViewWrite<BitView> viewWrite =
        new ListViewType<>(BasicViewTypes.BIT_TYPE, bitlist.getMaxSize())
            .getDefault()
            .createWritableCopy();
    for (int i = 0; i < bitlist.getCurrentSize(); i++) {
      viewWrite.append(BitView.viewOf(bitlist.getBit(i)));
    }
    return viewWrite.commitChanges();
  }

  /** Converts list of bits to {@link Bitlist} value */
  public static Bitlist getBitlist(ListViewRead<BitView> bitlistView) {
    return Bitlist.fromSszBytes(bitlistView.sszSerialize(), bitlistView.getType().getMaxLength());
  }

  /** Creates immutable vector of bits with size `bitvector.size()` from {@link Bitvector} value */
  public static VectorViewRead<BitView> createBitvectorView(Bitvector bitvector) {
    VectorViewWrite<BitView> viewWrite =
        new VectorViewType<>(BasicViewTypes.BIT_TYPE, bitvector.getSize())
            .getDefault()
            .createWritableCopy();
    for (int i = 0; i < bitvector.getSize(); i++) {
      viewWrite.set(i, BitView.viewOf(bitvector.getBit(i)));
    }
    return viewWrite.commitChanges();
  }

  /** Converts vector of bits to {@link Bitvector} value */
  public static Bitvector getBitvector(VectorViewRead<BitView> vectorView) {
    int[] bitIndexes =
        IntStream.range(0, vectorView.size()).filter(i -> vectorView.get(i).get()).toArray();
    return new Bitvector(vectorView.size(), bitIndexes);
  }
}
