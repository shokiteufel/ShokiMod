package com.shokiteufel.shokimod.paket.zustand;

import com.shokiteufel.shokimod.paket.PaketAbgriff;
import com.shokiteufel.shokimod.paket.PaketTestHilfe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Anmeldung der Spiegel am Bus mit echten Paket-Objekten: Pakete gehen durch den Abgriff, am Ende steht der
 * nachgebaute Zustand. So wie sie Hypixel schickt: Ziel, Anzeige, Teams mit dem Text, Punkte mit unsichtbaren Namen.
 */
class PaketZustaendeTest {
    private final Scoreboard bruecke = new Scoreboard();

    /** Einige Paketklassen (zum Beispiel der Punktestand) greifen beim Laden auf Minecrafts Register zu. */
    @BeforeAll
    static void minecraftStarten() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void vorbereiten() {
        zuruecksetzen();
        PaketZustaende.anmelden();
    }

    @AfterEach
    void zuruecksetzen() {
        PaketTestHilfe.zuruecksetzen();
        PaketZustaende.zuruecksetzenFuerTests();
    }

    private static void ein(Packet<?> paket) {
        PaketAbgriff.vor(paket, true);
        PaketAbgriff.nach(paket);
    }

    private Objective ziel(String name, String titel) {
        return bruecke.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(titel), ObjectiveCriteria.RenderType.INTEGER, false, null);
    }

    private PlayerTeam team(String name, String praefix, String suffix, String mitglied) {
        PlayerTeam team = bruecke.addPlayerTeam(name);
        team.setPlayerPrefix(Component.literal(praefix));
        team.setPlayerSuffix(Component.literal(suffix));
        bruecke.addPlayerToTeam(mitglied, team);
        return team;
    }

    @Test
    void buildsTheSidebarFromRealPacketsInHypixelsOrder() {
        Objective ziel = ziel("SBScoreboard", "§e§lSKYBLOCK");
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel));
        PlayerTeam geld = team("t1", "§7Purse: §6", "37,824,435", "§a");
        PlayerTeam adresse = team("t2", "§ewww.hypixel.net", "", "§b");
        ein(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(geld, true));
        ein(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(adresse, true));
        ein(new ClientboundSetScorePacket("§a", "SBScoreboard", 2, Optional.empty(), Optional.empty()));
        ein(new ClientboundSetScorePacket("§b", "SBScoreboard", 1, Optional.empty(), Optional.empty()));

        assertEquals("SKYBLOCK", PaketZustaende.seitenleiste().titel());
        assertEquals(List.of("Purse: 37,824,435", "www.hypixel.net"), PaketZustaende.seitenleiste().zeilen());
    }

    @Test
    void aLaterTeamUpdateChangesTheLineAndIsReportedOnce() {
        Objective ziel = ziel("SBScoreboard", "SKYBLOCK");
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel));
        PlayerTeam uhr = team("t1", "5:50pm", "", "§a");
        ein(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(uhr, true));
        ein(new ClientboundSetScorePacket("§a", "SBScoreboard", 1, Optional.empty(), Optional.empty()));
        List<SeitenleisteZustand.Aenderung> aenderungen = new ArrayList<>();
        PaketZustaende.seitenleiste().hoeren(aenderungen::add);

        uhr.setPlayerPrefix(Component.literal("6:00pm"));
        ein(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(uhr, false));

        assertEquals(List.of("6:00pm"), PaketZustaende.seitenleiste().zeilen());
        assertEquals(1, aenderungen.size());
        assertEquals(List.of(0), aenderungen.get(0).geaenderteZeilen());
    }

    @Test
    void resetScoreRemovesALine() {
        Objective ziel = ziel("SBScoreboard", "SKYBLOCK");
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel));
        ein(new ClientboundSetScorePacket("a", "SBScoreboard", 2, Optional.empty(), Optional.empty()));
        ein(new ClientboundSetScorePacket("b", "SBScoreboard", 1, Optional.empty(), Optional.empty()));

        ein(new ClientboundResetScorePacket("a", "SBScoreboard"));

        assertEquals(List.of("b"), PaketZustaende.seitenleiste().zeilen());
    }

    @Test
    void otherDisplaySlotsAreIgnored() {
        Objective ziel = ziel("Liste", "Liste");
        ein(new ClientboundSetObjectivePacket(ziel, 0));
        ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.LIST, ziel));
        ein(new ClientboundSetScorePacket("a", "Liste", 1, Optional.empty(), Optional.empty()));

        assertEquals(List.of(), PaketZustaende.seitenleiste().zeilen());
    }

    @Test
    void splitsChatFromTheActionBarByTheOverlayFlagAndStripsColourCodes() {
        List<String> chat = new ArrayList<>();
        List<String> leiste = new ArrayList<>();
        PaketZustaende.chatHoeren((text, inhalt, kopf) -> chat.add(text));
        PaketZustaende.aktionsleisteHoeren((text, inhalt, kopf) -> leiste.add(text));

        ein(new ClientboundSystemChatPacket(Component.literal("§aYou have §b24 §aunclaimed rewards!"), false));
        ein(new ClientboundSystemChatPacket(Component.literal("§c1,817/1,442 §a282 Defense"), true));

        assertEquals(List.of("You have 24 unclaimed rewards!"), chat);
        assertEquals(List.of("1,817/1,442 282 Defense"), leiste);
    }

    @Test
    void chatWithoutAnyListenerIsHarmless() {
        assertDoesNotThrow(() -> {
            ein(new ClientboundSystemChatPacket(Component.literal("egal"), false));
            ein(new ClientboundSystemChatPacket(Component.literal("egal"), true));
        });
    }

    @Test
    void aFailingListenerNeverBreaksTheMirrorsOrOtherListeners() {
        List<String> gesund = new ArrayList<>();
        PaketZustaende.chatHoeren((text, inhalt, kopf) -> {
            throw new IllegalStateException("absichtlich");
        });
        PaketZustaende.chatHoeren((text, inhalt, kopf) -> gesund.add(text));

        assertDoesNotThrow(() -> ein(new ClientboundSystemChatPacket(Component.literal("Hallo"), false)));

        assertEquals(List.of("Hallo"), gesund, "der Hoerer eines anderen Features wird trotzdem bedient");
        ein(new ClientboundTabListPacket(Component.literal("K"), Component.literal("F")));
        assertEquals("K", PaketZustaende.tabListe().kopf(), "die Spiegel laufen weiter");
    }

    @Test
    void aFailingSidebarListenerDoesNotKeepOthersFromTheChange() {
        List<String> gesund = new ArrayList<>();
        PaketZustaende.seitenleiste().hoeren(a -> {
            throw new IllegalStateException("absichtlich");
        });
        PaketZustaende.seitenleiste().hoeren(a -> gesund.add(a.titelNeu()));
        Objective ziel = ziel("SBScoreboard", "SKYBLOCK");
        ein(new ClientboundSetObjectivePacket(ziel, 0));

        assertDoesNotThrow(() -> ein(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ziel)));

        assertEquals(List.of("SKYBLOCK"), gesund);
        assertEquals("SKYBLOCK", PaketZustaende.seitenleiste().titel());
    }

    @Test
    void readsTheTabHeaderAndFooter() {
        ein(new ClientboundTabListPacket(Component.literal("§aYou are playing on MC.HYPIXEL.NET"), Component.literal("Ranks & MORE!")));

        assertEquals("You are playing on MC.HYPIXEL.NET", PaketZustaende.tabListe().kopf());
        assertEquals("Ranks & MORE!", PaketZustaende.tabListe().fuss());
    }
}
