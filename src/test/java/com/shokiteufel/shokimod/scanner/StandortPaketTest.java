package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.paket.PaketAbgriff;
import com.shokiteufel.shokimod.paket.PaketTestHilfe;
import com.shokiteufel.shokimod.paket.zustand.PaketZustaende;
import com.shokiteufel.shokimod.paket.zustand.ZustandTestHilfe;
import io.netty.buffer.Unpooled;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der ganze Weg mit echten Bytes: die Pakete aus dem Mitschnitt vom 08.11. (Tab-Liste: Anzeigename von "Area: ..." und "Server: ...",
 * so wie Hypixel sie schickt, mit mehreren Textteilen) gehen durch den Abgriff in den Tab-Zustand und von dort in den
 * {@link LocationScanner}; am Ende steht das Gebiet in {@code GameState.Server}. Die Folgen eines Gebietswechsels (Tracker, Konfiguration)
 * sind durch eine Aufzeichnung ersetzt.
 */
class StandortPaketTest {
    private final List<String> wechsel = new ArrayList<>();

    @BeforeAll
    static void minecraftStarten() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void vorbereiten() {
        PaketTestHilfe.zuruecksetzen();
        ZustandTestHilfe.zuruecksetzen();
        ZustandTestHilfe.anmelden();
        LocationScanner.anmelden();
        wechsel.clear();
        LocationScanner.folgenFuerTests((vorher, jetzt) -> wechsel.add(vorher + " -> " + jetzt));
        LocationScanner.weltGewechselt(true);
        GameState.Server.reset();
    }

    @AfterEach
    void aufraeumen() {
        LocationScanner.folgenFuerTests(null);
        ZustandTestHilfe.zuruecksetzen();
        PaketTestHilfe.zuruecksetzen();
        GameState.Server.reset();
    }

    private record Roh(char art, UUID id, byte[] bytes) {
    }

    private static List<List<Roh>> sitzungen() throws IOException {
        List<List<Roh>> aus = new ArrayList<>();
        List<Roh> aktuell = new ArrayList<>();
        try (InputStream in = StandortPaketTest.class.getResourceAsStream("/standort/schiggy-20261108-tab-roh.tsv");
             BufferedReader leser = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String zeile; (zeile = leser.readLine()) != null; ) {
                if (zeile.startsWith("#") || zeile.isBlank()) {
                    continue;
                }
                if (zeile.equals("---")) {
                    aus.add(aktuell);
                    aktuell = new ArrayList<>();
                    continue;
                }
                String[] f = zeile.split("\t", -1);
                aktuell.add(new Roh(f[0].charAt(0), UUID.fromString(f[1]), HexFormat.of().parseHex(f[2])));
            }
        }
        aus.add(aktuell);
        return aus;
    }

    /** Erwartete Texte in derselben Reihenfolge, aus der Klartext-Fassung desselben Mitschnitts. */
    private static List<List<String>> texte() throws IOException {
        List<List<String>> aus = new ArrayList<>();
        List<String> aktuell = new ArrayList<>();
        try (InputStream in = StandortPaketTest.class.getResourceAsStream("/standort/schiggy-20261108-tab.tsv");
             BufferedReader leser = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String zeile; (zeile = leser.readLine()) != null; ) {
                if (zeile.startsWith("#") || zeile.isBlank()) {
                    continue;
                }
                if (zeile.equals("---")) {
                    aus.add(aktuell);
                    aktuell = new ArrayList<>();
                    continue;
                }
                aktuell.add(zeile.split("\t", -1)[2]);
            }
        }
        aus.add(aktuell);
        return aus;
    }

    /** Das Paket so, wie der Client es aus den Bytes liest: Paketkennung weg, dann der Inhalt. */
    private static Packet<?> lesen(byte[] roh) {
        RegistryFriendlyByteBuf puffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(roh), RegistryAccess.EMPTY);
        puffer.readVarInt();
        Packet<?> paket = ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(puffer);
        assertEquals(0, puffer.readableBytes(), "das ganze Paket gelesen");
        return paket;
    }

    private static void ein(Packet<?> paket) {
        PaketAbgriff.vor(paket, true);
        PaketAbgriff.nach(paket);
    }

    @Test
    void realPacketsSetAreaAndServerInTheOrderTheyArrive() throws IOException {
        List<List<Roh>> sitzungen = sitzungen();
        List<List<String>> texte = texte();
        assertEquals(texte.size(), sitzungen.size());
        int geprueft = 0;
        for (int s = 0; s < sitzungen.size(); s++) {
            PaketZustaende.tabListe().leeren();
            GameState.Server.reset();
            wechsel.clear();
            List<Roh> pakete = sitzungen.get(s);
            assertEquals(texte.get(s).size(), pakete.size(), "gleich viele Pakete wie Textzeilen");
            Set<UUID> bekannt = new HashSet<>();
            List<String> erwarteteGebiete = new ArrayList<>();
            for (int i = 0; i < pakete.size(); i++) {
                Roh r = pakete.get(i);
                if (bekannt.add(r.id())) {
                    PaketZustaende.tabListe().hinzufuegen(r.id(), "widget-" + r.id());
                    PaketZustaende.tabListe().gelistet(r.id(), true);
                }
                ein(lesen(r.bytes()));
                String text = texte.get(s).get(i).trim();
                if (r.art() == 'A') {
                    String gebiet = text.substring("Area:".length()).trim();
                    assertEquals(gebiet, GameState.Server.map, "Gebiet nach Paket " + i);
                    if (erwarteteGebiete.isEmpty() || !erwarteteGebiete.get(erwarteteGebiete.size() - 1).equals(gebiet)) {
                        erwarteteGebiete.add(gebiet);
                    }
                } else {
                    assertEquals(text.substring("Server:".length()).trim(), GameState.Server.id, "Server nach Paket " + i);
                }
                geprueft++;
            }
            // Die Folgen eines Gebietswechsels kamen genau bei den echten Wechseln (Wiederholungen derselben Zeile aendern nichts)
            List<String> gemeldeteZiele = new ArrayList<>();
            for (String w : wechsel) {
                gemeldeteZiele.add(w.substring(w.indexOf(" -> ") + 4));
            }
            List<String> ziele = new ArrayList<>();
            for (String g : gemeldeteZiele) {
                if (ziele.isEmpty() || !ziele.get(ziele.size() - 1).equals(g)) {
                    ziele.add(g);
                }
            }
            assertEquals(erwarteteGebiete, ziele);
        }
        assertTrue(geprueft >= 50, "mindestens 50 echte Pakete geprueft: " + geprueft);
    }

    @Test
    void theSameLineTwiceChangesNothing() throws IOException {
        List<Roh> pakete = sitzungen().get(1);
        Roh gebiet = pakete.stream().filter(r -> r.art() == 'A').findFirst().orElseThrow();
        PaketZustaende.tabListe().hinzufuegen(gebiet.id(), "widget");
        PaketZustaende.tabListe().gelistet(gebiet.id(), true);
        ein(lesen(gebiet.bytes()));
        String erstes = GameState.Server.map;
        int folgenNachErstem = wechsel.size();
        ein(lesen(gebiet.bytes()));
        assertEquals(erstes, GameState.Server.map);
        assertEquals(folgenNachErstem, wechsel.size(), "kein zweiter Wechsel");
        assertFalse("Unknown".equals(GameState.Server.map));
    }

    @Test
    void aNewWorldMakesTheScanFillInUntilPacketsDeliverAgain() throws IOException {
        Roh gebiet = sitzungen().get(1).stream().filter(r -> r.art() == 'A').findFirst().orElseThrow();
        PaketZustaende.tabListe().hinzufuegen(gebiet.id(), "widget");
        PaketZustaende.tabListe().gelistet(gebiet.id(), true);
        assertFalse(LocationScanner.tabPaketGesehen(), "vor dem ersten Paket tastet die Abtastung ab");
        ein(lesen(gebiet.bytes()));
        assertTrue(LocationScanner.tabPaketGesehen(), "ein Paket hat den Standort geliefert");

        LocationScanner.weltGewechselt(false);   // ein Respawn in derselben Welt aendert nichts
        assertTrue(LocationScanner.tabPaketGesehen());
        LocationScanner.weltGewechselt(true);    // eine neue Welt: bis zum naechsten Paket wieder die Abtastung
        assertFalse(LocationScanner.tabPaketGesehen());
        assertFalse(LocationScanner.titelPaketGesehen());
    }

    @Test
    void theSidebarTitleAlsoCountsAsDeliveredByPackets() {
        Scoreboard bruecke = new Scoreboard();
        Objective ziel = bruecke.addObjective("SBScoreboard", ObjectiveCriteria.DUMMY, Component.literal("SKYBLOCK"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        assertFalse(LocationScanner.titelPaketGesehen());
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel));
        assertTrue(LocationScanner.titelPaketGesehen());
    }

    @Test
    void anUnknownEntryIsIgnored() throws IOException {
        // Ein Anzeigename fuer einen Eintrag, den der Zustand nicht kennt, aendert den Standort nicht
        Roh gebiet = sitzungen().get(1).stream().filter(r -> r.art() == 'A').findFirst().orElseThrow();
        ein(lesen(gebiet.bytes()));
        assertEquals("Unknown", GameState.Server.map);
    }

    @Test
    void theSidebarTitleSetsTheGameType() {
        Scoreboard bruecke = new Scoreboard();
        Objective ziel = bruecke.addObjective("SBScoreboard", ObjectiveCriteria.DUMMY, Component.literal("§e§lSKYBLOCK"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel));
        assertEquals("SKYBLOCK", GameState.Server.gametype);
        assertTrue(GameState.Server.isSkyblock());

        // Faellt die Seitenleiste weg (Weltwechsel), bleibt der letzte Wert stehen
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        assertEquals("SKYBLOCK", GameState.Server.gametype);
    }

    @Test
    void anotherGameIsNotSkyblock() {
        Scoreboard bruecke = new Scoreboard();
        Objective ziel = bruecke.addObjective("bw", ObjectiveCriteria.DUMMY, Component.literal("§e§lBED WARS"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel));
        assertEquals("Unknown", GameState.Server.gametype);
        assertFalse(GameState.Server.isSkyblock());
    }
}
