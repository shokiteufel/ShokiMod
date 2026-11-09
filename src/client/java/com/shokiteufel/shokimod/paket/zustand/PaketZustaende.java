package com.shokiteufel.shokimod.paket.zustand;

import com.shokiteufel.shokimod.paket.PaketBus;
import com.shokiteufel.shokimod.paket.PaketKopf;
import com.shokiteufel.shokimod.paket.PaketPhase;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;

/**
 * Das Fundament fuer Funktionen aus Paketen: baut aus dem Strom des {@link PaketBus} den Zustand nach, den ShokiMod heute
 * durch Abtasten holt (Seitenleiste, Tab-Liste, Teams), und liefert Chat, Aktionsleiste und Weltwechsel als Ereignisse.
 *
 * <p>Alle Spiegel haengen an der Phase {@code VOR}: Sie bilden den Zustand <i>einschliesslich</i> des gerade eintreffenden
 * Pakets ab, unabhaengig vom Spielzustand. Der Zustand wird zurueckgesetzt, wo auch Vanilla seine Welt neu anlegt
 * (Login, Respawn in eine andere Dimension, Trennen).
 *
 * <p>Nur auf dem Haupt-Thread benutzen. Fehler eines Hoerers fangen der Bus beziehungsweise die Aufrufe hier ab, sie
 * stoeren weder das Spiel noch andere Hoerer.
 */
public final class PaketZustaende {

    @FunctionalInterface
    public interface ChatHoerer {
        /** Eine Chatzeile ({@code klartext} ohne Farbcodes, {@code inhalt} mit Stil). */
        void bei(String klartext, Component inhalt, PaketKopf kopf);
    }

    @FunctionalInterface
    public interface WeltHoerer {
        /**
         * Der Server hat den Spieler in eine Welt gesetzt.
         *
         * @param weltart     zum Beispiel {@code hypixel:dimension_switch} oder {@code minecraft:overworld}
         * @param zwischenwelt ob es Hypixels Zwischenwelt beim Serverwechsel ist
         * @param neueWelt    ob das Spiel dabei eine neue Welt anlegt (alle Spiegel werden dann geleert)
         */
        void bei(String weltart, boolean zwischenwelt, boolean neueWelt, PaketKopf kopf);
    }

    public static final String ZWISCHENWELT = "hypixel:dimension_switch";

    private static final TeamZustand TEAMS = new TeamZustand();
    private static final SeitenleisteZustand SEITENLEISTE = new SeitenleisteZustand(TEAMS);
    private static final TabListeZustand TAB_LISTE = new TabListeZustand(TEAMS);
    private static final List<ChatHoerer> CHAT = new ArrayList<>();
    private static final List<ChatHoerer> AKTIONSLEISTE = new ArrayList<>();
    private static final List<WeltHoerer> WELT = new ArrayList<>();
    private static String letzteWelt;
    private static boolean initialisiert;

    private PaketZustaende() {
    }

    public static TeamZustand teams() {
        return TEAMS;
    }

    public static SeitenleisteZustand seitenleiste() {
        return SEITENLEISTE;
    }

    public static TabListeZustand tabListe() {
        return TAB_LISTE;
    }

    public static void chatHoeren(ChatHoerer h) {
        CHAT.add(h);
    }

    /** Die Aktionsleiste (Systemnachricht mit Overlay-Flag), bei Hypixel SkyBlock etwa alle 500 ms. */
    public static void aktionsleisteHoeren(ChatHoerer h) {
        AKTIONSLEISTE.add(h);
    }

    public static void weltHoeren(WeltHoerer h) {
        WELT.add(h);
    }

    /**
     * Laufen die Spiegel (init ist gelaufen, nicht mit {@code -Dshokimod.pakete.zustand=aus} abgeschaltet)? Wer sich auf sie stuetzt,
     * muss sonst selbst abtasten.
     */
    public static boolean laeuft() {
        return initialisiert;
    }

    /**
     * Einmal beim Start. Die Spiegel muessen von Beginn der Verbindung an mitlaufen, sonst fehlt ihnen der Anfang; sie kosten
     * je Paket nur ein paar Tabellenzugriffe. Mit {@code -Dshokimod.pakete.zustand=aus} bleiben sie ganz aus.
     */
    public static void init() {
        if (initialisiert || "aus".equalsIgnoreCase(System.getProperty("shokimod.pakete.zustand"))) {
            return;
        }
        initialisiert = true;
        PaketBus.init();
        anmelden();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            alleLeeren();
            letzteWelt = null;
        });
    }

    /** Meldet die Spiegel am Bus an (ohne Fabric-Ereignisse; auch fuer Tests). */
    static void anmelden() {
        PaketBus.abonnieren(ClientboundSystemChatPacket.class, PaketPhase.VOR, "Zustand Chat", PaketZustaende::chat);
        PaketBus.abonnieren(ClientboundSetDisplayObjectivePacket.class, PaketPhase.VOR, "Zustand Seitenleiste", (p, k) -> {
            if (PaketAnsicht.istSeitenleiste(p)) {
                SEITENLEISTE.anzeigeZiel(PaketAnsicht.anzeigeZielName(p));
            }
        });
        PaketBus.abonnieren(ClientboundSetObjectivePacket.class, PaketPhase.VOR, "Zustand Seitenleiste",
                (p, k) -> SEITENLEISTE.ziel(PaketAnsicht.zielName(p), Klartext.von(PaketAnsicht.zielTitel(p)), PaketAnsicht.zielMethode(p)));
        PaketBus.abonnieren(ClientboundSetScorePacket.class, PaketPhase.VOR, "Zustand Seitenleiste",
                (p, k) -> SEITENLEISTE.punkte(PaketAnsicht.punkteZiel(p), PaketAnsicht.punkteEintrag(p), PaketAnsicht.punkteWert(p),
                        PaketAnsicht.punkteText(p).map(Klartext::von).orElse(null)));
        PaketBus.abonnieren(ClientboundResetScorePacket.class, PaketPhase.VOR, "Zustand Seitenleiste",
                (p, k) -> SEITENLEISTE.punkteLoeschen(PaketAnsicht.loeschenEintrag(p), PaketAnsicht.loeschenZiel(p)));
        PaketBus.abonnieren(ClientboundSetPlayerTeamPacket.class, PaketPhase.VOR, "Zustand Teams", PaketZustaende::team);
        PaketBus.abonnieren(ClientboundPlayerInfoUpdatePacket.class, PaketPhase.VOR, "Zustand Tab-Liste", PaketZustaende::spielerInfo);
        PaketBus.abonnieren(ClientboundPlayerInfoRemovePacket.class, PaketPhase.VOR, "Zustand Tab-Liste", (p, k) -> {
            for (java.util.UUID id : PaketAnsicht.infoEntfernt(p)) {
                TAB_LISTE.entfernen(id);
            }
        });
        PaketBus.abonnieren(ClientboundTabListPacket.class, PaketPhase.VOR, "Zustand Tab-Liste",
                (p, k) -> TAB_LISTE.kopfUndFuss(PaketAnsicht.tabKopf(p), PaketAnsicht.tabFuss(p)));
        PaketBus.abonnieren(ClientboundLoginPacket.class, PaketPhase.VOR, "Zustand Welt", PaketZustaende::login);
        PaketBus.abonnieren(ClientboundRespawnPacket.class, PaketPhase.VOR, "Zustand Welt", PaketZustaende::respawn);
    }

    // ------------------------------------------------------------------ Verarbeiten

    private static void chat(ClientboundSystemChatPacket p, PaketKopf kopf) {
        List<ChatHoerer> hoerer = PaketAnsicht.aktionsleiste(p) ? AKTIONSLEISTE : CHAT;
        if (hoerer.isEmpty()) {
            return;
        }
        Component inhalt = PaketAnsicht.chatInhalt(p);
        String klartext = Klartext.von(inhalt);
        for (ChatHoerer h : hoerer) {
            try {
                h.bei(klartext, inhalt, kopf);
            } catch (Throwable t) {
                HoererFehler.melden("Chat", t);
            }
        }
    }

    private static void team(ClientboundSetPlayerTeamPacket p, PaketKopf kopf) {
        String name = PaketAnsicht.teamName(p);
        java.util.Set<String> betroffen = switch (PaketAnsicht.teamAktion(p)) {
            case ANLEGEN -> TEAMS.anlegen(name, PaketAnsicht.teamPraefix(p), PaketAnsicht.teamSuffix(p), PaketAnsicht.teamMitglieder(p));
            case ENTFERNEN -> TEAMS.entfernen(name);
            case AENDERN -> PaketAnsicht.teamHatParameter(p)
                    ? TEAMS.aendern(name, PaketAnsicht.teamPraefix(p), PaketAnsicht.teamSuffix(p)) : java.util.Set.of();
            case HINZU -> TEAMS.hinzu(name, PaketAnsicht.teamMitglieder(p));
            case WEG -> TEAMS.weg(name, PaketAnsicht.teamMitglieder(p));
        };
        if (!betroffen.isEmpty()) {
            SEITENLEISTE.teamGeaendert(betroffen);
            TAB_LISTE.teamsGeaendert();
        }
    }

    private static void spielerInfo(ClientboundPlayerInfoUpdatePacket p, PaketKopf kopf) {
        boolean neu = PaketAnsicht.hatAktion(p, ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        boolean gelistet = PaketAnsicht.hatAktion(p, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED);
        boolean modus = PaketAnsicht.hatAktion(p, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
        boolean ordnung = PaketAnsicht.hatAktion(p, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);
        boolean anzeige = PaketAnsicht.hatAktion(p, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        for (ClientboundPlayerInfoUpdatePacket.Entry e : PaketAnsicht.infoEintraege(p)) {
            java.util.UUID id = PaketAnsicht.infoId(e);
            if (neu) {
                TAB_LISTE.hinzufuegen(id, PaketAnsicht.infoName(e));
            }
            if (gelistet) {
                TAB_LISTE.gelistet(id, PaketAnsicht.infoGelistet(e));
            }
            if (modus) {
                TAB_LISTE.spielmodus(id, PaketAnsicht.infoZuschauer(e));
            }
            if (ordnung) {
                TAB_LISTE.ordnung(id, PaketAnsicht.infoOrdnung(e));
            }
            if (anzeige) {
                TAB_LISTE.anzeigename(id, PaketAnsicht.infoAnzeigename(e));
            }
        }
    }

    private static void login(ClientboundLoginPacket p, PaketKopf kopf) {
        alleLeeren();
        letzteWelt = PaketAnsicht.welt(p);
    }

    private static void respawn(ClientboundRespawnPacket p, PaketKopf kopf) {
        String welt = PaketAnsicht.welt(p);
        boolean neueWelt = !welt.equals(letzteWelt);
        if (neueWelt) {
            alleLeeren();
            letzteWelt = welt;
        }
        String art = PaketAnsicht.weltart(p);
        boolean zwischen = ZWISCHENWELT.equals(art);
        for (WeltHoerer h : WELT) {
            try {
                h.bei(art, zwischen, neueWelt, kopf);
            } catch (Throwable t) {
                HoererFehler.melden("Weltwechsel", t);
            }
        }
    }

    private static void alleLeeren() {
        SEITENLEISTE.leeren();
        TAB_LISTE.leeren();
        TEAMS.leeren();
    }

    /** Nur fuer Tests. */
    static void zuruecksetzenFuerTests() {
        alleLeeren();
        SEITENLEISTE.hoererLeeren();
        TAB_LISTE.hoererLeeren();
        CHAT.clear();
        AKTIONSLEISTE.clear();
        WELT.clear();
        letzteWelt = null;
    }
}
