/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import static com.google.common.base.Preconditions.checkArgument;

import io.libp2p.pubsub.FastIdSeenCache;
import io.libp2p.pubsub.PubsubMessage;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * The "fast id" under which the gossip seen cache indexes a pubsub message: the SHA-256 of the
 * message's wire bytes, as returned by {@link PubsubMessage#messageSha256()}.
 *
 * <p>{@link FastIdSeenCache} looks this key up in a {@code HashMap} for every inbound copy of every
 * message, including the duplicates that every mesh peer forwards, and that map holds every message
 * seen within the gossip seen TTL (millions of entries on a well connected node). The key is built
 * to make that lookup as cheap as possible:
 *
 * <ul>
 *   <li>{@link #hashCode()} is the big-endian {@code int} formed by the first four bytes of the
 *       digest, computed once in the constructor. A SHA-256 is already uniformly distributed, so
 *       the bytes need no mixing, and the map's hash spreading is applied by the map itself.
 *   <li>{@link #equals(Object)} compares the two digest arrays with {@link Arrays#equals(byte[],
 *       byte[])}, which HotSpot intrinsifies into a few wide compares, after an {@code instanceof}
 *       check against this final class.
 * </ul>
 *
 * <p>A general purpose byte container (for example tuweni's {@code Bytes}) hashes and compares byte
 * by byte through virtual calls, which showed up as a measurable share of the single gossip event
 * thread at several hundred peers.
 *
 * <p>Safety: the key only carries the digest; collision resistance comes from SHA-256 itself, so
 * two messages with different wire bytes get different keys exactly as before. The warning on
 * {@link PubsubMessage#hashCode()} about keeping {@code HashMap} keys collision free still holds
 * and is still met, because a {@code hashCode()} collision requires the first four digest bytes to
 * match, which a remote peer can only cause by brute force, and even then the full digest still
 * distinguishes the entries. Keys are also {@link Comparable} (unsigned lexicographic order of the
 * digest) so that a {@code HashMap} bin which treeifies under such collisions keeps logarithmic
 * lookups instead of scanning the whole bin. The array is wrapped without copying and must not be
 * modified by the caller.
 */
final class FastMessageId implements Comparable<FastMessageId> {

  static final int DIGEST_LENGTH = 32;

  private final byte[] digest;
  private final int hashCode;

  /**
   * Wraps a SHA-256 digest without copying it.
   *
   * @param digest the 32-byte digest of the message's wire bytes, owned by this key from now on
   */
  FastMessageId(final byte[] digest) {
    checkArgument(
        digest.length == DIGEST_LENGTH,
        "Expected a %s byte SHA-256 digest but got %s bytes",
        DIGEST_LENGTH,
        digest.length);
    this.digest = digest;
    this.hashCode =
        ((digest[0] & 0xff) << 24)
            | ((digest[1] & 0xff) << 16)
            | ((digest[2] & 0xff) << 8)
            | (digest[3] & 0xff);
  }

  static FastMessageId of(final PubsubMessage message) {
    return new FastMessageId(message.messageSha256());
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof FastMessageId that && Arrays.equals(digest, that.digest);
  }

  @Override
  public int compareTo(final FastMessageId other) {
    return Arrays.compareUnsigned(digest, other.digest);
  }

  @Override
  public String toString() {
    return "FastMessageId{0x" + HexFormat.of().formatHex(digest) + "}";
  }
}
