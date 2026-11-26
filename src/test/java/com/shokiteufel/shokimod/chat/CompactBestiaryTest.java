package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.Chat2Support.Place;
import com.shokiteufel.shokimod.chat.Chat2Support.Run;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.shokiteufel.shokimod.chat.Chat2Support.assertKind;
import static com.shokiteufel.shokimod.chat.Chat2Support.config;
import static com.shokiteufel.shokimod.chat.Chat2Support.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Chat > Compact Bestiary Messages: die Zeilenfolge des Blocks (lange Linie, BESTIARY, Stufe, Beschreibung, lange Linie), die kurze Zeile mit Hover und Klick,
 * der Meilenstein, und die Notbremsen. Die Zeilen sind nach den Mustern von SkyHanni gebaut (es gibt dort kein Beispiel des ganzen Blocks).
 */
class CompactBestiaryTest {

    private static final String BORDER = "§3§l" + "▬".repeat(64);
    private static final String TITLE = "§f" + " ".repeat(34) + "§6§lBESTIARY";
    private static final String LEVEL = "§f" + " ".repeat(10) + "§aZombie 4";
    private static final String DESCRIPTION = "§7Kill 100 more to reach level 5.";
    private static final String REWARD = "§8+§b5 Magic Find";

    private static Run on() {
        return new Run(config(c -> c.compactBestiary = true), new Place("Hub"));
    }

    @Test
    void theBlockBecomesOneLineBetweenTwoLongLines() {
        Run run = on();
        assertKind(ChatVerdict.Kind.SHOW, run.line(BORDER), "the opening line is shown before anyone knows what follows");
        assertKind(ChatVerdict.Kind.HIDE, run.line(TITLE), "title");
        assertKind(ChatVerdict.Kind.HIDE, run.line(LEVEL), "level");
        assertKind(ChatVerdict.Kind.HIDE, run.line(DESCRIPTION), "description");
        assertKind(ChatVerdict.Kind.HIDE, run.line(REWARD), "reward");
        ChatVerdict v = run.line(BORDER);
        assertKind(ChatVerdict.Kind.REPLACE, v, "closing line");
        assertEquals(2, v.lines().size());
        // Die kurze Zeile: BESTIARY, dahinter die Zeile mit der Stufe
        assertEquals(normal("§6§lBESTIARY §r§aZombie 4"), v.lines().get(0).legacyText());
        // Und die schliessende Linie noch einmal, wie sie war
        assertEquals(normal(BORDER), v.lines().get(1).legacyText());
    }

    @Test
    void theDescriptionIsTheTextOnHoverWithoutTheLastLine() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        run.line(LEVEL);
        run.line(DESCRIPTION);
        run.line(REWARD);
        ChatVerdict v = run.line(BORDER);
        Component compact = v.lines().get(0).component();
        HoverEvent hover = StyledText.of(compact).firstHover();
        Component tip = ((HoverEvent.ShowText) hover).value();
        // Alles bis auf die letzte Zeile (die Belohnung), ohne die weisse Farbe und ohne Leerzeichen am Rand
        assertEquals("BESTIARY\nZombie 4\nKill 100 more to reach level 5.", tip.getString());
    }

    @Test
    void theClickOfTheTitleLineStaysOnTheShortLine() {
        Run run = on();
        run.line(BORDER);
        Component title = Component.literal("").append(Component.literal(" ".repeat(34) + "BESTIARY")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/bestiary"))));
        // Mit Stil: Der Klick sitzt am Titel, den Klartext vergleicht der Filter
        ChatVerdict first = run.engine.decide(" ".repeat(34) + "BESTIARY", title, run.config, run.place, run.now += 20);
        assertKind(ChatVerdict.Kind.HIDE, first, "title with a click");
        run.line(LEVEL);
        ChatVerdict v = run.line(BORDER);
        ClickEvent click = StyledText.of(v.lines().get(0).component()).firstClick();
        assertEquals("/bestiary", ((ClickEvent.RunCommand) click).command());
    }

    @Test
    void aMilestoneLineIsWrittenAsItsOwnLineAndNotInTheDescription() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        run.line(LEVEL);
        // "Bestiary Milestone 3 -> 4": grau und gelb am Ende der Zeile
        assertKind(ChatVerdict.Kind.HIDE, run.line("§6Bestiary Milestone §83➡§e4"), "the milestone line itself");
        ChatVerdict after = run.line("§7Something after it");
        assertKind(ChatVerdict.Kind.REPLACE, after, "the line after the milestone carries it out");
        assertEquals(List.of("§6§lBESTIARY MILESTONE §83➡§e4"), Chat2Support.texts(after));
        ChatVerdict v = run.line(BORDER);
        assertKind(ChatVerdict.Kind.REPLACE, v, "closing line");
        assertEquals(normal("§6§lBESTIARY §r§aZombie 4"), v.lines().get(0).legacyText());
        // Die Zeilen nach dem Meilenstein stehen nicht mehr in der Beschreibung: es bleibt nur "BESTIARY" (die Stufe ist die letzte Zeile und faellt weg)
        HoverEvent hover = StyledText.of(v.lines().get(0).component()).firstHover();
        assertEquals("BESTIARY", ((HoverEvent.ShowText) hover).value().getString());
    }

    @Test
    void aMilestoneRightBeforeTheClosingLineIsWrittenWithIt() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        run.line(LEVEL);
        run.line("§6Bestiary Milestone §83➡§e4");
        ChatVerdict v = run.line(BORDER);
        assertEquals(3, v.lines().size());
        assertEquals("§6§lBESTIARY MILESTONE §83➡§e4", v.lines().get(0).legacyText());
    }

    @Test
    void aBlockThatNeverEndsIsGivenUpAfterFifteenLines() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        for (int i = 0; i < 15; i++) assertKind(ChatVerdict.Kind.HIDE, run.line("§7line " + i), "inside " + i);
        assertKind(ChatVerdict.Kind.HIDE, run.line("§7line 16"), "the sixteenth is the last that goes");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§7after"), "state is gone");
        assertKind(ChatVerdict.Kind.SHOW, run.line(BORDER), "a closing line without a block stays");
    }

    @Test
    void aBlockThatStopsForTenSecondsIsForgotten() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        run.line(LEVEL);
        run.now += 10_001L;
        assertKind(ChatVerdict.Kind.SHOW, run.line(BORDER), "too late");
    }

    @Test
    void aNewWorldForgetsTheBlock() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        run.line(LEVEL);
        run.engine.worldChanged(run.config);
        assertKind(ChatVerdict.Kind.SHOW, run.line(BORDER), "after the world change");
    }

    @Test
    void aBlockWithoutALevelLineLeavesTheClosingLineAlone() {
        Run run = on();
        run.line(BORDER);
        run.line(TITLE);
        assertKind(ChatVerdict.Kind.SHOW, run.line(BORDER), "nothing to shorten");
    }

    @Test
    void aTitleWithAnotherNumberOfSpacesIsNotTheTitle() {
        Run run = on();
        assertKind(ChatVerdict.Kind.SHOW, run.line("§f" + " ".repeat(33) + "§6§lBESTIARY"), "33 spaces");
        assertKind(ChatVerdict.Kind.SHOW, run.line("§f" + " ".repeat(35) + "§6§lBESTIARY"), "35 spaces");
        assertKind(ChatVerdict.Kind.SHOW, run.line(LEVEL), "no block open");
    }

    @Test
    void offByDefaultAndOnlyInSkyBlock() {
        Run off = new Run(new ChatTabCategory(), new Place("Hub"));
        assertKind(ChatVerdict.Kind.SHOW, off.line(TITLE), "off");
        Place lobby = new Place("Hub");
        lobby.skyblock = false;
        Run elsewhere = new Run(config(c -> c.compactBestiary = true), lobby);
        assertKind(ChatVerdict.Kind.SHOW, elsewhere.line(TITLE), "not SkyBlock");
        assertNull(elsewhere.poll(1000).lines().stream().findFirst().orElse(null));
    }
}
