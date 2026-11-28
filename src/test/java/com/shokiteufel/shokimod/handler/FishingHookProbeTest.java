package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Fishing log (28.11.2026): die Zeitanzeige am Haken fehlte zwischendurch im Overlay, obwohl der Haken sie zeigte. Das Protokoll schreibt auf, was um den
 * Haken steht - hier die Teile, die sich ohne Spiel pruefen lassen.
 */
class FishingHookProbeTest {

    private static FishingHookProbe.Seen stand(int id, String legacy, boolean inSearch, boolean chosen) {
        return new FishingHookProbe.Seen(id, "armor_stand", legacy, 0.9, 0.7, inSearch, chosen, true, true, FishingHookDisplay.read(legacy));
    }

    @Test
    void aNameTheModReadsIsReportedAsRead() {
        assertEquals("read", FishingHookProbe.whyNotRead("§e§l2.4"));
        assertEquals("read", FishingHookProbe.whyNotRead("§c§l!!!"));
    }

    @Test
    void theReasonForANameThatIsNotReadIsSaid() {
        assertTrue(FishingHookProbe.whyNotRead("§a§l2.4").startsWith("a number, but not yellow and bold"), "gruene Zahl");
        assertTrue(FishingHookProbe.whyNotRead("§e2.4").startsWith("a number, but not yellow and bold"), "nicht fett");
        assertTrue(FishingHookProbe.whyNotRead("§e§l2,4").startsWith("a number, but not yellow and bold"), "Komma statt Punkt");
        assertTrue(FishingHookProbe.whyNotRead("§c§l!!! §7(2)").startsWith("has !!! but not alone"));
        assertEquals("other text", FishingHookProbe.whyNotRead("§c§lYeti"));
        assertEquals("empty text", FishingHookProbe.whyNotRead("§r"));
        assertEquals("no name", FishingHookProbe.whyNotRead(null));
    }

    @Test
    void whatLooksLikeATimerIsFoundEvenIfTheModDoesNotReadIt() {
        assertTrue(FishingHookProbe.looksLikeTimer("§e§l2.4"), "gelesen");
        assertTrue(FishingHookProbe.looksLikeTimer("§a§l2.4"), "nicht gelesen, sieht aber so aus");        assertTrue(FishingHookProbe.looksLikeTimer("§c§l!!! §7(2)"));
        assertFalse(FishingHookProbe.looksLikeTimer("§c§lYeti"));
        assertFalse(FishingHookProbe.looksLikeTimer(""));
    }

    @Test
    void aDescriptionNamesTextCodesReadingDistanceAndSearchArea() {
        String read = FishingHookProbe.describe(stand(12, "§e§l2.4", true, true));
        assertTrue(read.contains("id=12 armor_stand \"§e§l2.4\""), read);
        assertTrue(read.contains("read as time 2.4 s"), read);
        assertTrue(read.contains("0.9 blocks from the hook, +0.7 up"), read);
        assertTrue(read.contains("inside the search area"), read);
        assertTrue(read.contains("USED"), read);

        String unread = FishingHookProbe.describe(stand(13, "§a§l2.4", false, false));
        assertTrue(unread.contains("not read: a number, but not yellow and bold"), unread);
        assertTrue(unread.contains("OUTSIDE the search area"), unread);
        assertFalse(unread.contains("USED"), unread);
    }

    @Test
    void theAlertIsDescribedAsTheAlert() {
        assertTrue(FishingHookProbe.describe(stand(5, "§c§l!!!", true, true)).contains("read as the alert"));
    }

    @Test
    void anotherKindOfThingIsNeverReadAsTheTimer() {
        FishingHookProbe.Seen text = new FishingHookProbe.Seen(9, "text_display", "§e§l2.4", 1.0, 1.0, true, false, false, true, null);
        assertTrue(FishingHookProbe.describe(text).contains("not read: not an armor stand"));
    }

    @Test
    void theSignatureIgnoresTheCountdownOfAReadTimerButNotAnUnreadOne() {
        // Gelesen: jeder Zehntelschritt waere sonst eine eigene Zeile
        String a = FishingHookProbe.signatureOf(List.of(stand(12, "§e§l2.4", true, true)), "time");
        String b = FishingHookProbe.signatureOf(List.of(stand(12, "§e§l2.3", true, true)), "time");
        assertEquals(a, b);
        // Nicht gelesen: in der Luecke will man jeden Schritt sehen
        String c = FishingHookProbe.signatureOf(List.of(stand(12, "§a§l2.4", true, false)), null);
        String d = FishingHookProbe.signatureOf(List.of(stand(12, "§a§l2.3", true, false)), null);
        assertNotEquals(c, d);
    }

    @Test
    void theSignatureChangesWithTheOverlayAndTheSearchArea() {
        List<FishingHookProbe.Seen> inside = List.of(stand(12, "§e§l2.4", true, true));
        List<FishingHookProbe.Seen> outside = List.of(stand(12, "§e§l2.4", false, false));
        assertNotEquals(FishingHookProbe.signatureOf(inside, "time"), FishingHookProbe.signatureOf(outside, null));
        assertNotEquals(FishingHookProbe.signatureOf(inside, "time"), FishingHookProbe.signatureOf(inside, "alert"));
    }

    @Test
    void anEmptyOverlayWithATimerLookingThingNextToTheHookIsAGap() {
        // Der Fall des Users: der Haken zeigt die Zeit, das Overlay nicht
        assertTrue(FishingHookProbe.isGap(List.of(stand(12, "§a§l2.4", true, false)), null), "gruene Zahl: nicht gelesen");
        assertTrue(FishingHookProbe.isGap(List.of(stand(12, "§e§l2.4", false, false)), null), "gelesen, aber ausserhalb des Suchbereichs - das Overlay blieb leer");
        assertFalse(FishingHookProbe.isGap(List.of(stand(12, "§e§l2.4", true, true)), "time"), "das Overlay steht");
        assertFalse(FishingHookProbe.isGap(List.of(stand(12, "§c§lYeti", true, false)), null), "kein Zeitgeber");
        assertFalse(FishingHookProbe.isGap(List.of(), null), "nichts da");
    }

    @Test
    void sceneTextListsTheCandidatesOnOneLineAndCountsTheRest() {
        String text = FishingHookProbe.sceneText(List.of(stand(12, "§a§l2.4", true, false), stand(13, "§c§lYeti", false, false)), 5, null, null);
        assertFalse(text.contains("\n"), "eine Zeile");
        assertTrue(text.startsWith("overlay EMPTY | 2 candidates within 6 blocks (+5 mob names and damage numbers left out)"), text);
        assertTrue(text.contains("id=12") && text.contains("id=13"), text);

        String plain = FishingHookProbe.sceneText(List.of(stand(12, "§e§l2.4", true, true)), 0, "time", "§e§l2.4");
        assertTrue(plain.startsWith("overlay shows time \"§e§l2.4\" | 1 candidates"), plain);
        assertFalse(plain.contains("left out"), "nichts ausgelassen, nichts gesagt: " + plain);
    }

    @Test
    void mobNamesAndDamageNumbersAreNoiseButTimersAreNot() {
        // Aus dem Log vom 28.11.: so sahen die Dinge um den Haken aus
        assertTrue(FishingHookProbe.isNoise(stand(1, "§8[§7Lv60§8] §2§9 §cEnt §a20,625§f/§a25,000§c❤", false, false)), "Mob mit Leben");
        assertTrue(FishingHookProbe.isNoise(stand(2, "§6540", true, false)), "Schadenszahl");
        assertTrue(FishingHookProbe.isNoise(stand(3, "§719", true, false)), "Schadenszahl in grau");
        assertTrue(FishingHookProbe.isNoise(stand(4, "§f✧§f3§e1§6,§c8§c3§f8§f✧", false, false)), "kritischer Treffer mit Sternen");
        assertTrue(FishingHookProbe.isNoise(stand(5, "§r", false, false)), "leer");
        assertFalse(FishingHookProbe.isNoise(stand(6, "§e§l1.4", true, true)), "der Zeitgeber");
        assertFalse(FishingHookProbe.isNoise(stand(7, "§a§l2.4", true, false)), "ein Zeitgeber in anderer Farbe darf nicht verschwinden");
        assertFalse(FishingHookProbe.isNoise(stand(8, "§c§l!!!", true, true)), "der Alarm");
        assertFalse(FishingHookProbe.isNoise(stand(9, "§c§lALARM!", true, false)), "unbekannter Text bleibt sichtbar");
        FishingHookProbe.Seen other = new FishingHookProbe.Seen(10, "text_display", "§6540", 1.0, 1.0, true, false, false, true, null);
        assertFalse(FishingHookProbe.isNoise(other), "nur bei Ruestungsstaendern");
    }

    @Test
    void aWholeNumberIsADamageNumberNotATimer() {
        // Bis 2.6.15 zaehlte jede Zahl: Jeder Treffer auf einen Mob war eine falsche Luecke
        assertFalse(FishingHookProbe.looksLikeTimer("§719"));
        assertFalse(FishingHookProbe.looksLikeTimer("§6540"));
        assertTrue(FishingHookProbe.looksLikeTimer("§a2.4"), "mit Dezimalpunkt");
        assertFalse(FishingHookProbe.isGap(List.of(stand(1, "§719", true, false)), null), "keine Luecke");
    }

    @Test
    void onlyNoteBlockSoundsAreWrittenOneByOne() {
        assertTrue(FishingHookProbe.soundIsDetailed("minecraft:block.note_block.pling"));
        assertTrue(FishingHookProbe.soundIsDetailed("minecraft:block.note_block.bell"));
        assertFalse(FishingHookProbe.soundIsDetailed("minecraft:entity.generic.swim"));
        assertFalse(FishingHookProbe.soundIsDetailed("rfu:rod_timer"));
        assertFalse(FishingHookProbe.soundIsDetailed(null));
    }

    @Test
    void theSoundsOfACastAreCountedWithTheirSpans() {
        java.util.Map<String, FishingHookProbe.SoundStat> sounds = new java.util.TreeMap<>();
        for (float pitch : new float[] {1.3f, 1.4f, 2.0f}) sounds.computeIfAbsent("rfu:rod_timer", FishingHookProbe.SoundStat::new).add(pitch, 1.0f);
        sounds.computeIfAbsent("minecraft:entity.generic.swim", FishingHookProbe.SoundStat::new).add(0.86f, 0.03f);
        sounds.computeIfAbsent("minecraft:entity.generic.swim", FishingHookProbe.SoundStat::new).add(1.03f, 0.07f);
        sounds.computeIfAbsent("minecraft:entity.generic.swim", FishingHookProbe.SoundStat::new).add(0.90f, 0.05f);
        sounds.computeIfAbsent("minecraft:entity.generic.swim", FishingHookProbe.SoundStat::new).add(0.95f, 0.06f);
        sounds.computeIfAbsent("minecraft:entity.arrow.shoot", FishingHookProbe.SoundStat::new).add(1.0f, 1.0f);

        assertEquals("while the hook was out (2650 ms): minecraft:entity.generic.swim x4 (pitch 0.86-1.03, vol 0.03-0.07), rfu:rod_timer x3 (pitch 1.30-2.00, vol 1.00),"
                + " minecraft:entity.arrow.shoot x1 (pitch 1.00, vol 1.00)", FishingHookProbe.soundSummary(sounds, 2650L));
    }

    @Test
    void noSoundsNoSummary() {
        assertEquals(null, FishingHookProbe.soundSummary(new java.util.TreeMap<>(), 1000L));
    }

    @Test
    void aSoundWithoutKnownValuesIsStillCounted() {
        FishingHookProbe.SoundStat stat = new FishingHookProbe.SoundStat("minecraft:x");
        stat.add(Float.NaN, Float.NaN);
        assertEquals("minecraft:x x1", stat.describe());
    }

    @Test
    void aSoundLineTellsIdValuesPlaceAndTiming() {
        String line = FishingHookProbe.soundText("minecraft:block.note_block.pling", "pitch=1.00 vol=1.00", "0.6 blocks from the hook", 4210L, true, "none", 4200L);
        assertEquals("minecraft:block.note_block.pling pitch=1.00 vol=1.00 | 0.6 blocks from the hook | hook out for 4210 ms | overlay none for 4200 ms", line);
    }

    @Test
    void aSoundAfterTheHookIsGoneSaysSo() {
        String line = FishingHookProbe.soundText("minecraft:entity.fishing_bobber.retrieve", "pitch=1.00 vol=1.00", "relative (not at a place)", 9000L, false, "alert", -1L);
        assertTrue(line.contains("hook gone, it was out for 9000 ms"), line);
        assertFalse(line.contains(" for -1"), "ohne Wechsel keine Dauer: " + line);
    }
}
