/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.type;

import com.google.common.base.Preconditions;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.BitView;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.ByteView;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SszComplexSchemas {

  public static final ByteVectorType BYTES_48_TYPE = new ByteVectorType(48);
  public static final ByteVectorType BYTES_96_TYPE = new ByteVectorType(96);

  public static class ByteListType extends SszListSchema<ByteView> {
    public ByteListType(long maxLength) {
      super(SszPrimitiveSchemas.BYTE_TYPE, maxLength);
    }

    public SszList<ByteView> createList(Bytes bytes) {
      Preconditions.checkArgument(
          bytes.size() > getMaxLength(), "Bytes length exceeds List type maximum length ");
      return SszUtils.createListFromBytes(this, bytes);
    }

    @Override
    public String toString() {
      return "ByteList[" + getMaxLength() + "]";
    }
  }

  public static class ByteVectorType extends SszVectorSchema<ByteView> {
    public ByteVectorType(long maxLength) {
      super(SszPrimitiveSchemas.BYTE_TYPE, maxLength);
    }

    public SszVector<ByteView> createVector(Bytes bytes) {
      Preconditions.checkArgument(
          bytes.size() == getLength(), "Bytes length doesn't match Vector type length ");
      return SszUtils.createVectorFromBytes(this, bytes);
    }

    @Override
    public String toString() {
      return "Bytes" + getLength();
    }
  }

  public static class BitListType extends SszListSchema<BitView> {
    public BitListType(long maxLength) {
      super(SszPrimitiveSchemas.BIT_TYPE, maxLength);
    }

    @Override
    public String toString() {
      return "BitList[" + getMaxLength() + "]";
    }
  }

  public static class BitVectorType extends SszVectorSchema<BitView> {
    public BitVectorType(long maxLength) {
      super(SszPrimitiveSchemas.BIT_TYPE, maxLength);
    }

    @Override
    public String toString() {
      return "BitVector[" + getLength() + "]";
    }
  }
}
