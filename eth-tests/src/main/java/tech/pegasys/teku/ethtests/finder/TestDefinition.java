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

import java.nio.file.Path;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.TestSpecFactory;

public class TestDefinition {
  private final String phase;
  private final String specName;
  private final String testType;
  private final String testName;
  private final Path pathFromPhaseTestDir;
  private Spec spec;

  public TestDefinition(
      final String phase,
      final String specName,
      final String testType,
      final String testName,
      final Path pathFromPhaseTestDir) {
    this.phase = phase;
    this.specName = specName;
    this.testType = testType.replace("\\", "/");
    this.testName = testName.replace("\\", "/");
    this.pathFromPhaseTestDir = pathFromPhaseTestDir;
  }

  public String getPhase() {
    return phase;
  }

  public String getSpecName() {
    return specName;
  }

  public Spec getSpec() {
    if (spec == null) {
      spec = createSpec();
    }
    return spec;
  }

  private Spec createSpec() {
    switch (phase) {
      case "altair":
        return TestSpecFactory.createAltair(specName);
      case "phase0":
        return SpecFactory.create(specName);
      default:
        throw new UnsupportedOperationException("Unsupported phase: " + phase);
    }
  }

  public String getTestType() {
    return testType;
  }

  public String getTestName() {
    return testName;
  }

  @Override
  public String toString() {
    return phase + " - " + specName + " - " + testType + " - " + testName;
  }

  public String getDisplayName() {
    return toString();
  }

  public Path getPathFromPhaseTestDir() {
    return pathFromPhaseTestDir;
  }

  public Path getTestDirectory() {
    return ReferenceTestFinder.findReferenceTestRootDirectory()
        .resolve(Path.of(specName, phase))
        .resolve(pathFromPhaseTestDir);
  }
}
