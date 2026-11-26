package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Compact Bestiary Messages: Der Block, den Hypixel bei einer neuen Stufe im Bestiary schreibt (lange Linie, "BESTIARY", die Stufe mit Belohnungen, lange
 * Linie), wird zu einer Zeile "BESTIARY <Zeile mit der Stufe>"; die ganze Beschreibung steht beim Zeigen, der Klick des Servers bleibt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CompactBestiaryChatMessage}). Von dort stammt die Regel: Die Zeile "BESTIARY" (34 Leerzeichen davor) beginnt
 * den Block, alles bis zur schliessenden langen Linie wird gelesen und ausgeblendet (hoechstens 15 Zeilen), die erste Zeile danach ist die Stufe, die
 * Zeilen davor ausser der letzten sind der Text beim Zeigen; eine Zeile, die auf "<alt>➡<neu>" in Grau und Gelb endet, ist ein Meilenstein (sie und was
 * danach kommt zaehlt nicht mehr zur Beschreibung) und wird als eigene Zeile "BESTIARY MILESTONE <alt>➡<neu>" geschrieben. Die Umsetzung ist eigene Arbeit: Java,
 * der Zustand kommt nach zehn Sekunden und beim Weltwechsel zurueck, und eine Abweichung: Die OEFFNENDE lange Linie steht VOR der Zeile "BESTIARY" und ist
 * dann schon gezeigt - nachtraegliches Loeschen gibt es hier nicht. Deshalb bleibt sie stehen, und die schliessende Linie wird hinter der kurzen Zeile noch
 * einmal geschrieben: Die Zeile steht zwischen zwei Linien wie der Block vorher.
 */
final class CompactBestiary {

    /** Wie lange ein Block hoechstens dauert; er ist in Millisekunden da */
    static final long MAX_MS = 10_000L;
    /** Wie viele Zeilen ein Block hoechstens hat */
    static final int MAX_LINES = 15;

    /** "§f" + 34 Leerzeichen + "BESTIARY" - im Klartext 34 Leerzeichen und das Wort */
    static final String TITLE = " ".repeat(34) + "BESTIARY";

    /** Eine Zeile mit der Stufe zum Meilenstein: etwas, dann grau "alt", Pfeil, gelb "neu" (so steht es im Text mit Farbcodes) */
    private static final Pattern MILESTONE = Pattern.compile("^.+(§8\\d{1,3}➡§e\\d{1,3})$");

    private boolean inside = false;
    private long openedAt = 0L;
    private int blocked = 0;
    private boolean acceptMore = true;
    private ClickEvent click = null;
    private String milestone = null;
    private final List<String> lines = new ArrayList<>();

    void reset() {
        inside = false;
        blocked = 0;
        acceptMore = true;
        click = null;
        milestone = null;
        lines.clear();
    }

    /** Eine Zeile; null heisst, der Block hat dazu nichts zu sagen */
    ChatVerdict line(Incoming in, long now) {
        if (inside && now - openedAt > MAX_MS) reset();
        String plain = in.plain;
        // Billig zuerst: ausserhalb des Blocks interessiert nur die Zeile mit dem Titel
        if (!inside && plain.length() != TITLE.length()) return null;
        if (plain.equals(TITLE)) {
            reset();
            inside = true;
            openedAt = now;
            click = in.styled().firstClick();
            lines.add(in.styled().legacy());
            return ChatVerdict.HIDE;
        }
        if (!inside) return null;

        blocked++;
        if (blocked > MAX_LINES) {
            // Kein Ende in Sicht: aufgeben, damit nicht der ganze Chat verschwindet
            reset();
            return ChatVerdict.HIDE;
        }
        StyledText styled = in.styled();
        if (WrapperBlocks.isWrapper(plain)) return finish(styled);

        List<ChatVerdict.Line> out = new ArrayList<>();
        if (milestone != null) {
            out.add(ChatVerdict.Line.of("§6§lBESTIARY MILESTONE " + milestone));
            milestone = null;
        }
        String legacy = styled.legacy();
        Matcher m = MILESTONE.matcher(legacy);
        if (m.matches()) {
            acceptMore = false;
            milestone = m.group(1);
        }
        if (acceptMore) lines.add(legacy);
        return out.isEmpty() ? ChatVerdict.HIDE : new ChatVerdict(ChatVerdict.Kind.REPLACE, out);
    }

    /** Die schliessende Linie: die kurze Zeile, danach die Linie noch einmal */
    private ChatVerdict finish(StyledText border) {
        List<String> list = new ArrayList<>(lines.size());
        for (String l : lines) list.add(l.replace("§f", "").strip());
        ClickEvent clickEvent = click;
        String pendingMilestone = milestone;
        reset();

        List<ChatVerdict.Line> out = new ArrayList<>();
        if (pendingMilestone != null) out.add(ChatVerdict.Line.of("§6§lBESTIARY MILESTONE " + pendingMilestone));
        if (list.size() >= 2) {
            String title = list.get(1);
            String hover = String.join("\n", list.subList(0, list.size() - 1));
            MutableComponent compact = StyledText.ofLegacy("§6§lBESTIARY §r" + title).toComponent();
            out.add(ChatVerdict.Line.ofComponent(StyledText.withEvents(compact, clickEvent, StyledText.hoverOf(hover))));
        } else {
            // Es fehlt die Zeile mit der Stufe: nichts zu kuerzen, die Linie bleibt wie sie ist
            if (out.isEmpty()) return null;
        }
        out.add(ChatVerdict.Line.ofComponent(border.toComponent()));
        return new ChatVerdict(ChatVerdict.Kind.REPLACE, out);
    }
}
