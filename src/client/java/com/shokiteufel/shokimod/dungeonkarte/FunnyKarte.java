package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.HidePlayers;
import com.shokiteufel.shokimod.paket.PaketBus;
import com.shokiteufel.shokimod.paket.PaketPhase;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.DungeonLog;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Funny Map: der ganze Dungeon auf einer Karte, ab den ersten Sekunden.
 *
 * Im Dungeon viermal je Sekunde: der {@link WeltScanner} liest aus den Chunks, die der Server schon geschickt hat, die Raeume und Tueren
 * ins {@link Raster} - bis alle Chunks da waren und nichts Neues mehr kam, dann ruht er (wie bei NoammAddons); die Karte, die Hypixel in den
 * Hotbar-Platz 9 legt, liefert den Zustand (entdeckt, geraeumt ...) und wird nur gelesen, wenn ein Kartenpaket kam ({@link KartenTakt}).
 * Gezeichnet wird in {@link FunnyKarteHud}. Es wird nichts angefordert und nichts gesendet: nur gelesen, was ohnehin da ist.
 *
 * Der Dungeon wird wie bei "Hide Players" erkannt (Tab-Liste "Dungeon: Catacombs", Seitenleiste "The Catacombs (F5)"); das Stockwerk
 * steht in der Klammer der Seitenleiste. Mit NoammAddons wird dessen Platz und Groesse gelesen (alle 30 Sekunden neu, die Datei ist klein).
 */
public final class FunnyKarte {

    /** Ausserhalb dieses Rechtecks (Bloecke) steht man nicht mehr im Raster, sondern im Boss-Raum oder sonst wo */
    private static final int BOSS_GRENZE_MIN = Raster.START - 40;
    private static final int BOSS_GRENZE_MAX = Raster.START + 10 * (Raster.RAUM / 2) + 40;

    private static final Raster RASTER = new Raster();
    private static WeltScanner scanner = new WeltScanner(RaumTabelle.standard(), RASTER);
    private static Object letzteWelt;
    private static boolean imDungeon;
    private static boolean imBoss;
    private static Integer stockwerk;
    private static KartenLeser.Kalibrierung kalibrierung;
    private static volatile NoammLage.Lage noamm;
    /** Die letzte {@link NoammLage#aenderung} (-1: noch nicht gelesen) */
    private static long noammStand = -1L;
    private static int ticks;
    private static String zuletztGeloggt = "";

    private FunnyKarte() {
    }

    /** Wann die Karte von Hypixel gelesen wird: wenn ein Kartenpaket kam, sonst nur zur Sicherheit alle paar Sekunden */
    private static final KartenTakt KARTENTAKT = new KartenTakt();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FunnyKarte::tick);
        try {
            // Nach dem Spiel: Dann liegen die neuen Farben schon in den Kartendaten
            PaketBus.abonnieren(ClientboundMapItemDataPacket.class, PaketPhase.NACH, "Funny Map Karte", (p, k) -> {
                if (FeatureGate.roomScan()) {
                    KARTENTAKT.paket();
                    DungeonLog.zaehlen(DungeonLog.Zaehler.KARTENPAKETE);
                }
            });
        } catch (Throwable t) {
            ShokiMod.LOGGER.warn("[FunnyMap] could not listen for map packets, the map is read on a timer instead: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ fuer die Anzeige

    public static Raster raster() {
        return RASTER;
    }

    /** Gibt es etwas zu zeichnen? */
    public static boolean sichtbar() {
        if (!FeatureGate.funnyMap() || !imDungeon || RASTER.leer()) return false;
        return !(imBoss && einstellungen().janein(KartenOption.HIDE_IN_BOSS));
    }

    /** Die Einstellungen von NoammAddons aus seiner Config (alle Werte der Dungeon Map), oder null */
    private static volatile KartenEinstellungen noammEinst;
    /** Die eigenen Einstellungen, einmal je halbe Sekunde aus den Feldern gelesen (Lesen ueber Felder ist nicht gratis, gezeichnet wird 60-mal je Sekunde) */
    private static KartenEinstellungen eigene;
    private static long eigeneVon;

    /**
     * Die Einstellungen, die gelten: mit "Use NoammAddons' place" und einer Karte in seiner Config seine, wie sie dort jetzt stehen (jede Sekunde
     * nachgesehen), sonst die eigenen der Funny Map.
     */
    public static KartenEinstellungen einstellungen() {
        // Im Abgleich stehen Noamms Werte schon in den eigenen Feldern; nur wenn er nicht zu erreichen ist, gilt seine Datei
        if (ModConfig.INSTANCE.ikohS.dungeon.funnyMap.followNoamm && !abgeglichen) {
            KartenEinstellungen n = noammEinst;
            if (n != null) return n;
        }
        long jetzt = System.nanoTime();
        if (eigene == null || jetzt - eigeneVon > 500_000_000L) {
            KartenEinstellungen neu = KartenEinstellungen.ausFeldern(ModConfig.INSTANCE.ikohS.dungeon.funnyMap);
            // Gleiche Werte: dasselbe Objekt behalten. Wer daraus Gerechnetes merkt (das Kartenbild), erkennt eine Aenderung an der Identitaet
            if (eigene == null || !eigene.gleich(neu)) eigene = neu;
            eigeneVon = jetzt;
        }
        return eigene;
    }

    /** Steht man im Boss-Raum (ausserhalb des Rasters)? Gilt nur im Dungeon und solange der Scan laeuft */
    public static boolean imBoss() {
        return imDungeon && imBoss;
    }

    /** Der Name des Raums, in dem diese Weltstelle liegt, oder null (nicht im Dungeon, Boss-Raum, ausserhalb, noch nicht erkannt) */
    public static String raumNameAn(double x, double z) {
        if (!imDungeon || imBoss) return null;
        int platz = SecretsLeiste.platz(x, z);
        if (platz < 0) return null;
        List<Raster.Raum> raeume = RASTER.raeume();
        for (int i = 0, n = raeume.size(); i < n; i++) {
            Raster.Raum raum = raeume.get(i);
            if (raum.plaetze().contains(platz)) return raum.daten().istUnbekannt() ? null : raum.daten().name();
        }
        return null;
    }

    /**
     * Die Plaetze des Raums, in dem diese Weltstelle liegt (fuer die Raum-Regel der Stern-Mobs), oder null, wenn der Raum nicht bekannt ist: nicht im
     * Dungeon, im Boss-Raum, ausserhalb des Rasters oder vom Scan noch nicht erkannt. Gerechnet wird mit den gemerkten Raeumen des Rasters.
     */
    public static java.util.List<Integer> raumPlaetzeAn(double x, double z) {
        if (!imDungeon || imBoss) return null;
        int platz = SecretsLeiste.platz(x, z);
        if (platz < 0) return null;
        List<Raster.Raum> raeume = RASTER.raeume();
        for (int i = 0, n = raeume.size(); i < n; i++) {
            List<Integer> plaetze = raeume.get(i).plaetze();
            if (plaetze.contains(platz)) return plaetze;
        }
        return null;
    }

    /** Wo das Raster in der Karte von Hypixel beginnt (Pixel), oder null, solange die Karte im Hotbar-Platz 9 noch nicht zu lesen war */
    public static KartenLeser.Kalibrierung kalibrierung() {
        return kalibrierung;
    }

    /**
     * Die Lage der Karte von NoammAddons, oder null, wenn er nicht da ist oder keine Karte in seiner Config steht. Im Abgleich stehen seine Werte
     * in den eigenen Feldern (dieselben Zahlen wie in seinem HUD-Editor, in seiner Referenzaufloesung).
     */
    public static NoammLage.Lage noamm() {
        if (abgeglichen) {
            ModConfig.FunnyMapCategory c = ModConfig.INSTANCE.ikohS.dungeon.funnyMap;
            // Dieselben Zahlen: dasselbe Objekt (die Karte fragt in jedem Bild, ein neues Objekt braucht es nur bei einer Aenderung)
            NoammLage.Lage l = abgeglicheneLage;
            if (l == null || l.x() != c.x || l.y() != c.y || l.scale() != c.scale || l.extraInfo() != c.extraLine) {
                l = new NoammLage.Lage(c.x, c.y, c.scale, c.extraLine);
                abgeglicheneLage = l;
            }
            return l;
        }
        return noamm;
    }

    private static NoammLage.Lage abgeglicheneLage;

    // ------------------------------------------------------------------ Abgleich mit Noamm (beide Richtungen)

    /** Lief der Abgleich mit der laufenden NoammAddons (nicht nur das Lesen seiner Datei)? */
    private static volatile boolean abgeglichen;
    private static KartenAbgleich.Stand letzterNoamm;
    private static KartenAbgleich.Stand letzterEigener;
    private static int abgleichTicks;

    private static KartenAbgleich.Stand eigenerStand(ModConfig.FunnyMapCategory c) {
        return new KartenAbgleich.Stand(KartenEinstellungen.ausFeldern(c), c.x, c.y, c.scale);
    }

    /**
     * Einmal je Sekunde: Die Karteneinstellungen von Noamm und die der Funny Map gleichen sich aus. Das gilt immer, wo man sie aendern kann, auch
     * ausserhalb eines Dungeons und mit ausgeschalteter Karte. Ohne die Bruecke zu seiner Anzeige bleibt es beim Lesen seiner Datei (nur in
     * einem Dungeon mit eingeschalteter Karte).
     */
    private static void abgleichTakt() {
        ModConfig.FunnyMapCategory c = ModConfig.INSTANCE.ikohS.dungeon.funnyMap;
        if (!c.followNoamm) {
            letzterNoamm = null;
            letzterEigener = null;
            abgeglichen = false;
            return;
        }
        KartenAbgleich.Stand noammJetzt = NoammBruecke.verfuegbar() ? NoammBruecke.lesen() : null;
        if (noammJetzt == null) {
            abgeglichen = false;
            if (FeatureGate.funnyMap()) noammPruefen();
            return;
        }
        KartenAbgleich.Stand eigenerJetzt = eigenerStand(c);
        switch (KartenAbgleich.entscheide(letzterNoamm, letzterEigener, noammJetzt, eigenerJetzt)) {
            case NOAMM_NACH_UNS -> {
                boolean geaendert = noammJetzt.einstellungen().inFelder(c);
                if (Math.abs(c.x - noammJetzt.x()) > 0.01f || Math.abs(c.y - noammJetzt.y()) > 0.01f || Math.abs(c.scale - noammJetzt.scale()) > 0.001f) {
                    c.x = noammJetzt.x();
                    c.y = noammJetzt.y();
                    c.scale = noammJetzt.scale();
                    geaendert = true;
                }
                letzterNoamm = noammJetzt;
                letzterEigener = eigenerStand(c);
                if (geaendert) {
                    ModConfig.INSTANCE.saveNow();
                    ShokiMod.LOGGER.info("[FunnyMap] map settings taken over from NoammAddons");
                }
            }
            case UNS_NACH_NOAMM -> {
                if (NoammBruecke.schreiben(eigenerJetzt)) {
                    KartenAbgleich.Stand nachher = NoammBruecke.lesen();
                    letzterNoamm = nachher != null ? nachher : noammJetzt;
                    letzterEigener = eigenerJetzt;
                    ShokiMod.LOGGER.info("[FunnyMap] map settings handed over to NoammAddons");
                }
            }
            default -> {
            }
        }
        abgeglichen = true;
    }

    // ------------------------------------------------------------------ Geheimnisse je Raum

    /** Wie viele Geheimnisse je Raum schon gefunden sind: Schluessel ist der kleinste Platz des Raums; nur steigende Werte zaehlen */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> gefunden = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Die Aktionsleiste im Dungeon nennt die Geheimnisse des Raums, in dem man steht ("0/1 Secrets"). Der Stand wird dem Raum zugeschrieben,
     * dessen Rasterplatz an der Stelle des Spielers liegt, und bleibt stehen, wenn man weitergeht.
     */
    public static void onActionBar(String leiste) {
        if (!imDungeon || imBoss) return;
        SecretsLeiste.Stand stand = SecretsLeiste.lesen(leiste);
        if (stand == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        int platz = SecretsLeiste.platz(client.player.getX(), client.player.getZ());
        if (platz < 0) return;
        for (Raster.Raum raum : RASTER.raeume()) {
            if (raum.plaetze().contains(platz)) {
                gefunden.merge(raum.plaetze().get(0), stand.gefunden(), Math::max);
                return;
            }
        }
    }

    /** Wie viele Geheimnisse dieses Raums schon gefunden sind, oder -1, wenn man noch nie darin stand */
    public static int gefundenIn(Raster.Raum raum) {
        Integer g = gefunden.get(raum.plaetze().get(0));
        return g == null ? -1 : g;
    }

    // ------------------------------------------------------------------ Takt

    private static void zuruecksetzen() {
        Mitspieler.zuruecksetzen();
        NoammLage.gemerktesVergessen();
        gefunden.clear();
        RASTER.leeren();
        scanner = new WeltScanner(RaumTabelle.standard(), RASTER);
        kalibrierung = null;
        imBoss = false;
        stockwerk = null;
        zuletztGeloggt = "";
    }

    private static void tick(Minecraft client) {
        // Der Abgleich mit Noamm laeuft nur mit eingeschalteter Karte (von Haus aus aus: dann tut die Funny Map nichts), dafuer ausserhalb von
        // SkyBlock und ausserhalb eines Dungeons, wo man ihre Einstellungen aendern kann
        if (client != null && client.level != null && FeatureGate.funnyMap() && ++abgleichTicks >= 20) {
            abgleichTicks = 0;
            try {
                abgleichTakt();
            } catch (RuntimeException e) {
                ShokiMod.LOGGER.warn("[FunnyMap] syncing with NoammAddons failed: {}", e.toString());
            }
        }
        if (!FeatureGate.roomScan()) {
            imDungeon = false;
            return;
        }
        if (client == null || client.level == null || client.player == null || !GameState.Server.isSkyblock()) {
            imDungeon = false;
            return;
        }
        // Eine neue Welt (neuer Lauf, andere Insel) fangt von vorn an
        if (client.level != letzteWelt) {
            letzteWelt = client.level;
            zuruecksetzen();
        }
        ticks++;
        if (ticks % 10 == 0) pruefeDungeon(client);
        if (!imDungeon) return;

        double x = client.player.getX();
        double z = client.player.getZ();
        imBoss = x < BOSS_GRENZE_MIN || x > BOSS_GRENZE_MAX || z < BOSS_GRENZE_MIN || z > BOSS_GRENZE_MAX;

        if (ticks % 5 == 0) arbeiten(client);
    }

    /** Lage der Noamm-Karte neu lesen, wenn seine Config seit dem letzten Mal geschrieben wurde; eine Zeile ins Log, wenn sich die Lage aendert */
    private static void noammPruefen() {
        java.nio.file.Path ordner = FabricLoader.getInstance().getConfigDir();
        long stand = NoammLage.aenderungGemerkt(ordner);
        if (stand == noammStand) return;
        noammStand = stand;
        NoammLage.Lage neu = NoammLage.lesen(ordner);
        if (!java.util.Objects.equals(neu, noamm)) {
            ShokiMod.LOGGER.info("[FunnyMap] NoammAddons' map place: {}", neu == null
                    ? "not found (no NoammAddons config with a 'Dungeon Map' entry) - the place of this map counts" : neu);
        }
        noamm = neu;
        // Auch seine Einstellungen der Karte (Farben, Texte, Spieler ...) gelten, wie sie bei ihm gerade stehen
        KartenEinstellungen einst = NoammLage.einstellungenLesen(ordner);
        noammEinst = einst;
        ShokiMod.LOGGER.info("[FunnyMap] NoammAddons' map settings: {}", einst == null ? "not found - the settings of this map count" : "taken over");
    }

    private static void pruefeDungeon(Minecraft client) {
        List<String> seite = ScoreboardUtils.getSidebarLines(client);
        boolean war = imDungeon;
        imDungeon = HidePlayers.detectDungeon(TabListScanner.lastLines(), seite);
        if (!imDungeon) {
            if (war) zuruecksetzen();
            return;
        }
        for (String zeile : seite) {
            Integer f = Stockwerk.aus(ScoreboardUtils.stripColor(zeile));
            if (f != null) {
                stockwerk = f;
                break;
            }
        }
    }

    private static int letzteFragen;
    private static int letzterStand;

    private static void arbeiten(Minecraft client) {
        // Das Dungeon log zaehlt, wie oft der Scan laeuft und fragt und wie oft sich das Raster aendert, und misst die Arbeit (ohne Log: nichts davon)
        long messung = DungeonLog.beginn();
        ClientWelt welt = new ClientWelt(client.level);
        boolean neu = scanner.durchgang(welt) > 0;
        scanner.tuerenPruefen(welt);
        boolean gelesen = KARTENTAKT.lesen(PaketBus.ausgeschaltet());
        if (gelesen) karteLesen(client);
        if (neu || gelesen || ticks % 100 == 0) protokollieren();
        if (DungeonLog.aktiv()) {
            DungeonLog.zaehlen(DungeonLog.Zaehler.SCAN_DURCHGAENGE);
            DungeonLog.zaehlen(DungeonLog.Zaehler.SCAN_WELTFRAGEN, scanner.weltFragen() - letzteFragen);
            DungeonLog.zaehlen(DungeonLog.Zaehler.RASTER_AENDERUNGEN, RASTER.stand() - letzterStand);
        }
        letzteFragen = scanner.weltFragen();
        letzterStand = RASTER.stand();
        DungeonLog.ende(DungeonLog.Zeit.KARTE_ARBEITEN, messung);
    }

    /** Der Zustand der Hypixel-Karte kommt ins Raster, sobald die Karte im Hotbar-Platz 9 liegt und kalibriert werden kann */
    private static void karteLesen(Minecraft client) {
        DungeonLog.zaehlen(DungeonLog.Zaehler.KARTE_GELESEN);
        ItemStack karte = client.player.getInventory().getItem(8);
        MapId id = karte.get(DataComponents.MAP_ID);
        if (id == null) return;
        MapItemSavedData daten = client.level.getMapData(id);
        if (daten == null) return;
        if (kalibrierung == null) kalibrierung = KartenLeser.kalibriere(daten.colors, stockwerk);
        if (kalibrierung == null) return;
        RASTER.vonKarte(KartenLeser.lesen(daten.colors, kalibrierung));
        mitspielerLesen(client, daten);
    }

    /**
     * Die Mitspieler, auch die weit weg: Hypixel legt alle als Markierungen in die Karte. Die Tab-Liste sagt, wer im Dungeon ist und wer tot ist; die
     * Markierung "player-N" gehoert dem N-ten lebenden Mitspieler (siehe {@link Mitspieler}). Gelesen wird nur, wenn die Karte gelesen wurde.
     */
    private static void mitspielerLesen(Minecraft client, MapItemSavedData daten) {
        Mitspieler.tabLesen(TabListScanner.lastLines(), client.getUser() == null ? null : client.getUser().getName());
        Map<String, Mitspieler.Marke> marken = new LinkedHashMap<>();
        for (Map.Entry<String, MapDecoration> e : daten.decorations.entrySet()) {
            MapDecoration d = e.getValue();
            boolean rahmen = d.type().value() == MapDecorationTypes.FRAME.value();
            marken.put(e.getKey(), new Mitspieler.Marke(rahmen, d.x(), d.y(), d.rot()));
        }
        Mitspieler.markenLesen(marken, kalibrierung, System.nanoTime());
    }

    /**
     * Eine Zeile ins Log, wenn sich der Stand aendert: wie viele Raeume erkannt sind und welche Hashes die Tabelle nicht kennt. So
     * sieht man nach einem Lauf, ob und welche Raeume in der Tabelle fehlen.
     */
    private static void protokollieren() {
        int[] z = RASTER.zaehleRaeume();
        String stand = (stockwerk == null ? "?" : stockwerk) + "|" + z[0] + "|" + z[1] + "|" + scanner.alleChunksGeladen() + "|"
                + scanner.unbekannteKerne();
        if (stand.equals(zuletztGeloggt)) return;
        zuletztGeloggt = stand;
        ShokiMod.LOGGER.info("[FunnyMap] floor {}: {} rooms recognised, {} only on the map, all chunks loaded: {}, hashes the table does not know (grid place -> hash): {}",
                stockwerk == null ? "?" : stockwerk, z[0], z[1], scanner.alleChunksGeladen(), scanner.unbekannteKerne());
        // Dieselbe Zeile ins Dungeon log (nur waehrend eines Laufs): so steht dort, welche Raeume der Tabelle fehlen
        if (DungeonLog.aktiv()) {
            DungeonLog.ereignis("rooms: floor " + (stockwerk == null ? "?" : stockwerk) + ", " + z[0] + " recognised, " + z[1]
                    + " only on the map, all chunks loaded: " + scanner.alleChunksGeladen() + ", unknown hashes (grid place -> hash): " + scanner.unbekannteKerne());
        }
    }
}
