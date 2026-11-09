package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.HidePlayers;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/**
 * Die Funny Map: der ganze Dungeon auf einer Karte, ab den ersten Sekunden.
 *
 * Alle halbe Sekunde im Dungeon: der {@link WeltScanner} liest aus den Chunks, die der Server schon geschickt hat, die Raeume und Tueren
 * ins {@link Raster}; die Karte, die Hypixel in den Hotbar-Platz 9 legt, liefert den Zustand (entdeckt, geraeumt ...). Gezeichnet wird
 * in {@link FunnyKarteHud}. Es wird nichts angefordert und nichts gesendet: nur gelesen, was ohnehin da ist.
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

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FunnyKarte::tick);
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
        if (ModConfig.INSTANCE.ikohS.funnyMap.followNoamm && !abgeglichen) {
            KartenEinstellungen n = noammEinst;
            if (n != null) return n;
        }
        long jetzt = System.nanoTime();
        if (eigene == null || jetzt - eigeneVon > 500_000_000L) {
            eigene = KartenEinstellungen.ausFeldern(ModConfig.INSTANCE.ikohS.funnyMap);
            eigeneVon = jetzt;
        }
        return eigene;
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
            ModConfig.FunnyMapCategory c = ModConfig.INSTANCE.ikohS.funnyMap;
            return new NoammLage.Lage(c.x, c.y, c.scale, c.extraLine);
        }
        return noamm;
    }

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
        ModConfig.FunnyMapCategory c = ModConfig.INSTANCE.ikohS.funnyMap;
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
        if (!FeatureGate.funnyMap()) {
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
        long stand = NoammLage.aenderung(ordner);
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

    private static void arbeiten(Minecraft client) {
        ClientWelt welt = new ClientWelt(client.level);
        scanner.durchgang(welt);
        scanner.tuerenPruefen(welt);
        karteLesen(client);
        protokollieren();
    }

    /** Der Zustand der Hypixel-Karte kommt ins Raster, sobald die Karte im Hotbar-Platz 9 liegt und kalibriert werden kann */
    private static void karteLesen(Minecraft client) {
        ItemStack karte = client.player.getInventory().getItem(8);
        MapId id = karte.get(DataComponents.MAP_ID);
        if (id == null) return;
        MapItemSavedData daten = client.level.getMapData(id);
        if (daten == null) return;
        if (kalibrierung == null) kalibrierung = KartenLeser.kalibriere(daten.colors, stockwerk);
        if (kalibrierung == null) return;
        RASTER.vonKarte(KartenLeser.lesen(daten.colors, kalibrierung));
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
    }
}
