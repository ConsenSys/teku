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

package tech.pegasys.teku.ethtests.finder;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface TestFinder {

  default Stream<TestDefinition> findTests(String spec, Path testRoot) throws IOException {
    return findTests(testRoot.toFile().getName(), spec, testRoot);
  }

  @MustBeClosed
  Stream<TestDefinition> findTests(String phase, String spec, Path testRoot) throws IOException;
}
