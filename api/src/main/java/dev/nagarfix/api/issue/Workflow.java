package dev.nagarfix.api.issue;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * All status rules in one place.
 *
 * <pre>
 * SUBMITTED -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED   (citizen confirms, or automatically after N days)
 *                               ^             |
 *                               +- REOPENED <-+               (citizen says it is not fixed)
 * any open status -> REJECTED (with a reason) or DUPLICATE (linked to the original)
 * </pre>
 */
public final class Workflow {

    /** Reports that still need work. */
    public static final List<String> OPEN = List.of("SUBMITTED", "ASSIGNED", "IN_PROGRESS", "REOPENED");

    private record Move(Set<String> from, String to) {
    }

    private static final Map<IssueAction, Move> MOVES = new EnumMap<>(IssueAction.class);

    static {
        MOVES.put(IssueAction.ASSIGN, new Move(Set.of("SUBMITTED"), "ASSIGNED"));
        MOVES.put(IssueAction.START, new Move(Set.of("ASSIGNED", "REOPENED"), "IN_PROGRESS"));
        MOVES.put(IssueAction.RESOLVE, new Move(Set.of("IN_PROGRESS"), "RESOLVED"));
        MOVES.put(IssueAction.REJECT, new Move(Set.copyOf(OPEN), "REJECTED"));
        MOVES.put(IssueAction.DUPLICATE, new Move(Set.copyOf(OPEN), "DUPLICATE"));
        MOVES.put(IssueAction.CONFIRM, new Move(Set.of("RESOLVED"), "CLOSED"));
        MOVES.put(IssueAction.REOPEN, new Move(Set.of("RESOLVED"), "REOPENED"));
    }

    private Workflow() {
    }

    /** The status this action leads to, or empty if the action is not allowed from this status. */
    public static Optional<String> next(IssueAction action, String from) {
        Move move = MOVES.get(action);
        return move != null && move.from().contains(from) ? Optional.of(move.to()) : Optional.empty();
    }

    public static boolean isOpen(String status) {
        return OPEN.contains(status);
    }

    /** Status in words, for messages: "in progress", "resolved", "a duplicate". */
    public static String label(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "in progress";
            case "DUPLICATE" -> "a duplicate";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }
}
