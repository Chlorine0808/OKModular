package ruiseki.okmodular.selftest.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.selftest.grid.BlockGrid;
import ruiseki.okmodular.selftest.judge.CheckResult;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.scenario.Expectation;
import ruiseki.okmodular.selftest.scenario.InputSpec;
import ruiseki.okmodular.selftest.scenario.Region;
import ruiseki.okmodular.selftest.scenario.Scenario;
import ruiseki.okmodular.selftest.scenario.Step;

class ReportHtmlTest {

    private static Scenario sc(String id, String section, String title, String source, boolean visual) {
        return new Scenario(
            id,
            section,
            title,
            source,
            0,
            64,
            visual,
            false,
            null,
            Collections.<InputSpec>emptyList(),
            Collections.<Step>emptyList(),
            10,
            Collections.<String, Region>emptyMap(),
            Collections.<String, Integer>emptyMap(),
            Collections.<Expectation>emptyList(),
            null);
    }

    private static ScenarioResult result(String id, String section, boolean visual, CheckResult... checks) {
        return ScenarioResult.of(
            sc(id, section, "title-" + id, "docs/" + id + ".md#s", visual),
            Arrays.asList(checks),
            Collections.<String, BlockGrid>emptyMap());
    }

    private static final CheckResult OK = new CheckResult("formed", true, "exp-ok", "act-ok");
    private static final CheckResult NG = new CheckResult("formed", false, "exp-ng", "act-ng");
    private static final RunInfo INFO = new RunInfo(
        "2026-10-28T10:00:00",
        "feature/x",
        "abc1234",
        100L,
        false,
        Collections.<String>emptyList());

    private static String render(List<ScenarioResult> rs, Annotations a, RunInfo info) {
        return ReportHtml.render(rs, a, info);
    }

    private static List<ScenarioResult> three() {
        return Arrays.asList(
            result("pass-id", "3.5", false, OK),
            result("visual-id", "3.6", true, OK),
            result("fail-id", "3.5", false, NG));
    }

    @Test
    void failedScenariosComeFirst() {
        String h = render(three(), Annotations.empty(), INFO);
        int f = h.indexOf("id=\"fail-id\"");
        int v = h.indexOf("id=\"visual-id\"");
        int p = h.indexOf("id=\"pass-id\"");
        assertTrue(f > 0 && f < v && v < p, f + " " + v + " " + p);
    }

    @Test
    void sectionTableCountsEachVerdict() {
        List<ScenarioResult> rs = new ArrayList<>();
        for (int i = 0; i < 13; i++) rs.add(result("p" + i, "3.5", false, OK));
        rs.add(result("f", "3.5", false, NG));
        String h = render(rs, Annotations.empty(), INFO);
        Matcher m = Pattern.compile("<tr><th>3\\.5</th><td>(\\d+)</td><td>(\\d+)</td><td>(\\d+)</td></tr>")
            .matcher(h);
        assertTrue(m.find(), h);
        assertEquals("13", m.group(1));
        assertEquals("1", m.group(2));
        assertEquals("0", m.group(3));
    }

    @Test
    void everyScenarioShowsItsSource() {
        String h = render(three(), Annotations.empty(), INFO);
        for (String id : new String[] { "pass-id", "visual-id", "fail-id" }) {
            assertTrue(h.contains("docs/" + id + ".md#s"), id);
        }
    }

    @Test
    void expectedAndActualSitSideBySide() {
        String h = render(three(), Annotations.empty(), INFO);
        assertTrue(
            Pattern.compile("<tr[^>]*>[^\\n]*exp-ng[^\\n]*act-ng[^\\n]*</tr>")
                .matcher(h)
                .find(),
            h);
    }

    @Test
    void escapesMarkupInMessages() {
        ScenarioResult r = result("x<i>", "3.5", false, new CheckResult("k<k>", false, "e&", "<b>&"));
        String h = render(Collections.singletonList(r), Annotations.empty(), INFO);
        assertTrue(h.contains("&lt;b&gt;&amp;"));
        assertFalse(h.contains("<b>"));
        assertFalse(h.contains("<i>"));
        assertFalse(h.contains("<k>"));
    }

    @Test
    void causeAndFixesAreShownWhenAnnotated() {
        Annotations a = Annotations.parse(
            new StringReader(
                "{\"scenarios\":{\"fail-id\":{\"cause\":\"mod-bug\",\"note\":\"the-note\"}},"
                    + "\"fixes\":[{\"commit\":\"deadbee\",\"scenario\":\"fail-id\",\"summary\":\"the-fix\"}]}"));
        String h = render(three(), a, INFO);
        assertTrue(h.contains("mod-bug"));
        assertTrue(h.contains("the-note"));
        assertTrue(h.contains("deadbee"));
        assertTrue(h.contains("the-fix"));
    }

    @Test
    void deadlineIsAnnounced() {
        RunInfo hit = new RunInfo("t", "b", "c", 5L, true, Collections.singletonList("note-x"));
        String h = render(three(), Annotations.empty(), hit);
        assertTrue(h.contains("締め切りで打ち切り"));
        assertTrue(h.contains("note-x"));
        assertFalse(render(three(), Annotations.empty(), INFO).contains("締め切りで打ち切り"));
    }

    @Test
    void passedScenariosAreFolded() {
        String h = render(three(), Annotations.empty(), INFO);
        int p = h.indexOf("id=\"pass-id\"");
        int f = h.indexOf("id=\"fail-id\"");
        assertTrue(h.lastIndexOf("<details", p) > h.lastIndexOf("</details>", p));
        assertTrue(h.lastIndexOf("<details", f) <= h.lastIndexOf("</details>", f));
    }

    @Test
    void figuresAreEmbeddedAndPageIsSelfContained() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 1, 0, 1 });
        g.set(0, 0, 0, "minecraft:stone");
        Map<String, BlockGrid> figs = Collections.singletonMap("fig-name", g);
        ScenarioResult r = ScenarioResult.failed(sc("e", "3.5", "t", "s", false), "boom<", figs);
        String h = render(Collections.singletonList(r), Annotations.empty(), INFO);
        assertTrue(h.contains("fig-name"));
        assertTrue(h.contains("<svg"));
        assertTrue(h.contains("boom&lt;"));
        assertTrue(h.contains("lang=\"ja\""));
        assertFalse(h.contains("http://cdn"));
        assertFalse(h.contains("<script"));
    }

    @Test
    void figuresOfOneScenarioSitSideBySide() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 1, 0, 1 });
        g.set(0, 0, 0, "minecraft:stone");
        Map<String, BlockGrid> figs = new java.util.LinkedHashMap<>();
        figs.put("before", g);
        figs.put("after", g);
        ScenarioResult r = ScenarioResult.failed(sc("e", "3.5", "t", "s", false), "x", figs);
        String h = render(Collections.singletonList(r), Annotations.empty(), INFO);
        int row = h.indexOf("<div class=\"figures\">");
        assertTrue(row >= 0);
        assertEquals(row, h.lastIndexOf("<div class=\"figures\">"));
        assertTrue(h.indexOf("<figcaption>before", row) > row);
        assertTrue(h.indexOf("<figcaption>after", row) > row);
        assertTrue(h.contains(".figures{display:flex"));
    }
}
