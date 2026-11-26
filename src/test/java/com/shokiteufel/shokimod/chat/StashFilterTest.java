package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.shokiteufel.shokimod.chat.ChatTestSupport.HUB;
import static com.shokiteufel.shokimod.chat.ChatTestSupport.types;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Chat Filter Types > Stash Messages: die Zeilen aus SkyHannis Tests, die Zeilenfolge der Warnung und die Wahl, die darauf wirkt. */
class StashFilterTest {

    private static final String MATERIALS = "                 You have 226 materials stashed away!";
    private static final String TYPES = "               (This totals 3 types of materials stashed!)";
    private static final String PICKUP = "                >>> CLICK HERE to pick them up! <<<";

    private static final class Run {
        final ChatFilterEngine engine = new ChatFilterEngine();
        final ChatTabCategory config;
        final ChatPlace place = HUB;
        long now = 1000L;

        Run(ChatTabCategory config) {
            this.config = config;
        }

        ChatVerdict line(String line) {
            now += 20;
            return engine.decide(line, config, place, now);
        }
    }

    private static ChatTabCategory stash(Consumer<ChatFilterTypesCategory> more) {
        return types(f -> {
            f.stashEnabled = true;
            more.accept(f);
        });
    }

    private static void assertKind(ChatVerdict.Kind kind, ChatVerdict v, String line) {
        assertEquals(kind, v.kind(), line);
    }

    /** Die Warnung einmal durchspielen und das Urteil der Zeile mit dem Klick zurueckgeben */
    private static ChatVerdict warn(Run run, String count, String types, String pickup) {
        assertKind(ChatVerdict.Kind.HIDE, run.line(count), "count");
        if (types != null) assertKind(ChatVerdict.Kind.HIDE, run.line(types), "types");
        return run.line(pickup);
    }

    @Test
    void fourLinesBecomeOneWithAClick() {
        Run run = new Run(stash(f -> {
        }));
        ChatVerdict v = warn(run, MATERIALS, TYPES, PICKUP);
        assertKind(ChatVerdict.Kind.REPLACE, v, "pickup line");
        ChatVerdict.Line line = v.lines().get(0);
        assertEquals("§bYou have §3226 §bmaterials in stash, §btotalling §33 types§b. §bClick to §3pickup §byour stash!", line.text());
        assertEquals("/pickupstash", line.command());
        assertEquals("§eClick to pickup your material stash!", line.hover());
        // Die leere Zeile danach geht mit - nur die naechste
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "empty after");
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "other");
    }

    @Test
    void itemsAreYellowAndASingleItemIsInTheSingular() {
        Run run = new Run(stash(f -> {
        }));
        ChatVerdict v = warn(run, "                     You have 1 item stashed away!", "               (This totals 1 type of item stashed!)", PICKUP);
        assertEquals("§eYou have §61 §eitem in stash, §etotalling §61 type§e. §eClick to §6pickup §eyour stash!", v.lines().get(0).text());
    }

    @Test
    void theBigNumberIsShortened() {
        Run run = new Run(stash(f -> {
        }));
        ChatVerdict v = warn(run, "                 You have 1,500 items stashed away!", null, PICKUP);
        assertEquals("§eYou have §61.5k §eitems in stash. §eClick to §6pickup §eyour stash!", v.lines().get(0).text());
    }

    @Test
    void aNonStashLineIsLeftAlone() {
        Run run = new Run(stash(f -> {
        }));
        assertKind(ChatVerdict.Kind.SHOW, run.line("Hello"), "chat");
        assertKind(ChatVerdict.Kind.SHOW, run.line("You have a stash somewhere"), "word stash");
        assertKind(ChatVerdict.Kind.SHOW, run.line(""), "empty without a stash line before");
    }

    @Test
    void theSampleLinesOfSkyHanniAreRecognised() {
        String[] counts = {MATERIALS, "                 You have 22 materials stashed away!", "                 You have 1,000 items stashed away!",
                "                     You have 2 items stashed away!", "                   You have 109 items stashed away!"};
        for (String s : counts) assertKind(ChatVerdict.Kind.HIDE, new Run(stash(f -> {
        })).line(s), s);
        String[] types = {"               (This totals 1 type of material stashed!)", "               (This totals 2 types of items stashed!)",
                "               (This totals 3 types of materials stashed!)", "               (This totals 4 types of items stashed!)",
                "              (This totals 8 types of materials stashed!)"};
        for (String s : types) assertKind(ChatVerdict.Kind.HIDE, new Run(stash(f -> {
        })).line(s), s);
    }

    @Test
    void theAddedLineGoesOnWish() {
        String items = "One or more items didn't fit in your inventory and were added to your item stash! Click here to pick them up!";
        String materials = "One or more materials didn't fit in your inventory and were added to your material stash! Click here to pick them up!";
        Run run = new Run(stash(f -> {
        }));
        assertKind(ChatVerdict.Kind.HIDE, run.line(items), "items");
        assertKind(ChatVerdict.Kind.HIDE, run.line(""), "empty after");
        assertKind(ChatVerdict.Kind.HIDE, run.line(materials), "materials");
        Run keep = new Run(stash(f -> f.stashHideAdded = false));
        assertKind(ChatVerdict.Kind.SHOW, keep.line(items), "kept");
    }

    @Test
    void theSameCountIsReportedOnlyOnce() {
        Run run = new Run(stash(f -> {
        }));
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "first");
        // Dieselbe Menge noch einmal: Die Zeilen gehen, die kurze kommt nicht
        assertKind(ChatVerdict.Kind.HIDE, warn(run, MATERIALS, TYPES, PICKUP), "second");
        // Eine andere Menge kommt wieder
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, "                 You have 300 materials stashed away!", TYPES, PICKUP), "other count");
        // Und ein anderer Stand desselben Typs wie der erste ebenfalls
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "back to the first");
    }

    @Test
    void duplicatesCanBeAllowed() {
        Run run = new Run(stash(f -> f.stashHideDupes = false));
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "first");
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "second");
    }

    @Test
    void aNewWorldReportsTheSameCountAgain() {
        Run run = new Run(stash(f -> {
        }));
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "first");
        run.engine.worldChanged(run.config);
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "after a world change");
        // Mit "Once Per World" aus bleibt der Stand ueber den Weltwechsel
        Run keep = new Run(stash(f -> f.stashOncePerWorld = false));
        assertKind(ChatVerdict.Kind.REPLACE, warn(keep, MATERIALS, TYPES, PICKUP), "first");
        keep.engine.worldChanged(keep.config);
        assertKind(ChatVerdict.Kind.HIDE, warn(keep, MATERIALS, TYPES, PICKUP), "after a world change, remembered");
    }

    @Test
    void lowCountsAreHidden() {
        Run run = new Run(stash(f -> f.stashHideLow = 300));
        assertKind(ChatVerdict.Kind.HIDE, warn(run, MATERIALS, TYPES, PICKUP), "226 is not above 300");
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, "                 You have 301 materials stashed away!", TYPES, PICKUP), "301 is");
    }

    @Test
    void viewStashChangesTheClick() {
        Run run = new Run(stash(f -> f.stashUseViewStash = true));
        ChatVerdict.Line line = warn(run, MATERIALS, TYPES, PICKUP).lines().get(0);
        assertEquals("/viewstash material", line.command());
        assertEquals("§bYou have §3226 §bmaterials in stash, §btotalling §33 types§b. §bClick to §3view §byour stash!", line.text());
        assertEquals("§eClick to view your material stash!", line.hover());
    }

    @Test
    void theTwoTypesAreCountedApart() {
        Run run = new Run(stash(f -> {
        }));
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, MATERIALS, TYPES, PICKUP), "materials");
        // Dieselbe Zahl bei Items ist ein anderer Stand
        assertKind(ChatVerdict.Kind.REPLACE, warn(run, "                 You have 226 items stashed away!", null, PICKUP), "items");
    }

    @Test
    void aPickupLineWithoutACountOnlyGoes() {
        Run run = new Run(stash(f -> {
        }));
        assertKind(ChatVerdict.Kind.HIDE, run.line(PICKUP), "no count before");
    }

    @Test
    void offMeansNothingHappens() {
        Run run = new Run(new ChatTabCategory());
        assertKind(ChatVerdict.Kind.SHOW, run.line(MATERIALS), "count");
        assertKind(ChatVerdict.Kind.SHOW, run.line(PICKUP), "pickup");
    }

    @Test
    void theCompactLineIsAPureFunctionOfTheStand() {
        StashFilter.Stand stand = new StashFilter.Stand(1_234_567, "item");
        stand.types = 12;
        ChatVerdict.Line line = StashFilter.compactLine(stand, new ChatTabCategory().filterTypes);
        assertEquals("§eYou have §61.2M §eitems in stash, §etotalling §612 types§e. §eClick to §6pickup §eyour stash!", line.text());
        assertNull(new StashFilter().line("nothing", new ChatTabCategory().filterTypes));
    }
}
