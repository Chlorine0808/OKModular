package ruiseki.okmodular.selftest.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

class AnnotationsTest {

    private static final String JSON = "{\"scenarios\":{" + "\"a\":{\"cause\":\"mod-bug\",\"note\":\"n1\"},"
        + "\"b\":{\"note\":\"only note\"}},"
        + "\"fixes\":[{\"commit\":\"abc1234\",\"scenario\":\"a\",\"summary\":\"fixed it\"}]}";

    @Test
    void parsesCausesNotesAndFixes() {
        Annotations a = Annotations.parse(new StringReader(JSON));
        assertEquals("mod-bug", a.causeOf("a"));
        assertEquals("n1", a.noteOf("a"));
        assertNull(a.causeOf("b"));
        assertEquals("only note", a.noteOf("b"));
        assertNull(a.causeOf("zzz"));
        assertNull(a.noteOf("zzz"));
        assertEquals(
            1,
            a.fixes()
                .size());
        assertEquals(
            "abc1234",
            a.fixes()
                .get(0).commit);
        assertEquals(
            "a",
            a.fixes()
                .get(0).scenario);
        assertEquals(
            "fixed it",
            a.fixes()
                .get(0).summary);
    }

    @Test
    void rejectsUnknownCause() {
        String bad = "{\"scenarios\":{\"a\":{\"cause\":\"cosmic-ray\"}}}";
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> Annotations.parse(new StringReader(bad)));
        assertTrue(
            e.getMessage()
                .contains("cosmic-ray"));
    }

    @Test
    void emptyHasNothing() {
        Annotations a = Annotations.empty();
        assertNull(a.causeOf("a"));
        assertTrue(
            a.fixes()
                .isEmpty());
    }
}
