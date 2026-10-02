package dev.nagarfix.api.issue;

/** Things a person can do to a report. Officers (or admins) do the first five; the reporter does the last two. */
public enum IssueAction {
    ASSIGN("take", false),
    START("start work on", false),
    RESOLVE("resolve", false),
    REJECT("reject", false),
    DUPLICATE("mark as a duplicate", false),
    CONFIRM("confirm", true),
    REOPEN("reopen", true);

    private final String verb;
    private final boolean byReporter;

    IssueAction(String verb, boolean byReporter) {
        this.verb = verb;
        this.byReporter = byReporter;
    }

    /** For messages: "You can't take a report that is closed." */
    public String verb() {
        return verb;
    }

    /** True for the citizen's own actions (confirm the fix, or reopen). */
    public boolean byReporter() {
        return byReporter;
    }
}
