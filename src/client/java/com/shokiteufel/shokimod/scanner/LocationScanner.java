package com.shokiteufel.shokimod.scanner;

import com.mojang.logging.LogUtils;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.paket.PaketBus;
import com.shokiteufel.shokimod.paket.zustand.PaketZustaende;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import org.slf4j.Logger;

import java.util.List;

/**
 * Wo wir gerade sind: Gebiet (Map), Server-ID und Spielart, die Quelle fuer {@code GameState.Server}, auf die sich alles stuetzt, was von
 * der Map abhaengt (Anzeige- und Alarmregeln je Gebiet, Tracker-Pausen, Marker).
 *
 * <p>Quelle sind die Pakete: Hypixel setzt "Area: ..." und "Server: ..." als Anzeigenamen unsichtbarer Tab-Eintraege und schickt den
 * Titel der Seitenleiste ("SKYBLOCK"); der Paket-Zustand ({@link PaketZustaende}) meldet jede Aenderung, und der Standort steht im selben
 * Tick, in dem das Paket ankommt. Frueher wurde die Tab-Liste jeden Tick abgetastet (jede Zeile in Text umgewandelt, sortiert, verglichen).
 *
 * <p>Die Abtastung bleibt als Rueckfall: solange noch kein Paket einen Standort geliefert hat, laeuft sie wie bisher, und sie springt
 * wieder an, wenn die Paket-Schicht ausgeschaltet ist oder der Bus wegen Fehlern abgeschaltet wurde. So bleibt der Standort nie leer,
 * weil eine Seite ausfaellt.
 */
public class LocationScanner {
    private static final Logger LOG = LogUtils.getLogger();

    /** Kam schon ein Standort aus den Tab-Paketen? Erst dann verzichtet die Abtastung der Tab-Liste auf ihn. */
    private static volatile boolean tabAusPaketen;
    /** Kam schon die Spielart aus den Seitenleisten-Paketen? Erst dann verzichtet die Abtastung der Seitenleiste auf sie. */
    private static volatile boolean titelAusPaketen;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LocationScanner::scanSidebar);
        anmelden();
    }

    /** Haengt sich an die Spiegel der Paket-Schicht. Laeuft sie nicht, kommt nie ein Ereignis und die Abtastung bleibt in Betrieb. */
    public static void anmelden() {
        try {
            PaketZustaende.tabListe().hoeren((id, name, alt, neu) -> anzeigenameGeaendert(neu));
            PaketZustaende.seitenleiste().hoeren(aenderung -> titelGeaendert(aenderung.titelNeu()));
            PaketZustaende.weltHoeren((weltart, zwischenwelt, neueWelt, kopf) -> weltGewechselt(neueWelt));
        } catch (Throwable t) {
            LOG.warn("[Standort] Anmeldung an die Paket-Schicht scheiterte, der Standort wird abgetastet", t);
        }
    }

    /** Lebt die Paket-Schicht? Sonst muss jemand abtasten. */
    private static boolean paketeLaufen() {
        return PaketZustaende.laeuft() && !PaketBus.ausgeschaltet();
    }

    /** Liefern Pakete den Standort aus der Tab-Liste? Dann ruft die Abtastung {@link #processTabList} nicht mehr auf. */
    public static boolean tabAusPaketen() {
        return tabAusPaketen && paketeLaufen();
    }

    /**
     * Der Server hat den Spieler in eine Welt gesetzt. Legt das Spiel dabei eine neue Welt an, kann der alte Standort nicht mehr stimmen:
     * bis ein Paket den neuen liefert, tastet die Abtastung wieder ab. So bleibt der Standort auch dann nicht stehen, wenn Hypixel in
     * einer Welt die Zeilen anders liefert als erwartet.
     */
    static void weltGewechselt(boolean neueWelt) {
        if (neueWelt) {
            tabAusPaketen = false;
            titelAusPaketen = false;
        }
    }

    /** Nur fuer Tests: hat ein Tab-Paket den Standort schon geliefert (ohne Pruefung, ob die Paket-Schicht laeuft)? */
    static boolean tabPaketGesehen() {
        return tabAusPaketen;
    }

    /** Nur fuer Tests: hat ein Seitenleisten-Paket die Spielart schon geliefert? */
    static boolean titelPaketGesehen() {
        return titelAusPaketen;
    }

    /** Ein Anzeigename der Tab-Liste hat sich geaendert (Paket). Nur "Area: ..." und "Server: ..." sagen etwas. */
    static void anzeigenameGeaendert(String neu) {
        String gebiet = Standort.gebiet(neu);
        String server = Standort.serverId(neu);
        if (gebiet == null && server == null) {
            return;
        }
        tabAusPaketen = true;
        if (gebiet != null && !gebiet.equals(GameState.Server.map)) {
            LOG.info("[Standort] Gebiet '{}' (aus Paketen)", gebiet);
        }
        if (server != null && !server.equals(GameState.Server.id)) {
            LOG.info("[Standort] Server '{}' (aus Paketen)", server);
        }
        uebernehmen(gebiet, server);
    }

    /** Der Titel der Seitenleiste ist da oder hat sich geaendert (Paket). */
    static void titelGeaendert(String titel) {
        String art = Standort.spielart(titel);
        // Ohne Seitenleiste (kurz nach einem Weltwechsel) bleibt der letzte Wert, sonst flackerte die Anzeige
        if (art == null) {
            return;
        }
        titelAusPaketen = true;
        GameState.Server.gametype = art;
    }

    // Rueckfall: der Titel der Seitenleiste, jeden Tick gelesen - nur solange keine Pakete ihn liefern
    private static void scanSidebar(Minecraft client) {
        if (client.level == null || client.player == null) return;
        // Ist alles abgeschaltet, muss auch niemand wissen, wo wir gerade sind
        if (!FeatureGate.location()) return;
        if (titelAusPaketen && paketeLaufen()) return;

        String title = ScoreboardUtils.stripColor(ScoreboardUtils.getSidebarTitle(client)).trim();
        // Direkt nach dem Weltwechsel gibt es die Seitenleiste noch nicht. Wuerde man hier auf Unknown fallen,
        // verschwaende das HUD kurz; was nicht lesbar war, laesst den letzten Wert stehen
        String art = Standort.spielart(title);
        if (art != null) {
            GameState.Server.gametype = art;
        }
    }

    // Rueckfall: Gebiet und Server-ID aus den Zeilen der Tab-Liste (TabListScanner ruft das nur bei einer Aenderung der Liste auf)
    public static void processTabList(List<String> unformattedLines) {
        Standort.Treffer treffer = Standort.ausZeilen(unformattedLines);
        // Fehlt eine der Zeilen (gerade beim Wechsel), wird nichts ueberschrieben: ein leerer Wert liesse fuer ein paar Ticks
        // alle Funktionen "ausserhalb" sein und die Anzeige flackern
        uebernehmen(treffer.gebiet(), treffer.serverId());
    }

    /** Was ein neu gelesenes Gebiet nach sich zieht ({@code vorher} ist das Gebiet davor). Austauschbar, damit Tests ohne Konfiguration laufen. */
    @FunctionalInterface
    interface Folgen {
        void gebiet(String vorher, String jetzt);
    }

    private static Folgen folgen = LocationScanner::standardFolgen;

    /** Nur fuer Tests: ersetzt die Folgen eines Gebietswechsels; null stellt die echten wieder her. */
    static void folgenFuerTests(Folgen f) {
        folgen = f != null ? f : LocationScanner::standardFolgen;
    }

    private static void standardFolgen(String before, String area) {
        // Neues Gebiet: alle Uhren halten an, bis wieder geangelt wird oder etwas hereinkommt
        if (before != null && !before.equals(area) && !"Unknown".equals(before)) {
            com.shokiteufel.shokimod.handler.ProfitTracker.pauseNow();
            com.shokiteufel.shokimod.handler.HuntingTracker.pauseNow();
            com.shokiteufel.shokimod.handler.CollectionTracker.pauseNow();
            com.shokiteufel.shokimod.handler.SeaCreatureTracker.pauseNow();
        }
        // Unbekannte Gebiete in die Auswahlliste der Chatregeln aufnehmen
        com.shokiteufel.shokimod.data.KnownAreas.observe(area);
    }

    private static void uebernehmen(String area, String serverId) {
        if (area != null && !area.isEmpty()) {
            String before = GameState.Server.map;
            GameState.Server.map = area;
            folgen.gebiet(before, area);
        }
        if (serverId != null && !serverId.isEmpty()) applyServerId(serverId);
    }

    // Wechselt der Server, ist, was auf dem vorigen zusammengetragen wurde, hinfaellig
    private static void applyServerId(String newId) {
        if (newId.equals(GameState.Server.id)) return;

        GameState.Server.id = newId;
        // Auch der Fangfortschritt der Critter gehoert zur Safari-Instanz und wird nicht mitgenommen
    }
}
