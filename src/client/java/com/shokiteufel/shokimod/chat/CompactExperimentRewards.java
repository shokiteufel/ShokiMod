package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Compact Enchanting Rewards: Nach dem Experimentation Table ("You claimed the Ultrasequencer rewards!" und darunter eine Zeile je Belohnung wie
 * "+600k Enchanting Exp") bleibt eine Zeile, die Belohnungen stehen beim Zeigen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CompactExperimentRewards}). Von dort stammt die Regel: Sie gilt nur, solange der Experimentation Table (oder eines
 * seiner Spiele: Superpairs, Chronomatron, Ultrasequencer) in den letzten drei Sekunden offen war; die Zeile "You claimed the X rewards!" und jede Zeile "+..." danach
 * werden ausgeblendet und gemerkt, und 100 Millisekunden nach der letzten Belohnung steht die Zeile mit dem Anspruch da, mit den Belohnungen beim Zeigen. Die
 * Umsetzung ist eigene Arbeit: Java, die Uhr ist der Tick des Chats ({@code poll}) statt einer verzoegerten Aufgabe, und zwei Abweichungen: Eine Belohnung zaehlt
 * erst, wenn die Zeile mit dem Anspruch vorher kam (sonst verschwaenden alle Zeilen "+..." der drei Sekunden), und kommt nach dem Anspruch eine Sekunde lang keine
 * Belohnung, wird die Zeile mit dem Anspruch unveraendert nachgeschrieben, damit nichts verloren geht.
 */
final class CompactExperimentRewards {

    private static final Pattern CLAIM = Pattern.compile("You claimed the \\S+ rewards!");
    private static final Pattern DROP = Pattern.compile(" \\+(?<reward>.*)");

    /** So lange nach der letzten Belohnung wird gewartet, ehe die Zeile steht */
    static final long QUIET_MS = 100L;
    /** Kommt nach dem Anspruch keine Belohnung, wird er so lange gehalten */
    static final long NO_REWARD_MS = 1000L;

    private StyledText claim = null;
    private long claimAt = 0L;
    private long lastRewardAt = 0L;
    private final List<String> rewards = new ArrayList<>();

    void reset() {
        claim = null;
        rewards.clear();
    }

    boolean pending() {
        return claim != null;
    }

    /** Eine Zeile; null heisst, sie geht den Filter nichts an. {@code windowOpen}: war der Experimentation Table gerade offen? */
    ChatVerdict line(Incoming in, boolean windowOpen, long now) {
        if (!windowOpen) return null;
        String plain = in.plain;
        if (plain.indexOf("You claimed the ") == 0) {
            if (!CLAIM.matcher(plain).matches()) return null;
            // Ein neuer Anspruch, ehe der alte zu Ende ist: den alten zuerst schreiben, damit er nicht verloren geht
            List<ChatVerdict.Line> before = claim == null ? List.of() : flushLines();
            claim = in.styled();
            claimAt = now;
            lastRewardAt = 0L;
            rewards.clear();
            return before.isEmpty() ? ChatVerdict.HIDE : new ChatVerdict(ChatVerdict.Kind.REPLACE, before);
        }
        if (claim == null || plain.length() < 3 || plain.charAt(0) != ' ' || plain.charAt(1) != '+') return null;
        Matcher m = DROP.matcher(plain);
        if (!m.matches()) return null;
        rewards.add(in.styled().legacy(m.start("reward"), m.end("reward")));
        lastRewardAt = now;
        return ChatVerdict.HIDE;
    }

    /** Ist etwas fertig zum Schreiben? Vom Tick gerufen */
    ChatVerdict poll(long now) {
        if (claim == null) return ChatVerdict.SHOW;
        boolean due = rewards.isEmpty() ? now - claimAt >= NO_REWARD_MS : now - lastRewardAt >= QUIET_MS;
        if (!due) return ChatVerdict.SHOW;
        return new ChatVerdict(ChatVerdict.Kind.REPLACE, flushLines());
    }

    /** Die Zeile mit dem Anspruch (mit den Belohnungen beim Zeigen), und der Zustand ist leer */
    private List<ChatVerdict.Line> flushLines() {
        StyledText line = claim;
        List<String> hover = new ArrayList<>(rewards.size());
        for (String r : rewards) hover.add("§8+" + r);
        claim = null;
        rewards.clear();
        if (hover.isEmpty()) return List.of(ChatVerdict.Line.ofComponent(line.toComponent()));
        StyledText shown = line.withEvents(null, StyledText.hoverOf(String.join("\n", hover)));
        return List.of(ChatVerdict.Line.ofComponent(shown.toComponent()));
    }
}
