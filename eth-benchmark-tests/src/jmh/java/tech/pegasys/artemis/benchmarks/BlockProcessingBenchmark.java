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

package tech.pegasys.artemis.benchmarks;

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.io.Resources;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.ethtests.MapObjectUtil;
import tech.pegasys.artemis.ethtests.TestObject;
import tech.pegasys.artemis.ethtests.TestSet;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;
import tech.pegasys.artemis.util.config.Constants;

public class BlockProcessingBenchmark {
  protected static Path configPath = null;
  private static final Path pathToTests =
      Paths.get(
          System.getProperty("user.dir").toString(),
          "src",
          "test-support",
          "resources",
          "eth2.0-spec-tests",
          "tests");
  private static final String FILE = "file://";
  private static final String resourceGlobBase = "**/tests/";

  @State(Scope.Benchmark)
  public static class ProcessBlockHeaderJMHState {
    public BeaconBlock block;
    public BeaconState state;

    @Setup
    public void setup() throws Exception {
      List<Object> list = minimalBeaconBlockHeaderSuccessSetup();
      block = (BeaconBlock) list.get(0);
      state = (BeaconState) list.get(1);
      // block = DataStructureUtil.randomBeaconBlock(0, 0);
      // state = DataStructureUtil.randomBeaconState(0);
    }
  }

  @Benchmark
  @Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void mainnetProcessBeaconBlockHeaderSuccess(
      ProcessBlockHeaderJMHState processBlockHeaderJMHState) throws BlockProcessingException {
    // Throwing BlockProcessingException is intentional in this method, as the input parameters
    // are designed to be processed without error. As we're not benchmarking error handling in this
    // case, it doesn't make sense to catch and blackhole the error. We want the benchmark to fail.

    BlockProcessorUtil.process_block_header(
        processBlockHeaderJMHState.state, processBlockHeaderJMHState.block, true);
  }

  @MustBeClosed
  static Stream<Object> blockSuccessSetup(String config) throws Exception {
    String resourcePath =
        buildResourcePath(config, "phase0", "operations", "block_header", "pyspec_tests");
    return operationSuccessSetup(
        resourcePath, buildResourcePath(config), "block.ssz", BeaconBlock.class);
  }

  static List<Object> minimalBeaconBlockHeaderSuccessSetup() throws Exception {
    try (Stream<Object> blockSuccessStream = blockSuccessSetup("minimal")) {
      if (blockSuccessStream != null) {
        return blockSuccessStream.collect(Collectors.toList());
      }
    }
    throw new Exception(
        "Unable to setup minimal beacon block header tests. Test object stream was null.");
  }

  @MustBeClosed
  @SuppressWarnings("rawtypes")
  public static Stream<Object> operationSuccessSetup(
      String resourcePath, String configPath, String operationName, Class operationClass)
      throws Exception {
    loadConfigFromGlob(configPath);

    TestSet testSet = new TestSet(Path.of(resourcePath));
    testSet.add(new TestObject(operationName, operationClass, null));
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    testSet.add(new TestObject("post.ssz", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @SuppressWarnings({"rawtypes"})
  public static void loadConfigFromGlob(String configGlob) throws Exception {
    String resourceGlob = buildResourcePath(resourceGlobBase, configGlob, "config.yaml");

    try (Stream<URL> urls = Resources.find(resourceGlob)) {
      List<String> configTypes = urls.map(url -> url.toString()).collect(Collectors.toList());
      String joinedConfigs = configTypes.stream().collect(Collectors.joining());
      if (configTypes.size() != 0 && joinedConfigs.contains(configGlob)) {
        String constants = joinedConfigs.contains("mainnet") ? "mainnet" : "minimal";
        Constants.setConstants(constants);
      } else {
        STDOUT.log(
            Level.FATAL,
            "Unable to load 'config.yaml' configuration file for the '"
                + configGlob
                + "' config type.'");
        throw new Exception(
            "TestSuite.loadConfigFromPath(): Configuration files was not found in the hierarchy of the provided path");
      }
    }

    // TODO fix this massacre of a technical debt
    // Checks if constants were changed from minimal to mainnet or vice-versa, and updates
    // reflection information
    if (Constants.SLOTS_PER_HISTORICAL_ROOT
        != SimpleOffsetSerializer.classReflectionInfo
            .get(BeaconState.class)
            .getVectorLengths()
            .get(0)) {
      SimpleOffsetSerializer.setConstants();
    }
  }

  @SuppressWarnings("unchecked")
  public static Stream<Object> findTestsByPath(TestSet testSet) {
    Path path = Path.of(pathToTests.toString(), testSet.getPath().toString());
    try (Stream<Path> walk = Files.walk(path)) {
      List<String> result = walk.map(x -> x.toString()).collect(Collectors.toList());
      result =
          result.stream()
              .filter(walkPath -> isFilePathConfiguredForTest(testSet, walkPath))
              .collect(Collectors.toList());

      return result.stream()
          .map(
              walkPath -> {
                return testSet.getFileNames().stream()
                    .flatMap(
                        fileName -> {
                          Object object =
                              pathToObject(
                                  Path.of(walkPath, fileName),
                                  testSet.getTestObjectByFileName(fileName));
                          return testSet.getTestObjectByFileName(fileName).stream()
                              .map(
                                  testObject -> {
                                    if (fileName.contains(".ssz")) {
                                      Bytes objectBytes =
                                          getSSZBytesFromPath(Path.of(walkPath, fileName));
                                      return SimpleOffsetSerializer.deserialize(
                                          objectBytes, testObject.getClassName());
                                    } else {
                                      return parseObjectFromFile(
                                          testObject.getClassName(), testObject.getPath(), object);
                                    }
                                  });
                        })
                    .collect(Collectors.toList());
              });
    } catch (IOException e) {
      STDOUT.log(Level.WARN, e.toString());
    }
    return null;
  }

  public static Object pathToObject(Path path, List<TestObject> testObjects) {
    return getObjectFromYAMLInputStream(getInputStreamFromPath(path), testObjects);
  }

  @SuppressWarnings({"rawtypes"})
  public static Object getObjectFromYAMLInputStream(InputStream in, List<TestObject> testObjects) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Object object = null;
    try {
      if (testObjects != null
          && (testObjects.get(0).getClassName().equals(UnsignedLong.class)
              || testObjects.get(0).getClassName().equals(Boolean.class)
              || testObjects.get(0).getClassName().equals(String.class))) {
        object = ((String) mapper.readerFor(String.class).readValue(in));
      } else {
        object = ((Map) mapper.readerFor(Map.class).readValue(in));
      }

    } catch (IOException e) {
      STDOUT.log(Level.WARN, e.toString());
    }
    return object;
  }

  @SuppressWarnings({"rawtypes"})
  private static Object parseObjectFromFile(Class className, Path path, Object object) {
    if (path != null) {
      Iterator<Path> itr = path.iterator();
      while (itr.hasNext()) {
        object = ((Map) object).get(itr.next().toString());
      }
    }
    return MapObjectUtil.convertMapToTypedObject(className, object);
  }

  @SuppressWarnings({"rawtypes"})
  public static Bytes getSSZBytesFromPath(Path path) {
    InputStream in = getInputStreamFromPath(path);
    try {
      byte[] targetArray = new byte[in.available()];
      in.read(targetArray);
      return Bytes.wrap(targetArray);
    } catch (IOException e) {
      STDOUT.log(Level.WARN, e.toString());
    }
    return null;
  }

  public static InputStream getInputStreamFromPath(Path path) {
    URL url = null;
    InputStream in = null;
    try {
      url = new URL(FILE + path);
      in = url.openConnection().getInputStream();
    } catch (MalformedURLException e) {
      STDOUT.log(Level.WARN, e.toString());
    } catch (IOException e) {
      STDOUT.log(Level.WARN, e.toString());
    }
    return in;
  }

  public static boolean isFilePathConfiguredForTest(TestSet testSet, String walkPath) {
    boolean isIncludedPath =
        testSet.getFileNames().stream()
            .allMatch(fileName -> Files.exists(Path.of(walkPath, fileName)));
    boolean isSuccessTest =
        testSet.getFileNames().stream()
                .filter(
                    fileName ->
                        fileName.contains("pre.yaml")
                            || fileName.contains("pre.ssz")
                            || fileName.contains("post.yaml")
                            || fileName.contains("post.ssz"))
                .collect(Collectors.toList())
                .size()
            > 1;
    if (isSuccessTest) return isIncludedPath;
    boolean isNotExcludedPath =
        !(Files.exists(Path.of(walkPath, "post.ssz"))
            || Files.exists(Path.of(walkPath, "post.yaml")));
    boolean isMetaPath =
        testSet.getFileNames().size() == 1 && testSet.getFileNames().get(0).equals("meta.yaml");
    return isIncludedPath && (isNotExcludedPath || isMetaPath);
  }

  private static String buildResourcePath(String first, String... more) {
    if (FileSystems.getDefault().getSeparator().equals("/")) {
      return Path.of(first, more).toString();
    } else {
      // TODO - Determine whether to implement custom handling for this case.
      // For now, if the default file system name-seperator is not "/",
      // (as is expected for Java resources), throw UnsupportedOperationException.
      throw new UnsupportedOperationException(
          "The JMH resource loader expects the default name-seperator to be '/'.");
    }
  }
}
