package dev.nagarfix.api.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The status rules on their own: no database, so these run everywhere (also on your PC). */
class WorkflowTest {

    @Test
    void theNormalPathEndsClosed() {
        assertThat(Workflow.next(IssueAction.ASSIGN, "SUBMITTED")).contains("ASSIGNED");
        assertThat(Workflow.next(IssueAction.START, "ASSIGNED")).contains("IN_PROGRESS");
        assertThat(Workflow.next(IssueAction.RESOLVE, "IN_PROGRESS")).contains("RESOLVED");
        assertThat(Workflow.next(IssueAction.CONFIRM, "RESOLVED")).contains("CLOSED");
    }

    @Test
    void theCitizenCanReopenAndWorkStartsAgain() {
        assertThat(Workflow.next(IssueAction.REOPEN, "RESOLVED")).contains("REOPENED");
        assertThat(Workflow.next(IssueAction.START, "REOPENED")).contains("IN_PROGRESS");
    }

    @Test
    void openReportsCanBeRejectedOrMarkedDuplicate() {
        for (String status : Workflow.OPEN) {
            assertThat(Workflow.next(IssueAction.REJECT, status)).contains("REJECTED");
            assertThat(Workflow.next(IssueAction.DUPLICATE, status)).contains("DUPLICATE");
        }
    }

    @Test
    void finishedReportsCannotChange() {
        for (String status : List.of("CLOSED", "REJECTED", "DUPLICATE")) {
            for (IssueAction action : IssueAction.values()) {
                assertThat(Workflow.next(action, status)).as("%s from %s", action, status).isEmpty();
            }
        }
    }

    @Test
    void stepsCannotBeSkipped() {
        assertThat(Workflow.next(IssueAction.START, "SUBMITTED")).isEmpty();
        assertThat(Workflow.next(IssueAction.RESOLVE, "SUBMITTED")).isEmpty();
        assertThat(Workflow.next(IssueAction.RESOLVE, "ASSIGNED")).isEmpty();
        assertThat(Workflow.next(IssueAction.CONFIRM, "IN_PROGRESS")).isEmpty();
        assertThat(Workflow.next(IssueAction.REOPEN, "CLOSED")).isEmpty();
    }

    @Test
    void onlyTheReporterConfirmsOrReopens() {
        assertThat(IssueAction.CONFIRM.byReporter()).isTrue();
        assertThat(IssueAction.REOPEN.byReporter()).isTrue();
        assertThat(IssueAction.ASSIGN.byReporter()).isFalse();
        assertThat(IssueAction.RESOLVE.byReporter()).isFalse();
    }

    @Test
    void statusWordsForMessages() {
        assertThat(Workflow.label("IN_PROGRESS")).isEqualTo("in progress");
        assertThat(Workflow.label("DUPLICATE")).isEqualTo("a duplicate");
        assertThat(Workflow.label("CLOSED")).isEqualTo("closed");
    }
}
