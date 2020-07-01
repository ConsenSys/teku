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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.assertThatProtoArrayMatches;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoArraySnaphot;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.StateStorageMode;

public class ProtoArrayStorageTest {
  private ProtoArrayStorage protoArrayStorage;

  @ParameterizedTest
  @MethodSource("getStorageSystems")
  public void shouldReturnEmptyIfThereIsNoProtoArrayOnDisk(StorageSystem storageSystem)
      throws Exception {
    storageSystem.chainUpdater().initializeGenesis();
    protoArrayStorage = storageSystem.createProtoArrayStorage();
    SafeFuture<Optional<ProtoArray>> future = protoArrayStorage.getProtoArray();
    assertThat(future.isDone()).isTrue();
    assertThat(future.get().isPresent()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("getStorageSystems")
  public void shouldReturnSameSetOfNodes(StorageSystem storageSystem) throws Exception {
    storageSystem.chainUpdater().initializeGenesis();
    protoArrayStorage = storageSystem.createProtoArrayStorage();

    // init ProtoArray
    ProtoArray protoArray =
        new ProtoArray(
            10000,
            UnsignedLong.valueOf(100),
            UnsignedLong.valueOf(99),
            new ArrayList<>(),
            new HashMap<>());

    // add block 1
    protoArray.onBlock(
        UnsignedLong.valueOf(10000),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        Bytes32.ZERO,
        UnsignedLong.valueOf(101),
        UnsignedLong.valueOf(100));

    // add block 2
    protoArray.onBlock(
        UnsignedLong.valueOf(10001),
        Bytes32.fromHexString("0x1234"),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        UnsignedLong.valueOf(101),
        UnsignedLong.valueOf(100));

    ProtoArraySnaphot protoArraySnaphot = ProtoArraySnaphot.save(protoArray);
    protoArrayStorage.onProtoArrayUpdate(protoArraySnaphot);

    SafeFuture<Optional<ProtoArray>> future = protoArrayStorage.getProtoArray();
    assertThat(future.isDone()).isTrue();
    assertThat(future.get().isPresent()).isTrue();

    ProtoArray protoArrayFromDisk = protoArrayStorage.getProtoArray().get().get();
    assertThatProtoArrayMatches(protoArray, protoArrayFromDisk);
  }

  @ParameterizedTest
  @MethodSource("getStorageSystems")
  public void shouldOverwriteTheProtoArray(StorageSystem storageSystem) throws Exception {
    storageSystem.chainUpdater().initializeGenesis();
    protoArrayStorage = storageSystem.createProtoArrayStorage();

    // init ProtoArray
    ProtoArray protoArray1 =
        new ProtoArray(
            10000,
            UnsignedLong.valueOf(100),
            UnsignedLong.valueOf(99),
            new ArrayList<>(),
            new HashMap<>());

    ProtoArraySnaphot protoArraySnaphot1 = ProtoArraySnaphot.save(protoArray1);
    protoArrayStorage.onProtoArrayUpdate(protoArraySnaphot1);

    ProtoArray protoArray2 =
        new ProtoArray(
            10000,
            UnsignedLong.valueOf(98),
            UnsignedLong.valueOf(97),
            new ArrayList<>(),
            new HashMap<>());

    // add block 1
    protoArray2.onBlock(
        UnsignedLong.valueOf(10000),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        Bytes32.ZERO,
        UnsignedLong.valueOf(101),
        UnsignedLong.valueOf(100));

    // add block 2
    protoArray2.onBlock(
        UnsignedLong.valueOf(10001),
        Bytes32.fromHexString("0x1234"),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        UnsignedLong.valueOf(101),
        UnsignedLong.valueOf(100));

    ProtoArraySnaphot protoArraySnaphot2 = ProtoArraySnaphot.save(protoArray2);
    protoArrayStorage.onProtoArrayUpdate(protoArraySnaphot2);

    ProtoArray protoArrayFromDisk = protoArrayStorage.getProtoArray().get().get();
    assertThatProtoArrayMatches(protoArray2, protoArrayFromDisk);
  }

  public static Stream<Arguments> getStorageSystems() {
    final StorageSystem storageSystemV3 =
        InMemoryStorageSystem.createEmptyV3StorageSystem(StateStorageMode.ARCHIVE);
    final StorageSystem storageSystemV4 =
        InMemoryStorageSystem.createEmptyV4StorageSystem(StateStorageMode.ARCHIVE, 1);
    final List<StorageSystem> encodings = List.of(storageSystemV3, storageSystemV4);
    return encodings.stream().map(Arguments::of);
  }
}
