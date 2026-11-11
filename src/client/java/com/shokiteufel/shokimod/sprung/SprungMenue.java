package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.LeapCustomType;
import com.shokiteufel.shokimod.data.ModConfig.LeapKeyMode;
import com.shokiteufel.shokimod.data.ModConfig.LeapMenuCategory;
import com.shokiteufel.shokimod.data.ModConfig.LeapRender;
import com.shokiteufel.shokimod.dungeonkarte.FunnyKarte;
import com.shokiteufel.shokimod.handler.DungeonLauf;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das Leap Menu von NoammAddons ({@code LeapMenu.kt}, CC0) mit allen seinen Einstellungen: Das Fenster des Spirit Leaps ("Spirit Leap" oder
 * "Teleport to Player") wird durch ein eigenes Menue ersetzt - Raster, Karte oder Rad -, die Mitspieler stehen in der gewaehlten Reihenfolge, ein
 * Klick oder eine Taste springt zu ihnen. Dazu die Meldung im Partychat nach einem Sprung und das Ausblenden der Spieler nebenan.
 *
 * <p>Bei Noamm haengt der Map-Modus an seiner eigenen Karte; sie auszuschalten (sie gibt es hier als Funny Map) hat ihn dort lahmgelegt. Hier
 * kommen die Mitspieler aus der Tab-Liste ({@link SprungTeam}) und die Karte aus der Funny Map, unabhaengig von der Karte von NoammAddons.
 *
 * <p>Der Einsprung in das Fenster steht in {@code SprungMenueMixin}; er fragt nur {@link #uebernimmt}, und ohne Leap Menu kostet er einen Vergleich.
 */
public final class SprungMenue {

    /** Ein Platz im Menue: der Platz im Fenster des Servers (der Kopf, den der Klick anklickt) und der Mitspieler dazu */
    public record Eintrag(int slot, SprungSpieler spieler) {
    }

    /** Was die eigene Reihenfolge braucht */
    record Sortierdaten(LeapCustomType typ, List<String> reihenfolge) {
    }

    private static final Pattern KOPFNAME = Pattern.compile("(?:\\[.+?\\] )?(\\w+)");
    private static final Pattern TUER = Pattern.compile("^(?:\\[.+?\\] )?(\\w+) opened a WITHER door!$");
    private static final Pattern WACHTER = Pattern.compile("^\\[BOSS\\] The Watcher: .+$");
    private static final String SPRUNG = "You have teleported to ";
    private static final String BLUTTUER = "The BLOOD DOOR has been opened!";
    private static final String KERN = "The Core entrance is opening!";
    /** Die Kernkammer von F7 (Bloecke), in der ein Mitspieler gruen steht: x 42 bis 68, y 106 bis 155, z 54 bis 119 */
    private static final double[] KERN_KASTEN = {42, 106, 54, 68, 155, 119};
    private static final long AKTUALISIERUNG_NANOS = 100_000_000L;
    /** Der Text, wenn der Server keinen Kopf zeigt - einmal gebaut, im Bild nur uebergeben */
    private static final Component KEINER = Component.literal("No players found").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);

    private static final Eintrag[] PLAETZE = new Eintrag[4];

    private static AbstractContainerScreen<?> bildschirm;
    private static boolean istSprung;
    private static boolean geklickt;
    private static AbstractContainerMenu letztesMenue;
    private static long letzteAktualisierung;

    private static volatile long versteckenBis;
    private static boolean blutOffen;
    private static boolean kernOffen;
    private static SprungSpieler letzterTuerOeffner;

    private static String reihenfolgeText;
    private static List<String> reihenfolgeListe = List.of();

    private SprungMenue() {
    }

    static LeapMenuCategory cfg() {
        return ModConfig.INSTANCE.ikohS.dungeon.leapMenu;
    }

    // ------------------------------------------------------------------ Erkennen

    /**
     * Ist dies das Fenster des Spirit Leaps, und soll das Menue es ersetzen? Der Titel wird je Fenster einmal gelesen. Noamm: nur im Dungeon, mit
     * "Leap Menu" an, Titel enthaelt "spirit leap" oder "teleport to player".
     */
    public static boolean uebernimmt(AbstractContainerScreen<?> screen) {
        LeapMenuCategory c = cfg();
        if (!c.enabled || !c.customMenu || gestoert || !DungeonLauf.imDungeon()) return false;
        if (screen != bildschirm) {
            bildschirm = screen;
            String titel = screen.getTitle().getString().toLowerCase(Locale.ROOT);
            istSprung = titel.contains("spirit leap") || titel.contains("teleport to player");
            geklickt = false;
        }
        return istSprung;
    }

    /** Welche Darstellung gilt: die Karte nur, wenn die Funny Map etwas zu zeigen hat (und, wenn gewaehlt, der Blutraum offen ist) */
    private static SprungDarstellung darstellung() {
        LeapMenuCategory c = cfg();
        if (c.renderMode == LeapRender.MAP && karteMoeglich(c)) return SprungKarte.INSTANZ;
        if (c.renderMode == LeapRender.WHEEL) return SprungRad.INSTANZ;
        return SprungGitter.INSTANZ;
    }

    /** Wie {@code mapLeapAvailable} bei Noamm: die Karte an und nicht im Boss-Raum, und mit "After Blood Only" erst, wenn der Blutraum offen ist */
    private static boolean karteMoeglich(LeapMenuCategory c) {
        return FeatureGate.funnyMap() && !FunnyKarte.raster().leer() && !DungeonLauf.imBoss() && (!c.mapAfterBlood || blutOffen);
    }

    // ------------------------------------------------------------------ Plaetze

    static Eintrag platz(int i) {
        return i >= 0 && i < PLAETZE.length ? PLAETZE[i] : null;
    }

    /** Auf welchem Platz des Menues steht dieser Spieler? -1, wenn auf keinem */
    static int platzVon(SprungSpieler spieler) {
        for (int i = 0; i < PLAETZE.length; i++) {
            if (PLAETZE[i] != null && PLAETZE[i].spieler() == spieler) return i;
        }
        return -1;
    }

    static SprungKlasse eigeneKlasse() {
        SprungSpieler ich = SprungTeam.ich();
        return ich == null ? null : ich.klasse;
    }

    static Sortierdaten sortierDaten() {
        LeapMenuCategory c = cfg();
        if (!Objects.equals(c.customOrder, reihenfolgeText)) {
            reihenfolgeText = c.customOrder;
            reihenfolgeListe = SprungSortierung.liste(c.customOrder);
        }
        return new Sortierdaten(c.customType, reihenfolgeListe);
    }

    /**
     * Die Plaetze neu aus dem Fenster des Servers lesen ({@code updateLeapMenu}): welcher Platz traegt den Kopf welches Mitspielers - die ersten vier
     * der gewaehlten Reihenfolge, soweit der Server ihren Kopf zeigt. Hoechstens zehnmal je Sekunde.
     */
    private static void aktualisieren(AbstractContainerMenu menu) {
        long jetzt = System.nanoTime();
        if (menu == letztesMenue && jetzt - letzteAktualisierung < AKTUALISIERUNG_NANOS) return;
        letztesMenue = menu;
        letzteAktualisierung = jetzt;

        Arrays.fill(PLAETZE, null);
        Map<String, Integer> koepfe = new HashMap<>();
        int ende = menu.slots.size() - 36;
        for (int i = 0; i < ende; i++) {
            ItemStack stueck = menu.slots.get(i).getItem();
            if (stueck.isEmpty() || !stueck.is(Items.PLAYER_HEAD)) continue;
            Matcher m = KOPFNAME.matcher(stueck.getHoverName().getString());
            if (m.find()) koepfe.put(m.group(1), i);
        }
        Sortierdaten daten = sortierDaten();
        List<SprungSpieler> sortiert = SprungSortierung.sortieren(SprungTeam.andere(), cfg().sorting, eigeneKlasse(), daten.typ(), daten.reihenfolge());
        for (int i = 0; i < PLAETZE.length && i < sortiert.size(); i++) {
            SprungSpieler spieler = sortiert.get(i);
            Integer slot = koepfe.get(spieler.name);
            if (slot != null) PLAETZE[i] = new Eintrag(slot, spieler);
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Zeichnet das Menue statt des Fensters (vom Einsprung gerufen, nachdem {@link #uebernimmt} ja gesagt hat) */
    public static void zeichnen(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, int mausX, int mausY) {
        Minecraft client = Minecraft.getInstance();
        aktualisieren(screen.getMenu());
        int breite = client.getWindow().getGuiScaledWidth();
        int hoehe = client.getWindow().getGuiScaledHeight();

        boolean keiner = true;
        for (Eintrag e : PLAETZE) {
            if (e != null) {
                keiner = false;
                break;
            }
        }
        if (keiner) {
            g.pose().pushMatrix();
            g.pose().translate(breite / 2f, hoehe / 2f);
            g.pose().scale(4f, 4f);
            g.centeredText(client.font, KEINER, 0, 0, 0xFFAA0000);
            g.pose().popMatrix();
            return;
        }
        darstellung().zeichnen(g, client.font, breite, hoehe, mausX, mausY);
    }

    /** Die Farbe eines Kastens (ARGB ohne Durchsichtigkeit): nach den Einstellungen und dem Stand des Laufs */
    static int kastenFarbe(SprungSpieler spieler, boolean zeiger) {
        LeapMenuCategory c = cfg();
        boolean imKern = c.highlightInCore && kernOffen && imKern(spieler);
        return SprungFarben.kasten(zeiger, c.showLastDoorOpener, spieler == letzterTuerOeffner, c.tintDeadPlayers, spieler.tot, c.highlightInCore,
                kernOffen, imKern);
    }

    private static boolean imKern(SprungSpieler spieler) {
        AbstractClientPlayer p = entitaet(spieler.name);
        if (p == null) return false;
        return p.getX() >= KERN_KASTEN[0] && p.getX() < KERN_KASTEN[3]
                && p.getY() >= KERN_KASTEN[1] && p.getY() < KERN_KASTEN[4]
                && p.getZ() >= KERN_KASTEN[2] && p.getZ() < KERN_KASTEN[5];
    }

    private static AbstractClientPlayer entitaet(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        for (AbstractClientPlayer p : client.level.players()) {
            if (name.equals(p.getName().getString())) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ Klick und Tasten

    /**
     * Ein Klick im Fenster. Gilt das Menue, gehoert der Klick ihm (true): Mit "Left Click Only" springen andere Tasten nicht, und ein Klick neben
     * einen Kasten trifft nie einen Platz des Servers darunter - bei Noamm ginge er dann doch zum Fenster durch.
     */
    public static boolean klick(AbstractContainerScreen<?> screen, int taste, double x, double y) {
        if (!uebernimmt(screen)) return false;
        if (taste != 0 && cfg().leftClickOnly) return true;
        Minecraft client = Minecraft.getInstance();
        Integer index = darstellung().index(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), x, y);
        if (index != null) springen(index);
        return true;
    }

    /**
     * Eine Taste im Fenster. Nach einem Klick schliesst die erste Taste das Fenster (es soll nicht offen bleiben, wenn der Server es nicht
     * schliesst); sonst springt eine Taste der Einstellungen zu ihrem Spieler.
     *
     * @return true, wenn die Taste verbraucht ist
     */
    public static boolean taste(AbstractContainerScreen<?> screen, int taste) {
        if (!uebernimmt(screen)) return false;
        Minecraft client = Minecraft.getInstance();
        if (geklickt) {
            geklickt = false;
            if (client.player != null) client.player.closeContainer();
            return false;
        }
        LeapMenuCategory c = cfg();
        if (!c.keybinds) return false;

        Integer index = null;
        if (c.keyMode == LeapKeyMode.CORNERS) {
            int platz = -1;
            for (int i = 0; i < 4; i++) {
                if (c.slotKey(i) == taste) {
                    platz = i;
                    break;
                }
            }
            if (platz != -1) index = darstellung() == SprungRad.INSTANZ ? SprungRad.tastenPlatz(platz) : Integer.valueOf(platz);
        } else {
            SprungKlasse klasse = null;
            for (int k = 0; k < 5; k++) {
                if (c.classKey(k) == taste) {
                    klasse = SprungKlasse.values()[k];
                    break;
                }
            }
            if (klasse != null) {
                for (int i = 0; i < PLAETZE.length; i++) {
                    if (PLAETZE[i] != null && PLAETZE[i].spieler().klasse == klasse) {
                        index = i;
                        break;
                    }
                }
            }
        }
        if (index == null) return false;
        springen(index);
        return true;
    }

    /** Springt zum Spieler auf diesem Platz: ein Klick auf seinen Kopf im Fenster des Servers ({@code triggerLeap}) */
    private static void springen(int index) {
        Eintrag e = platz(index);
        Minecraft client = Minecraft.getInstance();
        if (e == null || client.player == null || client.gameMode == null) return;
        if (e.spieler().tot) {
            client.player.sendSystemMessage(Component.literal("LeapMenu >> ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(e.spieler().name + " is dead!").withStyle(ChatFormatting.RED)));
            return;
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, e.slot(), 0, ContainerInput.PICKUP, client.player);
        geklickt = true;
    }

    // ------------------------------------------------------------------ Chat

    /**
     * Die Chatzeilen des Menues (ohne Farbcodes): der Sprung ("You have teleported to X!") schreibt in den Partychat und blendet auf Wunsch die
     * Spieler nebenan aus; Tuer, Blutraum und Kern halten den Stand fuer die Farben und die Karte fest.
     */
    public static void chat(String zeile) {
        if (zeile == null || !FeatureGate.leapMenu()) return;
        if (zeile.startsWith(SPRUNG) && zeile.endsWith("!")) {
            sprungGemeldet(zeile.substring(SPRUNG.length(), zeile.length() - 1));
            return;
        }
        if (!DungeonLauf.imDungeon()) return;
        Matcher tuer = TUER.matcher(zeile);
        if (tuer.matches()) {
            letzterTuerOeffner = SprungTeam.mitName(tuer.group(1));
        } else if (BLUTTUER.equals(zeile)) {
            // Bei Noamm endet "letzter Tuer-Oeffner", sobald der Blutraum entdeckt ist; hier mit dem Oeffnen der Tuer
            letzterTuerOeffner = null;
        } else if (WACHTER.matcher(zeile).matches()) {
            if (!blutOffen) blutOffen = true;
            letzterTuerOeffner = null;
        } else if (KERN.equals(zeile)) {
            Integer stockwerk = DungeonLauf.stockwerk();
            if (stockwerk != null && stockwerk == 7 && DungeonLauf.imBoss()) kernOffen = true;
        }
    }

    private static void sprungGemeldet(String name) {
        LeapMenuCategory c = cfg();
        Minecraft client = Minecraft.getInstance();
        if (c.announceLeap && client.getConnection() != null) {
            String text = c.leapMessage == null ? "" : c.leapMessage.replace("{name}", name);
            if (!text.isBlank()) client.getConnection().sendCommand("pc " + text);
        }
        if (c.hideAfterLeap) versteckenBis = System.currentTimeMillis() + (long) (c.hideTime * 1000L);
    }

    // ------------------------------------------------------------------ Ausblenden nach dem Sprung

    /** Laeuft das Ausblenden noch? Ein einziger Vergleich, vom Einsprung in die Darstellung je Figur gefragt */
    public static boolean versteckenAktiv() {
        long bis = versteckenBis;
        return bis != 0L && System.currentTimeMillis() <= bis;
    }

    /** Soll diese Figur nach dem Sprung nicht gezeichnet werden: ein Mitspieler, hoechstens zwei Bloecke von einem entfernt */
    public static boolean versteckt(Player figur) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || figur == client.player) return false;
        if (figur.distanceToSqr(client.player) > 4) return false;
        String name = figur.getName().getString();
        for (SprungSpieler s : SprungTeam.alle()) {
            if (s.name.equals(name) && !name.equals(client.player.getName().getString())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Fehler

    /** Hat das Menue einen Fehler geworfen? Dann gilt bis zum Ende des Dungeons wieder das Fenster des Servers */
    private static boolean gestoert;

    /**
     * Ein Fehler im Menue (Zeichnen, Klick, Taste): ins Log, ein Hinweis im Chat - und das Menue ist fuer den Rest des Laufs aus, das Fenster des
     * Servers geht wieder. Der Einsprung sitzt in einem Fenster, das jeder oeffnet; ein Absturz dort waere schlimmer als ein fehlendes Menue.
     */
    public static void gestoert(String wo, RuntimeException e) {
        if (gestoert) return;
        gestoert = true;
        com.shokiteufel.shokimod.ShokiMod.LOGGER.warn("[LeapMenu] error while {}: the normal leap menu is shown for the rest of this run", wo, e);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] The leap menu hit an error (" + e + ") - the normal menu is shown until you leave the dungeon.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------------ Zuruecksetzen

    /** Der Dungeon ist vorbei (oder man hat ihn verlassen): alles, was der Lauf gemerkt hat, ist vergessen */
    static void dungeonVorbei() {
        gestoert = false;
        Arrays.fill(PLAETZE, null);
        blutOffen = false;
        kernOffen = false;
        letzterTuerOeffner = null;
        bildschirm = null;
        istSprung = false;
        geklickt = false;
        letztesMenue = null;
    }
}
