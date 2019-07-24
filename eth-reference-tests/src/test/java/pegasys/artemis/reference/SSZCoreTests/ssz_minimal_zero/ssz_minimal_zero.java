package pegasys.artemis.reference.SSZCoreTests.ssz_minimal_zero;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.alogger.ALogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;


class ssz_minimal_zero {

  private static final ALogger STDOUT = new ALogger("stdout");
  private static String testFile = "./src/test/resources/eth2.0-spec-tests/tests/ssz_static/core/ssz_minimal_zero.yaml";

  @SuppressWarnings({"unchecked", "rawtypes"})
  String convertYamlToJson(String yaml) throws IOException {
      ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      Object obj = yamlReader.readValue(yaml, Object.class);

      ObjectMapper jsonWriter = new ObjectMapper();
      return jsonWriter.writeValueAsString(obj);
  }

  private static String readYamlFile(String pathname) throws IOException {
    String lineSeparator = System.getProperty("line.separator");
    List<String> lines = Files.readAllLines(Paths.get(pathname));

    return lines.stream().collect(Collectors.joining(lineSeparator));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void main() throws IOException, ParseException {
    String jsonString = convertYamlToJson(readYamlFile(testFile));
    JSONParser parser = new JSONParser();
    JSONObject json = (JSONObject) parser.parse(jsonString);
    JSONArray test_cases = (JSONArray) json.get("test_cases");
    HashMap eth2Object = (HashMap) test_cases.get(8);
    String key = eth2Object.keySet().toArray()[0].toString();
    HashMap entry = (HashMap) eth2Object.get(key);
    String entryJSONstring = entry.get("value").toString();
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.findAndRegisterModules();
    Checkpoint checkpoint = mapper.readValue(entryJSONstring, Checkpoint.class);
    // Gson gson = new GsonBuilder().registerTypeAdapter(UnsignedLong.class, new InterfaceAdapter<UnsignedLong>()).registerTypeAdapter(Bytes32.class, new InterfaceAdapter<Bytes32>()).create();
    // Checkpoint attestation = gson.fromJson(entryJSONstring, Checkpoint.class);
    Checkpoint point = new Checkpoint();
    //String st = gson.toJson(point);
    assertEquals(key , "Checkpoint");
    System.out.println(test_cases.get(0));
  }
}
