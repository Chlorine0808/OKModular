package ruiseki.okmodular.selftest.report;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ruiseki.okmodular.selftest.judge.CheckResult;
import ruiseki.okmodular.selftest.judge.ScenarioResult;
import ruiseki.okmodular.selftest.judge.Verdict;

/** Renders the selftest results as one self-contained HTML page (inline CSS, no external loads). */
public final class ReportHtml {

    private static final String CSS = "body{font-family:sans-serif;margin:1.5em auto;max-width:70em;padding:0 1em;color:#222}"
        + "table{border-collapse:collapse;margin:.5em 0}th,td{border:1px solid #ccc;padding:.2em .6em;text-align:left}"
        + ".banner{background:#fdd;border:2px solid #c00;padding:.5em 1em;font-weight:bold}"
        + ".scenario{border-left:6px solid #ccc;margin:1em 0;padding:.2em 1em}"
        + ".scenario.fail{border-color:#c00}.scenario.visual{border-color:#d90}.scenario.pass{border-color:#2a2}"
        + ".source,.cause,.note,.error{margin:.2em 0}.cause{color:#a00;font-weight:bold}"
        + ".bad td{background:#fee}.grid-figure{margin:.5em 0}.layer{margin:.3em 0 0}svg{display:block}";

    private ReportHtml() {}

    public static String render(List<ScenarioResult> results, Annotations notes, RunInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n")
            .append("<title>OKModular 実機テスト報告</title>\n<style>")
            .append(CSS)
            .append("</style>\n</head>\n<body>\n<h1>OKModular 実機テスト報告</h1>\n");
        runInfo(sb, info);
        sectionTable(sb, results);
        fixes(sb, notes);
        scenarios(sb, "失敗 (fail)", results, Verdict.FAIL, notes);
        scenarios(sb, "要目視", results, Verdict.VISUAL, notes);
        scenarios(sb, "成功 (pass)", results, Verdict.PASS, notes);
        return sb.append("</body>\n</html>\n")
            .toString();
    }

    private static void runInfo(StringBuilder sb, RunInfo info) {
        if (info.deadlineHit) {
            sb.append("<p class=\"banner\">締め切りで打ち切り: 未実行のシナリオは失敗として数えている</p>\n");
        }
        sb.append("<table class=\"run-info\">\n");
        row(sb, "開始", info.startedAt);
        row(sb, "ブランチ", info.branch);
        row(sb, "コミット", info.commit);
        row(sb, "tick 数", String.valueOf(info.ticks));
        sb.append("</table>\n");
        if (!info.notes.isEmpty()) {
            sb.append("<ul class=\"run-notes\">\n");
            for (String n : info.notes) sb.append("<li>")
                .append(esc(n))
                .append("</li>\n");
            sb.append("</ul>\n");
        }
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("<tr><th>")
            .append(esc(k))
            .append("</th><td>")
            .append(esc(v))
            .append("</td></tr>\n");
    }

    private static void sectionTable(StringBuilder sb, List<ScenarioResult> results) {
        Map<String, int[]> counts = new TreeMap<>();
        for (ScenarioResult r : results) {
            int[] c = counts.get(r.section);
            if (c == null) counts.put(r.section, c = new int[3]);
            c[r.verdict == Verdict.PASS ? 0 : r.verdict == Verdict.FAIL ? 1 : 2]++;
        }
        sb.append("<h2>節ごとの結果</h2>\n<table class=\"sections\">\n<tr><th>節</th><th>")
            .append(Verdict.PASS.label)
            .append("</th><th>")
            .append(Verdict.FAIL.label)
            .append("</th><th>")
            .append(Verdict.VISUAL.label)
            .append("</th></tr>\n");
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int[] c = e.getValue();
            sb.append("<tr><th>")
                .append(esc(e.getKey()))
                .append("</th><td>")
                .append(c[0])
                .append("</td><td>")
                .append(c[1])
                .append("</td><td>")
                .append(c[2])
                .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private static void fixes(StringBuilder sb, Annotations notes) {
        if (notes.fixes()
            .isEmpty()) return;
        sb.append("<h2>本体の修正</h2>\n<table class=\"fixes\">\n<tr><th>コミット</th><th>シナリオ</th><th>内容</th></tr>\n");
        for (Annotations.Fix f : notes.fixes()) {
            sb.append("<tr><td>")
                .append(esc(f.commit))
                .append("</td><td>")
                .append(esc(f.scenario))
                .append("</td><td>")
                .append(esc(f.summary))
                .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private static void scenarios(StringBuilder sb, String heading, List<ScenarioResult> results, Verdict v,
        Annotations notes) {
        StringBuilder body = new StringBuilder();
        int n = 0;
        for (ScenarioResult r : results) {
            if (r.verdict != v) continue;
            n++;
            scenario(body, r, notes);
        }
        sb.append("<h2>")
            .append(heading)
            .append(" (")
            .append(n)
            .append(")</h2>\n")
            .append(body);
    }

    private static void scenario(StringBuilder sb, ScenarioResult r, Annotations notes) {
        boolean fold = r.verdict == Verdict.PASS;
        String cls = r.verdict == Verdict.PASS ? "pass" : r.verdict == Verdict.FAIL ? "fail" : "visual";
        if (fold) {
            sb.append("<details class=\"scenario pass\" id=\"")
                .append(esc(r.id))
                .append("\">\n<summary>")
                .append(esc(r.id))
                .append(" ")
                .append(esc(r.title))
                .append("</summary>\n");
        } else {
            sb.append("<div class=\"scenario ")
                .append(cls)
                .append("\" id=\"")
                .append(esc(r.id))
                .append("\">\n<h3>")
                .append(esc(r.id))
                .append(" ")
                .append(esc(r.title))
                .append("</h3>\n");
        }
        sb.append("<p class=\"verdict\">")
            .append(esc(r.verdict.label))
            .append("</p>\n<p class=\"source\">出典: ")
            .append(esc(r.source))
            .append("</p>\n");
        String cause = notes.causeOf(r.id);
        if (cause != null) {
            sb.append("<p class=\"cause\">原因の見立て: ")
                .append(esc(cause))
                .append("</p>\n");
        }
        String note = notes.noteOf(r.id);
        if (note != null) {
            sb.append("<p class=\"note\">")
                .append(esc(note))
                .append("</p>\n");
        }
        if (r.error != null) {
            sb.append("<p class=\"error\">エラー: ")
                .append(esc(r.error))
                .append("</p>\n");
        }
        if (!r.checks.isEmpty()) {
            sb.append("<table class=\"checks\">\n<tr><th>種類</th><th>期待</th><th>実際</th><th>結果</th></tr>\n");
            for (CheckResult c : r.checks) {
                sb.append(c.passed ? "<tr>" : "<tr class=\"bad\">")
                    .append("<td>")
                    .append(esc(c.kind))
                    .append("</td><td>")
                    .append(esc(c.expected))
                    .append("</td><td>")
                    .append(esc(c.actual))
                    .append("</td><td>")
                    .append(c.passed ? "ok" : "NG")
                    .append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }
        for (Map.Entry<String, ruiseki.okmodular.selftest.grid.BlockGrid> e : r.figures.entrySet()) {
            sb.append(GridFigure.render(e.getKey(), e.getValue()));
        }
        sb.append(fold ? "</details>\n" : "</div>\n");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
