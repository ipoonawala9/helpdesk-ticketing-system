package com.ibrahim.helpdesk.ticket.priority;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keyword and category rules for ticket priority, applied in this order:
 *
 * <ol>
 *   <li>An incident signal in the title or description, such as "breach" or
 *       "outage", makes the ticket CRITICAL.</li>
 *   <li>Otherwise the category sets a baseline: SECURITY is HIGH, OTHER is LOW,
 *       every other category is MEDIUM.</li>
 *   <li>An urgency or blocking signal, such as "locked out" or "urgent", raises
 *       the ticket to at least HIGH.</li>
 *   <li>With no urgency signal, a low-urgency signal, such as "question" or
 *       "no rush", lowers the ticket to LOW, except for SECURITY tickets.</li>
 *   <li>Each reopen raises the result one level, but reopening alone never
 *       takes a ticket above HIGH. CRITICAL is reserved for incident
 *       signals.</li>
 * </ol>
 *
 * <p>Matching is case-insensitive on whole words and phrases, so "crash"
 * matches "App crash" but not "crashpad". Phrases that negate urgency, such as
 * "not urgent", are removed before urgency signals are looked for. The rules do
 * not otherwise understand negation: "not blocked" still counts as "blocked".
 */
@Component
public class RuleBasedTicketPriorityPolicy implements TicketPriorityPolicy {

    static final List<String> CRITICAL_SIGNALS = List.of(
            "breach", "breached", "data breach", "hacked", "ransomware", "compromised",
            "data loss", "lost all data", "outage",
            "production down", "system down", "server down", "site down", "service down");

    static final List<String> HIGH_SIGNALS = List.of(
            "urgent", "asap", "emergency", "blocked",
            "cannot log in", "can't log in", "cannot login", "can't login",
            "unable to log in", "unable to login", "locked out",
            "crash", "crashes", "crashed", "crashing",
            "malware", "virus", "phishing",
            "payment failed", "charged twice", "double charged", "overcharged",
            "all users", "everyone", "entire team", "whole team");

    /** Explicitly calm phrases. Removed before urgency matching, and count as low-urgency signals. */
    static final List<String> CALM_PHRASES = List.of(
            "not urgent", "non-urgent", "no rush", "not an emergency", "low priority");

    static final List<String> LOW_SIGNALS = List.of(
            "question", "how do i", "how to", "feature request", "suggestion",
            "whenever you can", "when you have time");

    /** Reopening can escalate a ticket up to this level, but not beyond. */
    private static final TicketPriority REOPEN_ESCALATION_CEILING = TicketPriority.HIGH;

    private static final Pattern CRITICAL = phrasePattern(CRITICAL_SIGNALS);
    private static final Pattern HIGH = phrasePattern(HIGH_SIGNALS);
    private static final Pattern CALM = phrasePattern(CALM_PHRASES);
    private static final Pattern LOW = phrasePattern(LOW_SIGNALS);

    @Override
    public TicketPriority determine(PriorityInput input) {
        String text = normalise(input.title()) + " \n " + normalise(input.description());
        boolean calm = CALM.matcher(text).find();
        String urgencyText = CALM.matcher(text).replaceAll(" ");

        if (CRITICAL.matcher(urgencyText).find()) {
            return TicketPriority.CRITICAL;
        }

        TicketPriority priority = baseline(input.category());

        if (HIGH.matcher(urgencyText).find()) {
            priority = max(priority, TicketPriority.HIGH);
        } else if ((calm || LOW.matcher(text).find()) && input.category() != TicketCategory.SECURITY) {
            priority = TicketPriority.LOW;
        }

        return escalateForReopens(priority, input.reopenCount());
    }

    private static TicketPriority baseline(TicketCategory category) {
        if (category == null) {
            return TicketPriority.MEDIUM;
        }
        return switch (category) {
            case SECURITY -> TicketPriority.HIGH;
            case OTHER -> TicketPriority.LOW;
            case HARDWARE, SOFTWARE, BILLING, ACCOUNT, NETWORK -> TicketPriority.MEDIUM;
        };
    }

    private static TicketPriority escalateForReopens(TicketPriority priority, int reopenCount) {
        if (reopenCount <= 0 || priority.compareTo(REOPEN_ESCALATION_CEILING) >= 0) {
            return priority;
        }
        int escalated = Math.min(priority.ordinal() + reopenCount, REOPEN_ESCALATION_CEILING.ordinal());
        return TicketPriority.values()[escalated];
    }

    private static TicketPriority max(TicketPriority a, TicketPriority b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Lower-cases and turns typographic apostrophes into plain ones, so "can’t" matches "can't". */
    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'');
    }

    /**
     * One alternation over all phrases, each anchored on both sides by a
     * non-alphanumeric character or the end of the text, with any run of
     * whitespace allowed between words.
     */
    private static Pattern phrasePattern(List<String> phrases) {
        String alternatives = phrases.stream()
                .map(phrase -> Pattern.quote(phrase).replace(" ", "\\E\\s+\\Q"))
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow();
        return Pattern.compile("(?<![a-z0-9])(?:" + alternatives + ")(?![a-z0-9])");
    }
}
