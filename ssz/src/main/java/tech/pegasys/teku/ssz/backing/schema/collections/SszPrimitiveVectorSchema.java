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

package tech.pegasys.teku.ssz.backing.schema.collections;

import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.collections.SszPrimitiveVector;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.impl.SszPrimitiveVectorSchemaImpl;

public interface SszPrimitiveVectorSchema<
        ElementT,
        SszElementT extends SszPrimitive<ElementT, SszElementT>,
        SszVectorT extends SszPrimitiveVector<ElementT, SszElementT>>
    extends SszPrimitiveCollectionSchema<ElementT, SszElementT, SszVectorT>,
        SszVectorSchema<SszElementT, SszVectorT> {

  static <ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
      SszPrimitiveVectorSchema<ElementT, SszElementT, ?> create(
          SszPrimitiveSchema<ElementT, SszElementT> elementSchema, int length) {
    return new SszPrimitiveVectorSchemaImpl<>(elementSchema, length);
  }
}
