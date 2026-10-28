package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.selftest.scenario.Scenario;

// The §2 scenarios only reload chunks inside one server run; a save that crosses a server restart is
// not exercised. The report has to say so whenever §2 ran, and only then.
@DisplayName("実機テストの注記")
class SelfTestNotesTest {

    private static Scenario in(String section) {
        return Scenario.invalid("x-" + section, section, "src", "unused");
    }

    @Test
    @DisplayName("§2 を回したらサーバー再起動を跨ぐ確認が未実施だと書く")
    void notesTheMissingRestartCheckWhenSection2Ran() {
        List<String> notes = new ArrayList<>();
        SelfTest.addSectionNotes(Arrays.asList(in("1"), in("2")), notes);
        assertEquals(Arrays.asList(SelfTest.NO_RESTART_NOTE), notes);
        assertTrue(SelfTest.NO_RESTART_NOTE.contains("サーバー再起動を跨ぐ確認は未実施"));
    }

    @Test
    @DisplayName("§2 が無ければ何も書かない")
    void addsNothingWithoutSection2() {
        List<String> notes = new ArrayList<>();
        SelfTest.addSectionNotes(Arrays.asList(in("1"), in("20"), in(null)), notes);
        assertTrue(notes.isEmpty());
    }
}
