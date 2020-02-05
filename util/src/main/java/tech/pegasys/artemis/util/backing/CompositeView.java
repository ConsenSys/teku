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

package tech.pegasys.artemis.util.backing;

import java.util.function.Function;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;

public interface CompositeView<C extends View> extends View {

  default int size() {
    return getType().getMaxLength();
  }

  C get(int index);

  void set(int index, C value);

  @Override
  CompositeViewType getType();

  default void update(int index, Function<C, C> mutator) {
    set(index, mutator.apply(get(index)));
  }
}
