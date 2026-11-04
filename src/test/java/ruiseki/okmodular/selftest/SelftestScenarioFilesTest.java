package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.selftest.judge.ExpectationJudge;
import ruiseki.okmodular.selftest.scenario.Scenario;
import ruiseki.okmodular.selftest.scenario.ScenarioParser;

// The in-game run takes minutes and a typo in a scenario file only shows up there, as one failed
// scenario among many. These checks read the real files under selftest/ so an unknown kind or
// action, a duplicate id or a misspelt structure name is caught by ./gradlew test instead.
class SelftestScenarioFilesTest {

    private static final File SCENARIOS = new File("selftest/scenarios");
    private static final File[] STRUCTURE_DIRS = { new File("selftest/config/okmodular/structures"),
        new File("src/main/resources/assets/okmodular/defaults/structures") };

    private static List<Scenario> all() throws IOException {
        File[] files = SCENARIOS.listFiles((d, n) -> n.endsWith(".json"));
        assertNotNull(files, "no folder " + SCENARIOS.getAbsolutePath());
        Arrays.sort(files);
        ScenarioParser parser = new ScenarioParser(ExpectationJudge.KINDS);
        List<Scenario> out = new ArrayList<>();
        for (File f : files) {
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                out.addAll(parser.parseFile(f.getName(), r));
            }
        }
        return out;
    }

    @Test
    @DisplayName("どのシナリオも読み取りに成功する")
    void everyScenarioParses() throws IOException {
        List<Scenario> all = all();
        assertFalse(all.isEmpty());
        for (Scenario s : all) assertNull(s.parseError, s.id + ": " + s.parseError);
    }

    @Test
    @DisplayName("id はファイルをまたいでも重複しない")
    void idsAreUniqueAcrossFiles() throws IOException {
        Map<String, Integer> seen = new HashMap<>();
        for (Scenario s : all()) seen.merge(s.id, 1, Integer::sum);
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            assertEquals(1, (int) e.getValue(), "duplicate id " + e.getKey());
        }
    }

    @Test
    @DisplayName("組む構造は fixture か同梱の既定にある")
    void machineStructuresExist() throws IOException {
        Set<String> names = structureNames();
        for (Scenario s : all()) {
            if (s.machine == null) continue;
            assertTrue(names.contains(s.machine.structure), s.id + ": no structure \"" + s.machine.structure + "\"");
        }
    }

    /** Every "name" in the structure files, which hold one object or an array of them. */
    private static Set<String> structureNames() throws IOException {
        Set<String> names = new HashSet<>();
        for (File dir : STRUCTURE_DIRS) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            assertNotNull(files, "no folder " + dir.getAbsolutePath());
            for (File f : files) {
                try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                    JsonElement root = new JsonParser().parse(r);
                    Iterable<JsonElement> items = root.isJsonArray() ? root.getAsJsonArray() : Arrays.asList(root);
                    for (JsonElement e : items) {
                        JsonObject o = e.getAsJsonObject();
                        if (o.has("name")) names.add(
                            o.get("name")
                                .getAsString());
                    }
                }
            }
        }
        return names;
    }
}
