package com.ibrahim.helpdesk.ticket.priority;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.ibrahim.helpdesk.ticket.entity.TicketCategory.BILLING;
import static com.ibrahim.helpdesk.ticket.entity.TicketCategory.HARDWARE;
import static com.ibrahim.helpdesk.ticket.entity.TicketCategory.OTHER;
import static com.ibrahim.helpdesk.ticket.entity.TicketCategory.SECURITY;
import static com.ibrahim.helpdesk.ticket.entity.TicketCategory.SOFTWARE;
import static com.ibrahim.helpdesk.ticket.entity.TicketPriority.CRITICAL;
import static com.ibrahim.helpdesk.ticket.entity.TicketPriority.HIGH;
import static com.ibrahim.helpdesk.ticket.entity.TicketPriority.LOW;
import static com.ibrahim.helpdesk.ticket.entity.TicketPriority.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedTicketPriorityPolicyTest {

    private final TicketPriorityPolicy policy = new RuleBasedTicketPriorityPolicy();

    private TicketPriority priority(TicketCategory category, String title, String description) {
        return priority(category, title, description, 0);
    }

    private TicketPriority priority(TicketCategory category, String title, String description, int reopens) {
        return policy.determine(new PriorityInput(category, title, description, reopens));
    }

    /** Neutral wording that matches no signal list. */
    private TicketPriority neutral(TicketCategory category) {
        return priority(category, "Printer paper tray", "The tray sticks when I open it");
    }

    @Nested
    @DisplayName("Category baseline")
    class Baseline {

        @Test
        void securityIsHigh() {
            assertThat(neutral(SECURITY)).isEqualTo(HIGH);
        }

        @Test
        void otherIsLow() {
            assertThat(neutral(OTHER)).isEqualTo(LOW);
        }

        @ParameterizedTest(name = "{0} is MEDIUM")
        @EnumSource(value = TicketCategory.class, names = {"HARDWARE", "SOFTWARE", "BILLING", "ACCOUNT", "NETWORK"})
        void everythingElseIsMedium(TicketCategory category) {
            assertThat(neutral(category)).isEqualTo(MEDIUM);
        }

        @Test
        @DisplayName("every category has a baseline, so adding one cannot silently fall through")
        void everyCategoryCovered() {
            for (TicketCategory category : TicketCategory.values()) {
                assertThat(neutral(category)).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Incident signals make a ticket CRITICAL")
    class Critical {

        static final List<String> SIGNALS = RuleBasedTicketPriorityPolicy.CRITICAL_SIGNALS;

        @ParameterizedTest(name = "\"{0}\"")
        @FieldSource("SIGNALS")
        void everyCriticalSignal(String signal) {
            assertThat(priority(OTHER, "Help", "We think there is " + signal + " here")).isEqualTo(CRITICAL);
        }

        @ParameterizedTest(name = "in category {0}")
        @EnumSource(TicketCategory.class)
        void overridesEveryCategory(TicketCategory category) {
            assertThat(priority(category, "Server down", "Nothing loads")).isEqualTo(CRITICAL);
        }

        @Test
        @DisplayName("wins even alongside calm wording")
        void beatsCalmWording() {
            assertThat(priority(OTHER, "Quick question", "Is this a data breach? No rush")).isEqualTo(CRITICAL);
        }

        @Test
        @DisplayName("matches in the title as well as the description")
        void matchesTitle() {
            assertThat(priority(HARDWARE, "RANSOMWARE on my laptop", "See screenshot")).isEqualTo(CRITICAL);
        }
    }

    @Nested
    @DisplayName("Urgency signals raise a ticket to at least HIGH")
    class High {

        static final List<String> SIGNALS = RuleBasedTicketPriorityPolicy.HIGH_SIGNALS;

        @ParameterizedTest(name = "\"{0}\"")
        @FieldSource("SIGNALS")
        void everyHighSignal(String signal) {
            assertThat(priority(OTHER, "Help", "Right now " + signal + " for us")).isEqualTo(HIGH);
        }

        @Test
        @DisplayName("an urgency signal outranks low-urgency wording")
        void beatsLowSignals() {
            assertThat(priority(SOFTWARE, "Question", "How do I stop the app crashing?")).isEqualTo(HIGH);
        }

        @Test
        @DisplayName("does not lower a SECURITY ticket and does not raise it past HIGH")
        void securityStaysHigh() {
            assertThat(priority(SECURITY, "Phishing email", "Urgent, suspicious link")).isEqualTo(HIGH);
        }
    }

    @Nested
    @DisplayName("Low-urgency signals lower a ticket to LOW")
    class Low {

        static final List<String> SIGNALS = RuleBasedTicketPriorityPolicy.LOW_SIGNALS;
        static final List<String> CALM = RuleBasedTicketPriorityPolicy.CALM_PHRASES;

        @ParameterizedTest(name = "\"{0}\"")
        @FieldSource("SIGNALS")
        void everyLowSignal(String signal) {
            assertThat(priority(BILLING, "Invoices", "A " + signal + " about invoice layout")).isEqualTo(LOW);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @FieldSource("CALM")
        void everyCalmPhrase(String phrase) {
            assertThat(priority(HARDWARE, "Monitor", "Flickers slightly, " + phrase)).isEqualTo(LOW);
        }

        @ParameterizedTest(name = "\"{0}\" does not count as urgent")
        @ValueSource(strings = {"This is not urgent", "Non-urgent request", "Not an emergency, just odd"})
        void negatedUrgencyIsNotHigh(String description) {
            assertThat(priority(SOFTWARE, "Toolbar", description)).isEqualTo(LOW);
        }

        @Test
        @DisplayName("never lowers a SECURITY ticket")
        void securityIsNeverLowered() {
            assertThat(priority(SECURITY, "Question about 2FA", "How do I enable it? No rush")).isEqualTo(HIGH);
        }
    }

    @Nested
    @DisplayName("Matching")
    class Matching {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
                "App CRASH on start      | HIGH",
                "Can’t log in since Monday | HIGH",
                "cannot    log   in      | HIGH",
                "crash.                  | HIGH",
                "(outage)                | CRITICAL",
                "crashpad settings       | MEDIUM",
                "the breacher tool       | MEDIUM",
                "reviruses scanner       | MEDIUM",
                "everyoneelse            | MEDIUM"
        })
        void wholeWordsCaseInsensitiveTypographicApostrophes(String description, TicketPriority expected) {
            assertThat(priority(SOFTWARE, "Report", description)).isEqualTo(expected);
        }

        @Test
        @DisplayName("negation is not understood beyond the listed calm phrases")
        void negationLimitationIsDocumented() {
            assertThat(priority(SOFTWARE, "Update", "We are not blocked by this")).isEqualTo(HIGH);
        }

        @Test
        @DisplayName("tolerates null title, description and category")
        void nullSafe() {
            assertThat(policy.determine(new PriorityInput(null, null, null, 0))).isEqualTo(MEDIUM);
        }

        @Test
        @DisplayName("is deterministic")
        void deterministic() {
            PriorityInput input = new PriorityInput(HARDWARE, "Laptop crash", "Blue screen", 1);
            TicketPriority first = policy.determine(input);
            for (int i = 0; i < 20; i++) {
                assertThat(policy.determine(input)).isEqualTo(first);
            }
        }
    }

    @Nested
    @DisplayName("Reopen escalation")
    class Reopens {

        @ParameterizedTest(name = "{0} reopened {1} time(s) -> {2}")
        @CsvSource({
                "OTHER,    0, LOW",
                "OTHER,    1, MEDIUM",
                "OTHER,    2, HIGH",
                "OTHER,    9, HIGH",
                "HARDWARE, 1, HIGH",
                "HARDWARE, 5, HIGH",
                "SECURITY, 3, HIGH"
        })
        void escalatesOneLevelPerReopenUpToHigh(TicketCategory category, int reopens, TicketPriority expected) {
            assertThat(priority(category, "Printer paper tray", "The tray sticks when I open it", reopens))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("reopening never creates CRITICAL, and a CRITICAL ticket stays CRITICAL")
        void criticalOnlyFromIncidentSignals() {
            assertThat(priority(OTHER, "Question", "How do I?", 50)).isEqualTo(HIGH);
            assertThat(priority(OTHER, "Outage", "Everything is down", 3)).isEqualTo(CRITICAL);
        }

        @Test
        @DisplayName("a calm ticket still escalates when reopened")
        void calmTicketEscalates() {
            assertThat(priority(BILLING, "Invoice layout", "No rush", 1)).isEqualTo(MEDIUM);
        }
    }
}
