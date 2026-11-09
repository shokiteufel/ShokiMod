package com.shokiteufel.shokimod.paket;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.shokiteufel.shokimod.verkehr.Beziehung;
import com.shokiteufel.shokimod.verkehr.Einordnung;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.slf4j.Logger;

/**
 * Pruefstand der Paket-Schicht, nur mit {@code -Dshokimod.pakete=...} oder der Datei
 * {@code logs/shokimod-pakete/diagnose.txt} im Spielordner (ohne einen der beiden Schalter passiert nichts und nichts
 * kostet etwas). Werte, auch kombinierbar ({@code zaehlen,reihenfolge}):
 * <ul>
 *   <li>{@code zaehlen}: alle 10 s eine Logzeile mit Zahl der Pakete, dem groessten Tick und den haeufigsten Typen.
 *       Meldet zusaetzlich, wenn gar keine Pakete ankommen (Abgriff nicht eingehaengt).</li>
 *   <li>{@code reihenfolge}: prueft an Chatnachrichten, dass die Phasen zur Spielreihenfolge passen: {@code VOR}, dann
 *       das Chatereignis von Fabric, dann {@code NACH}. Meldet die Zahlen alle 10 s und die ersten Abweichungen.</li>
 *   <li>{@code alle} (mit {@code zaehlen}): die ganze Typ-Tabelle statt der sechs haeufigsten. {@code zaehlen} zaehlt
 *       eingehende Spielpakete getrennt von allem anderen (ausgehend, Handshake, Login, Konfiguration) und gibt beim
 *       Trennen die Summe der Verbindung je Richtung, Phase und Typ aus ({@code ein|aus/PHASE:Klasse xN}).</li>
 *   <li>{@code ablauf}: schreibt jedes Paket in der Reihenfolge des Busses in {@code logs/shokimod-pakete/bus-ablauf.txt}
 *       (zum Vergleich mit der Reihenfolge des Recorders).</li>
 *   <li>{@code zustand}: Seitenleiste und Tab-Liste, wie der Bus sie aus den Paketen nachbaut.</li>
 * </ul>
 */
public final class PaketDiagnose {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MODUS = modusLesen();
    private static final long FENSTER_NANOS = 10_000_000_000L;

    private static final Map<Class<?>, int[]> ZAEHLER = new HashMap<>();
    /** Ausgehende Pakete und eingehende ausserhalb der Spiel-Phase, Schluessel {@code ein|aus/PHASE:Klasse}. */
    private static final Map<String, int[]> ZAEHLER_ANDERE = new HashMap<>();
    /** Alle Pakete der laufenden Verbindung nach {@code ein|aus/PHASE:Klasse}; wird beim Trennen ausgegeben und geleert. */
    private static final Map<String, int[]> GESAMT = new HashMap<>();
    private static long fensterStart = System.nanoTime();
    private static long pakete;
    /** Eingehende Spielpakete je Beziehung (Index = Ordinal von Beziehung) und Index 4 = ohne Einordnung, im laufenden Fenster. */
    private static final long[] beziehungen = new long[5];
    private static long andere;
    private static long ausgehendImFenster;
    private static long direktImFenster;
    private static long groessterTick;
    private static long aktuellerTick = -1;
    private static long imAktuellenTick;

    // Reihenfolgeprobe: 0 = nichts offen, 1 = VOR gesehen, 2 = VOR und Chatereignis gesehen
    private static int stufe;
    private static long probeNummer;
    private static long reihenfolgeOk;
    private static long ohneChatereignis;
    private static long reihenfolgeFalsch;
    private static long chatereignisOhneVor;
    private static int beispiele;

    private PaketDiagnose() {
    }

    /**
     * Die Schalter der Diagnose: zuerst {@code -Dshokimod.pakete=...}; ist der nicht gesetzt, die erste nicht leere Zeile
     * (ohne Kommentarzeilen mit {@code #}) der Datei {@code <Spielordner>/logs/shokimod-pakete/diagnose.txt}. Die Datei ist der
     * bequeme Weg in einem Launcher, in dem man keine Startoptionen setzen will; sie loeschen oder leeren schaltet die
     * Diagnose wieder ab. Ohne beides: aus, und die Diagnose kostet nichts.
     */
    private static String modusLesen() {
        String eigenschaft = System.getProperty("shokimod.pakete", "");
        if (!eigenschaft.isBlank()) {
            return eigenschaft.toLowerCase(java.util.Locale.ROOT);
        }
        try {
            java.nio.file.Path datei = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                    .resolve("logs").resolve("shokimod-pakete").resolve("diagnose.txt");
            if (java.nio.file.Files.isRegularFile(datei)) {
                return modusAusZeilen(java.nio.file.Files.readAllLines(datei, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            // kein Spielordner (zum Beispiel im Test) oder Datei nicht lesbar: Diagnose bleibt aus
        }
        return "";
    }

    /**
     * Die erste Zeile mit Inhalt, die kein Kommentar ({@code #}) ist, kleingeschrieben; sonst leer. Eine Byte-Order-Marke am
     * Dateianfang (die Windows-Editoren wie Notepad und mancher Skriptaufruf setzen) wird ueberlesen: Sie ist kein Leerzeichen
     * und liesse sonst eine Kommentarzeile wie einen Schalter aussehen.
     */
    static String modusAusZeilen(java.util.List<String> zeilen) {
        for (String zeile : zeilen) {
            String z = zeile.replace("﻿", "").strip();
            if (!z.isEmpty() && !z.startsWith("#")) {
                return z.toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "";
    }

    public static void init() {
        if (MODUS.isBlank()) {
            return;
        }
        PaketBus.init();
        if (MODUS.contains("zaehlen")) {
            PaketBus.abonnierenAlle(PaketPhase.NACH, "Diagnose zaehlen", PaketDiagnose::gezaehlt);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> summeAusgeben());
        }
        if (MODUS.contains("reihenfolge")) {
            reihenfolgeAnmelden();
        }
        if (MODUS.contains("ablauf")) {
            ablaufOeffnen();
            PaketBus.abonnierenAlle(PaketPhase.NACH, "Diagnose ablauf", PaketDiagnose::ablaufPaket);
        }
        if (MODUS.contains("zustand")) {
            zustandAnmelden();
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> ausgeben());
        LOG.info("ShokiMod Pakete: Diagnose an ({})", MODUS);
    }

    /** Klassenname ohne Paket, Unterklassen mit {@code $} (zum Beispiel {@code ServerboundMovePlayerPacket$Pos}), wie ihn der Recorder schreibt. */
    private static String kurzname(Class<?> klasse) {
        String name = klasse.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /** Schluessel {@code ein|aus/PHASE:Klasse} (zum Beispiel {@code aus/PLAY:ServerboundMovePlayerPacket$Pos}). */
    private static String schluessel(net.minecraft.network.protocol.Packet<?> paket, PaketKopf kopf) {
        return (kopf.richtung() == PaketRichtung.EINGEHEND ? "ein/" : "aus/")
                + (kopf.protokoll() == null ? "?" : kopf.protokoll().name()) + ":" + kurzname(paket.getClass());
    }

    /** Beim Trennen: alle Pakete dieser Verbindung je Richtung, Phase und Typ, fuer den Vergleich mit dem Recorder. */
    private static void summeAusgeben() {
        if (GESAMT.isEmpty()) {
            return;
        }
        long ein = 0;
        long aus = 0;
        List<Map.Entry<String, int[]>> alle = new ArrayList<>(GESAMT.entrySet());
        alle.sort(Map.Entry.comparingByKey());
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, int[]> e : alle) {
            if (e.getKey().startsWith("ein/")) {
                ein += e.getValue()[0];
            } else {
                aus += e.getValue()[0];
            }
            text.append(text.length() == 0 ? "" : ", ").append(e.getKey()).append(" x").append(e.getValue()[0]);
        }
        LOG.info("ShokiMod Pakete: Summe der Verbindung: {} eingehend, {} ausgehend; {}", ein, aus, text);
        GESAMT.clear();
    }

    /** Modus {@code ablauf}: jedes Paket in der Reihenfolge des Busses in die Datei {@code logs/shokimod-pakete/bus-ablauf.txt}. */
    private static java.io.BufferedWriter ablauf;

    private static void ablaufOeffnen() {
        try {
            java.nio.file.Path ordner = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs").resolve("shokimod-pakete");
            java.nio.file.Files.createDirectories(ordner);
            ablauf = java.nio.file.Files.newBufferedWriter(ordner.resolve("bus-ablauf.txt"), java.nio.charset.StandardCharsets.UTF_8);
            ClientTickEvents.END_CLIENT_TICK.register(client -> ablaufLeeren());
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                ablaufSchreiben("---ENDE---");
                ablaufLeeren();
            });
        } catch (java.io.IOException e) {
            LOG.warn("ShokiMod Pakete: Ablaufdatei nicht geoeffnet", e);
        }
    }

    private static void ablaufPaket(net.minecraft.network.protocol.Packet<?> paket, PaketKopf kopf) {
        Einordnung e = kopf.einordnung();
        ablaufSchreiben(schluessel(paket, kopf) + " " + kopf.laufendeNummer()
                + " " + (e == null ? "-" : e.beziehung().kurz() + "/" + e.basis().kurz()));
    }

    private static void ablaufSchreiben(String zeile) {
        try {
            if (ablauf != null) {
                ablauf.write(zeile);
                ablauf.newLine();
            }
        } catch (java.io.IOException e) {
            ablauf = null;
        }
    }

    private static void ablaufLeeren() {
        try {
            if (ablauf != null) {
                ablauf.flush();
            }
        } catch (java.io.IOException e) {
            ablauf = null;
        }
    }

    private static void gezaehlt(net.minecraft.network.protocol.Packet<?> paket, PaketKopf kopf) {
        GESAMT.computeIfAbsent(schluessel(paket, kopf), k -> new int[1])[0]++;
        if (kopf.direkt()) {
            direktImFenster++;
        }
        if (kopf.richtung() == PaketRichtung.EINGEHEND && kopf.protokoll() == ConnectionProtocol.PLAY) {
            ZAEHLER.computeIfAbsent(paket.getClass(), k -> new int[1])[0]++;
            pakete++;
            beziehungen[kopf.einordnung() == null ? 4 : kopf.einordnung().beziehung().ordinal()]++;
            if (kopf.tick() != aktuellerTick) {
                aktuellerTick = kopf.tick();
                imAktuellenTick = 0;
            }
            groessterTick = Math.max(groessterTick, ++imAktuellenTick);
        } else {
            // alles andere: ausgehende Pakete und eingehende der Handshake-, Login-, Konfigurations- und Status-Phase
            String schluessel = (kopf.richtung() == PaketRichtung.EINGEHEND ? "ein/" : "aus/")
                    + (kopf.protokoll() == null ? "?" : kopf.protokoll().name()) + ":" + kurzname(paket.getClass());
            ZAEHLER_ANDERE.computeIfAbsent(schluessel, k -> new int[1])[0]++;
            andere++;
            if (kopf.richtung() == PaketRichtung.AUSGEHEND) {
                ausgehendImFenster++;
            }
        }
    }

    private static void reihenfolgeAnmelden() {
        PaketBus.abonnieren(ClientboundSystemChatPacket.class, PaketPhase.VOR, "Diagnose Reihenfolge", (paket, kopf) -> {
            if (stufe != 0) {
                reihenfolgeFalsch++;
                beispiel("Chat-Paket " + kopf.laufendeNummer() + ": VOR, obwohl das vorige noch offen war (Stufe " + stufe + ")");
            }
            stufe = 1;
            probeNummer = kopf.laufendeNummer();
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((nachricht, overlay) -> {
            if (stufe == 1) {
                stufe = 2;
            } else if (stufe == 0) {
                chatereignisOhneVor++;
            }
            return true;
        });
        PaketBus.abonnieren(ClientboundSystemChatPacket.class, PaketPhase.NACH, "Diagnose Reihenfolge", (paket, kopf) -> {
            if (stufe == 2 && kopf.laufendeNummer() == probeNummer) {
                reihenfolgeOk++;
            } else if (stufe == 1 && kopf.laufendeNummer() == probeNummer) {
                ohneChatereignis++;   // Fabrics Ereignis kam nicht (zum Beispiel weil ein anderer Abonnent die Zeile ausblendet)
            } else {
                reihenfolgeFalsch++;
                beispiel("Chat-Paket " + kopf.laufendeNummer() + ": NACH in Stufe " + stufe + ", erwartet Nummer " + probeNummer);
            }
            stufe = 0;
        });
    }

    // Zustand: die aus Paketen nachgebaute Seitenleiste und Tab-Liste
    private static long seitenleisteAenderungen;
    private static long tabAenderungen;
    private static int zustandBeispiele;

    private static void zustandAnmelden() {
        com.shokiteufel.shokimod.paket.zustand.PaketZustaende.init();
        com.shokiteufel.shokimod.paket.zustand.PaketZustaende.seitenleiste().hoeren(a -> {
            seitenleisteAenderungen++;
            if (zustandBeispiele++ < 30) {
                StringBuilder zeilen = new StringBuilder();
                for (int i = 0; i < Math.min(3, a.geaenderteZeilen().size()); i++) {
                    int index = a.geaenderteZeilen().get(i);
                    zeilen.append(i == 0 ? "" : " | ").append(index).append(": '")
                            .append(index < a.neu().size() ? a.neu().get(index) : "(weg)").append("'");
                }
                LOG.info("ShokiMod Pakete: Seitenleiste '{}' {} Zeilen, geaendert: {}", a.titelNeu(), a.neu().size(), zeilen);
            }
        });
        com.shokiteufel.shokimod.paket.zustand.PaketZustaende.tabListe().hoeren((id, name, alt, neu) -> {
            tabAenderungen++;
            if (neu != null && zustandBeispiele++ < 30) {
                LOG.info("ShokiMod Pakete: Tab-Anzeigename '{}'", neu);
            }
        });
        com.shokiteufel.shokimod.paket.zustand.PaketZustaende.weltHoeren((art, zwischenwelt, neueWelt, kopf) ->
                LOG.info("ShokiMod Pakete: Weltwechsel {} (Zwischenwelt {}, neue Welt {}), Paket {}", art, zwischenwelt, neueWelt, kopf.laufendeNummer()));
    }

    private static void beispiel(String text) {
        if (beispiele++ < 5) {
            LOG.warn("ShokiMod Pakete: Reihenfolge weicht ab: {}", text);
        }
    }

    private static void ausgeben() {
        long jetzt = System.nanoTime();
        if (jetzt - fensterStart < FENSTER_NANOS) {
            return;
        }
        double sekunden = (jetzt - fensterStart) / 1e9;
        if (MODUS.contains("zaehlen")) {
            if (pakete == 0 && net.minecraft.client.Minecraft.getInstance().level != null) {
                LOG.warn("ShokiMod Pakete: in {} s kam kein Paket an, obwohl eine Welt geladen ist: Der Abgriff ist vermutlich nicht eingehaengt",
                        String.format("%.1f", sekunden));
            }
            List<Map.Entry<Class<?>, int[]>> sortiert = new ArrayList<>(ZAEHLER.entrySet());
            sortiert.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
            StringBuilder top = new StringBuilder();
            // Mit dem Schalter "alle" (zum Beispiel zaehlen,alle) die ganze Typ-Tabelle statt der sechs haeufigsten
            int grenze = MODUS.contains("alle") ? sortiert.size() : 6;
            for (int i = 0; i < Math.min(grenze, sortiert.size()); i++) {
                top.append(i == 0 ? "" : ", ").append(kurzname(sortiert.get(i).getKey())).append(" x").append(sortiert.get(i).getValue()[0]);
            }
            LOG.info("ShokiMod Pakete: {} Pakete in {} s, groesster Tick {}, laufende Nummer {}, Tick {}, Fehler {}; haeufigste: {}",
                    pakete, String.format("%.1f", sekunden), groessterTick, PaketBus.laufendeNummer(), PaketBus.tick(),
                    PaketBus.fehlerGesamt(), top);
            LOG.info("ShokiMod Pakete: Einordnung der eingehenden Spielpakete: eigen {}, fremd {}, gemischt {}, welt {}, ohne Einordnung {}",
                    beziehungen[Beziehung.EIGEN.ordinal()], beziehungen[Beziehung.FREMD.ordinal()],
                    beziehungen[Beziehung.GEMISCHT.ordinal()], beziehungen[Beziehung.WELT.ordinal()], beziehungen[4]);
            java.util.Arrays.fill(beziehungen, 0);
            // Alles ausserhalb der eingehenden Spiel-Phase: ausgehend sowie Handshake, Login, Konfiguration, Status
            List<Map.Entry<String, int[]>> weitere = new ArrayList<>(ZAEHLER_ANDERE.entrySet());
            weitere.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
            StringBuilder weitereText = new StringBuilder();
            int grenzeWeitere = MODUS.contains("alle") ? weitere.size() : 6; // eigene Grenze: die der Spielpakete ist hier falsch
            for (int i = 0; i < Math.min(grenzeWeitere, weitere.size()); i++) {
                weitereText.append(i == 0 ? "" : ", ").append(weitere.get(i).getKey()).append(" x").append(weitere.get(i).getValue()[0]);
            }
            LOG.info("ShokiMod Pakete: Weitere {} Pakete in {} s (ausgehend {}), direkt geliefert {} (alle Phasen); haeufigste: {}",
                    andere, String.format("%.1f", sekunden), ausgehendImFenster, direktImFenster, weitereText);
            if (pakete > 0 && ausgehendImFenster == 0 && net.minecraft.client.Minecraft.getInstance().level != null) {
                LOG.warn("ShokiMod Pakete: in {} s kam ein Paket an, aber keines ging hinaus: Der Haken fuer ausgehende Pakete (Connection.sendPacket) ist vermutlich nicht eingehaengt",
                        String.format("%.1f", sekunden));
            }
            ZAEHLER.clear();
            ZAEHLER_ANDERE.clear();
            pakete = 0;
            andere = 0;
            ausgehendImFenster = 0;
            direktImFenster = 0;
            groessterTick = 0;
        }
        if (MODUS.contains("reihenfolge")) {
            LOG.info("ShokiMod Pakete: Reihenfolge Chat: richtig (VOR, Chatereignis, NACH) {}, ohne Chatereignis {}, falsch {}, Chatereignis ohne VOR {}",
                    reihenfolgeOk, ohneChatereignis, reihenfolgeFalsch, chatereignisOhneVor);
        }
        if (MODUS.contains("zustand")) {
            var seite = com.shokiteufel.shokimod.paket.zustand.PaketZustaende.seitenleiste();
            var tab = com.shokiteufel.shokimod.paket.zustand.PaketZustaende.tabListe();
            LOG.info("ShokiMod Pakete: Zustand: Seitenleiste '{}' {} Zeilen ({} Aenderungen), Tab-Liste {} Eintraege, {} Zeilen ({} Anzeigename-Aenderungen), {} Teams",
                    seite.titel(), seite.zeilen().size(), seitenleisteAenderungen, tab.anzahl(), tab.zeilen().size(), tabAenderungen,
                    com.shokiteufel.shokimod.paket.zustand.PaketZustaende.teams().anzahl());
            // Der ganze Stand, fuer den Vergleich mit dem, was ein Server gesendet hat (Zeilen mit " | " getrennt)
            if (!seite.zeilen().isEmpty()) {
                LOG.info("ShokiMod Pakete: Zustand-Seitenleiste: {}", String.join(" | ", seite.zeilen()));
            }
            if (tab.anzahl() > 0) {
                LOG.info("ShokiMod Pakete: Zustand-Tab: {}", String.join(" | ", tab.zeilen()));
            }
        }
        fensterStart = jetzt;
    }
}
