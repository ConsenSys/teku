/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.ssz.access.basic;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZException;
import org.ethereum.beacon.ssz.access.SSZBasicAccessor;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.visitor.SSZReader;
import org.ethereum.beacon.ssz.visitor.SSZWriter;

/** {@link SSZBasicAccessor} for {@link String} */
public class StringPrimitive implements SSZBasicAccessor {

  private static Set<String> supportedTypes = new HashSet<>();
  private static Set<Class> supportedClassTypes = new HashSet<>();

  static {
    supportedTypes.add("string");
  }

  static {
    supportedClassTypes.add(String.class);
  }

  @Override
  public int getSize(SSZField field) {
    return -1;
  }

  @Override
  public Set<String> getSupportedSSZTypes() {
    return supportedTypes;
  }

  @Override
  public Set<Class> getSupportedClasses() {
    return supportedClassTypes;
  }

  @Override
  public void encode(Object value, SSZField field, OutputStream result) {
    String sValue = (String) value;
    Bytes res = SSZWriter.encodeString(sValue);
    try {
      result.write(res.toArrayUnsafe());
    } catch (IOException e) {
      String error = String.format("Failed to write string value \"%s\" to stream", sValue);
      throw new SSZException(error, e);
    }
  }

  @Override
  public Object decode(SSZField field, SSZReader reader) {
    return reader.readString();
  }
}
