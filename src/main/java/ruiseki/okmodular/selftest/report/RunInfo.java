package ruiseki.okmodular.selftest.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Facts about one selftest run, shown at the top of the report. */
public final class RunInfo {

    public final String startedAt, branch, commit;
    public final long ticks;
    public final boolean deadlineHit;
    public final List<String> notes;

    public RunInfo(String startedAt, String branch, String commit, long ticks, boolean deadlineHit,
        List<String> notes) {
        this.startedAt = startedAt;
        this.branch = branch;
        this.commit = commit;
        this.ticks = ticks;
        this.deadlineHit = deadlineHit;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }
}
