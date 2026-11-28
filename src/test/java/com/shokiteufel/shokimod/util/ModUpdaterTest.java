package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Welche Datei holt "/shoki update"? Die neueste mit der Endung der eigenen Minecraft-Fassung.
 *
 * Die Lage, die den Fehler brachte (Meldung des Users, 28.11.): Seit 2.4.10 gibt es nur noch
 * Dateien fuer 26.3, die letzten fuer 26.2 und 26.1.x sind 2.4.9. Der Befehl las nur die
 * ersten 30 Eintraege der Liste - lauter 26.3 - und fand fuer 26.2 und 26.1.x nichts.
 */
class ModUpdaterTest {

    // ---- Bausteine

    /** Ein Release mit der Jar und der Quell-Jar, wie sie auf GitHub stehen */
    private static JsonObject release(String version, String branch) {
        JsonObject release = new JsonObject();
        release.addProperty("draft", false);
        JsonArray assets = new JsonArray();
        assets.add(asset("shokimod-" + version + branch + ".jar"));
        assets.add(asset("shokimod-" + version + branch + "-sources.jar"));
        release.add("assets", assets);
        return release;
    }

    private static JsonObject asset(String name) {
        JsonObject asset = new JsonObject();
        asset.addProperty("name", name);
        asset.addProperty("browser_download_url", "https://example.invalid/" + name);
        return asset;
    }

    /** Eine Seite, aufgefuellt mit Eintraegen fuer 26.3, damit sie voll ist */
    private static JsonArray fullPage(int firstMinor) {
        JsonArray page = new JsonArray();
        for (int i = 0; i < ModUpdater.PAGE_SIZE; i++) {
            page.add(release("2." + firstMinor + "." + i, "+26.3"));
        }
        return page;
    }

    /** Liefert die Seiten der Reihe nach und zaehlt, wie oft gefragt wurde */
    private static final class Source implements ModUpdater.Pages {
        final List<JsonArray> pages;
        final List<Integer> asked = new ArrayList<>();

        Source(JsonArray... pages) {
            this.pages = List.of(pages);
        }

        @Override
        public JsonArray page(int number) {
            asked.add(number);
            return number <= pages.size() ? pages.get(number - 1) : new JsonArray();
        }
    }

    /** Wie GitHub am 28.11.: ganz vorn nur 26.3, danach 2.4.9 fuer alle drei, danach Aelteres */
    private static Source githubOn28November() {
        JsonArray first = fullPage(6);
        JsonArray second = new JsonArray();
        second.add(release("2.4.9", "+26.3"));
        second.add(release("2.4.9", "+26.2"));
        second.add(release("2.4.9", "+26.1.x"));
        second.add(release("2.4.8", "+26.3"));
        second.add(release("2.4.8", "+26.2"));
        second.add(release("2.4.8", "+26.1.x"));
        return new Source(first, second);
    }

    // ---- der gemeldete Fehler

    @Test
    void anOlderBranchStillFindsItsNewestFileBehindManyNewerReleasesOfAnotherBranch() throws Exception {
        ModUpdater.Found found = ModUpdater.scan(githubOn28November(), "+26.2");

        assertNotNull(found.mine(), "die Datei fuer 26.2 steht auf Seite 2");
        assertEquals("2.4.9+26.2", found.mine().version());
        assertEquals("shokimod-2.4.9+26.2.jar", found.mine().fileName());
        assertEquals("https://example.invalid/shokimod-2.4.9+26.2.jar", found.mine().url());
    }

    @Test
    void theSameHoldsFor261x() throws Exception {
        ModUpdater.Found found = ModUpdater.scan(githubOn28November(), "+26.1.x");

        assertEquals("2.4.9+26.1.x", found.mine().version());
    }

    @Test
    void theNewestBranchIsFoundOnTheFirstPageAndNothingMoreIsRead() throws Exception {
        Source source = githubOn28November();

        ModUpdater.Found found = ModUpdater.scan(source, "+26.3");

        assertEquals("2.6.99+26.3", found.mine().version(), "die neueste Nummer der ersten Seite");
        assertEquals(List.of(1), source.asked, "eine Anfrage reicht");
    }

    @Test
    void theSearchStopsAtTheFirstPageWithAHit() throws Exception {
        Source source = githubOn28November();
        // Eine dritte Seite gibt es nicht zu lesen: Seite 2 hat den Treffer
        ModUpdater.scan(source, "+26.2");

        assertEquals(List.of(1, 2), source.asked);
    }

    // ---- die Meldung, warum es nichts Neues gibt

    @Test
    void whoIsOnTheNewestFileOfAnOldBranchIsToldThatNewerOnesExistElsewhere() throws Exception {
        ModUpdater.Found found = ModUpdater.scan(githubOn28November(), "+26.2");

        assertEquals("2.6.99+26.3", found.overall().version());
        assertTrue(found.newerOnlyElsewhere("2.4.9+26.2"));
    }

    @Test
    void whoCanStillUpdateGetsNoSuchNote() throws Exception {
        ModUpdater.Found found = ModUpdater.scan(githubOn28November(), "+26.2");

        // Wer auf 2.4.8+26.2 ist, bekommt 2.4.9 - da gibt es etwas zum Holen
        assertFalse(found.newerOnlyElsewhere("2.4.8+26.2"));
    }

    @Test
    void whoIsOnTheNewestOfTheNewestBranchGetsNoSuchNote() throws Exception {
        ModUpdater.Found found = ModUpdater.scan(githubOn28November(), "+26.3");

        assertFalse(found.newerOnlyElsewhere("2.6.99+26.3"));
    }

    // ---- Grenzen der Suche

    @Test
    void noFileForTheBranchAnywhereGivesNothingAndEndsAtTheShortPage() throws Exception {
        JsonArray only = new JsonArray();
        only.add(release("2.4.9", "+26.3"));
        Source source = new Source(only);

        ModUpdater.Found found = ModUpdater.scan(source, "+26.2");

        assertNull(found.mine());
        assertEquals("2.4.9+26.3", found.overall().version());
        assertEquals(List.of(1), source.asked, "eine kurze Seite ist die letzte");
    }

    @Test
    void anEmptyListGivesNothing() throws Exception {
        Source source = new Source(new JsonArray());

        ModUpdater.Found found = ModUpdater.scan(source, "+26.2");

        assertNull(found.mine());
        assertNull(found.overall());
        assertFalse(found.newerOnlyElsewhere("2.4.9+26.2"));
    }

    @Test
    void theSearchNeverReadsMoreThanTheLimit() throws Exception {
        // Lauter volle Seiten ohne Treffer: ohne Grenze liefe das endlos
        ModUpdater.Pages endless = number -> fullPage(6);
        List<Integer> asked = new ArrayList<>();

        ModUpdater.Found found = ModUpdater.scan(number -> {
            asked.add(number);
            return endless.page(number);
        }, "+26.2");

        assertNull(found.mine());
        assertEquals(ModUpdater.MAX_PAGES, asked.size());
    }

    @Test
    void aFailedPageStopsTheSearchWithItsMessage() {
        IOException broken = assertThrows(IOException.class, () -> ModUpdater.scan(number -> {
            throw new IOException("GitHub answered 403 - try again later.");
        }, "+26.2"));

        assertEquals("GitHub answered 403 - try again later.", broken.getMessage());
    }

    // ---- Auswahl innerhalb einer Seite (galt schon vorher)

    @Test
    void draftsAndSourceJarsAreNeverPicked() {
        JsonArray page = new JsonArray();
        JsonObject draft = release("2.9.0", "+26.2");
        draft.addProperty("draft", true);
        page.add(draft);
        page.add(release("2.4.9", "+26.2"));

        ModUpdater.Release picked = ModUpdater.pick(page, "+26.2");

        assertEquals("2.4.9+26.2", picked.version(), "der Entwurf zaehlt nicht");
        assertEquals("shokimod-2.4.9+26.2.jar", picked.fileName(), "nicht die Quell-Jar");
    }

    @Test
    void theBranchSuffixMustMatchExactly() {
        JsonArray page = new JsonArray();
        page.add(release("2.4.9", "+26.1.x"));

        assertNull(ModUpdater.pick(page, "+26.2"));
        assertNotNull(ModUpdater.pick(page, "+26.1.x"));
    }

    // ---- Versionen vergleichen

    @Test
    void versionsAreComparedByNumberNotByText() {
        assertTrue(ModUpdater.newer("2.4.10+26.3", "2.4.9+26.2"), "10 ist mehr als 9");
        assertTrue(ModUpdater.newer("2.10.0", "2.9.0"));
        assertTrue(ModUpdater.newer("3.0.0", "2.99.99"));
        assertFalse(ModUpdater.newer("2.4.9+26.3", "2.4.9+26.2"), "gleiche Nummer, anderer Zweig: nicht neuer");
        assertFalse(ModUpdater.newer("2.4.8", "2.4.9"));
    }

    @Test
    void theBranchIsTheTextFromThePlus() {
        assertEquals("+26.1.x", ModUpdater.branch("2.4.9+26.1.x"));
        assertEquals("+26.2", ModUpdater.branch("2.4.9+26.2"));
        assertEquals("", ModUpdater.branch("2.4.9"));
    }
}
