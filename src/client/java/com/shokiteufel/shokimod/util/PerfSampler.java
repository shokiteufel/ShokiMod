package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Das "Performance log" (Reiter Logs, von Haus aus aus): Findet heraus, wo die Zeit des Spiel-Threads bleibt, wenn es ruckelt.
 *
 * <p>Ein Hilfsfaden schaut zwanzigmal je Sekunde auf den Aufrufstapel des Spiel-Threads und merkt sich, ob ShokiMod darin vorkommt - und
 * welche Methode. Jede Minute steht eine Zusammenfassung in {@code logs/shokimod-perf.log}: wie viel Prozent der Zeit in ShokiMod lag, die
 * Einstiegsstellen (die aeusserste ShokiMod-Methode im Stapel, also "wer hat es angestossen"), die innerste (wo die Zeit wirklich lag, mit
 * Zeile), was ausserhalb von ShokiMod am meisten Zeit brauchte, dazu die Zahl der Ticks ueber 50 ms, der laengste Tick und die
 * Muellsammlung. So laesst sich "es laggt" einer Stelle zuordnen, statt zu raten. Kostet fast nichts: zwanzig Stapelabfragen je Sekunde.
 *
 * <p>Wer es braucht: einschalten, ein paar Minuten spielen, wo es laggt, die Datei weitergeben.
 */
public final class PerfSampler {
    /** Alles, was zu ShokiMod gehoert (auch die Mixins) */
    static final String MOD = "com.shokiteufel.shokimod.";
    private static final long INTERVALL_NANOS = 50_000_000L;
    private static final long FENSTER_MILLIS = 60_000L;
    private static final long LANGER_TICK_NANOS = 50_000_000L;
    private static final int ZEILEN = 12;
    private static final long DATEI_MAX = 1_000_000L;

    private static final Object SPERRE = new Object();
    private static volatile Thread haupt;
    private static volatile boolean laeuft;
    private static Thread faden;
    private static Fenster fenster = new Fenster();
    private static long tickBeginn;
    private static int takt;
    private static long gcAnzahl;
    private static long gcMillis;
    private static long letztesBild;
    private static long speicherStart;

    private PerfSampler() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            haupt = Thread.currentThread();
            tickBeginn = System.nanoTime();
            if (++takt >= 20) {
                takt = 0;
                abgleichen();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (laeuft && tickBeginn != 0L) {
                long dauer = System.nanoTime() - tickBeginn;
                synchronized (SPERRE) {
                    fenster.tick(dauer);
                }
            }
        });
    }

    /** Ein Bild wurde gezeichnet (vom HudRenderer): zaehlt die Bilder und merkt sich die laengste Pause zwischen zwei Bildern */
    public static void bild() {
        if (!laeuft) {
            return;
        }
        long jetzt = System.nanoTime();
        synchronized (SPERRE) {
            if (letztesBild != 0L) {
                fenster.bild(jetzt - letztesBild);
            }
            letztesBild = jetzt;
        }
    }

    /** Wie viel der Spiel-Thread bisher angelegt hat (Bytes), oder -1, wenn die Plattform es nicht kann */
    private static long angelegt() {
        try {
            Thread t = haupt;
            if (t != null && ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean mx) {
                return mx.getThreadAllocatedBytes(t.threadId());
            }
        } catch (RuntimeException | LinkageError e) {
            // nicht verfuegbar: der Wert fehlt im Bericht
        }
        return -1L;
    }

    /** Haelt den Hilfsfaden im Einklang mit dem Schalter */
    private static void abgleichen() {
        boolean soll = ModConfig.INSTANCE.logs.perfLog;
        if (soll && !laeuft) {
            starten();
        } else if (!soll && laeuft) {
            stoppen();
        }
    }

    private static Path datei() {
        return FabricLoader.getInstance().getGameDir().resolve("logs").resolve("shokimod-perf.log");
    }

    private static void starten() {
        laeuft = true;
        synchronized (SPERRE) {
            fenster = new Fenster();
        }
        merkeGc();
        letztesBild = 0L;
        speicherStart = angelegt();
        schreibe("---- Performance log on: " + kopf());
        Thread t = new Thread(PerfSampler::schleife, "shokimod-perf");
        t.setDaemon(true);
        faden = t;
        t.start();
    }

    private static void stoppen() {
        laeuft = false;
        Thread t = faden;
        if (t != null) {
            LockSupport.unpark(t);
        }
        schreibe("---- Performance log off");
    }

    private static String kopf() {
        ModConfig c = ModConfig.INSTANCE;
        return "mod " + FabricLoader.getInstance().getModContainer("shokimod").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + ", Java " + System.getProperty("java.version") + ", " + Runtime.getRuntime().availableProcessors() + " CPUs, "
                + (Runtime.getRuntime().maxMemory() >> 20) + " MB heap | on: "
                + an("profit", c.profit.enabled) + an("funnyMap", c.ikohS.dungeon.funnyMap.enabled) + an("starMobs", c.ikohS.dungeon.starMobs.enabled)
                + an("mist", c.ikohS.mist.runicGhosts || c.ikohS.mist.showGhosts) + an("forge", c.mining.forge.enabled)
                + an("skillXp", c.skillXp.enabled) + an("hidePlayers", c.odin.hidePlayers) + an("mobVisuals", c.mobVisuals.masterEnabled);
    }

    private static String an(String name, boolean an) {
        return an ? name + " " : "";
    }

    private static void schleife() {
        long fensterStart = System.currentTimeMillis();
        while (laeuft) {
            // ereignis-ok: Abtasten ist zeitgesteuert, es gibt kein Ereignis, auf das man warten koennte
            LockSupport.parkNanos(INTERVALL_NANOS);
            if (!laeuft) {
                break;
            }
            Thread ziel = haupt;
            if (ziel != null) {
                StackTraceElement[] stapel = ziel.getStackTrace();
                synchronized (SPERRE) {
                    fenster.abtasten(stapel);
                }
            }
            long jetzt = System.currentTimeMillis();
            if (jetzt - fensterStart >= FENSTER_MILLIS) {
                fensterStart = jetzt;
                Fenster fertig;
                synchronized (SPERRE) {
                    fertig = fenster;
                    fenster = new Fenster();
                }
                long jetztAngelegt = angelegt();
                String speicher = speicherStart >= 0 && jetztAngelegt >= speicherStart
                        ? String.format(java.util.Locale.ROOT, "%.0f MB/s", (jetztAngelegt - speicherStart) / 1_048_576.0 / (FENSTER_MILLIS / 1000.0)) : "n/a";
                speicherStart = jetztAngelegt;
                schreibe(fertig.bericht(gcText(), speicher));
            }
        }
    }

    private static void merkeGc() {
        long n = 0;
        long ms = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        gcAnzahl = n;
        gcMillis = ms;
    }

    private static String gcText() {
        long n = gcAnzahl;
        long ms = gcMillis;
        merkeGc();
        return (gcAnzahl - n) + " runs, " + (gcMillis - ms) + " ms";
    }

    private static synchronized void schreibe(String text) {
        try {
            Path d = datei();
            Files.createDirectories(d.getParent());
            if (Files.exists(d) && Files.size(d) > DATEI_MAX) {
                Files.writeString(d, "---- (older lines cut) ----\n", StandardCharsets.UTF_8);
            }
            String zeit = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Files.writeString(d, "[" + zeit + "] " + text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Perf] could not write logs/shokimod-perf.log: {}", e.toString());
        }
    }

    /** Die Zahlen eines Fensters; ohne Faden und Datei, damit sich das Zaehlen pruefen laesst */
    static final class Fenster {
        int proben;
        int mod;
        int ticks;
        int langeTicks;
        long laengsterTick;
        int bilder;
        int bilderUeber33;
        int bilderUeber50;
        long laengstesBild;
        final Map<String, Integer> einstieg = new HashMap<>();
        final Map<String, Integer> innerste = new HashMap<>();
        final Map<String, Integer> fremd = new HashMap<>();

        /** Der Abstand zwischen zwei Bildern in Nanosekunden: ueber 33 ms sind unter 30 Bilder je Sekunde, ueber 50 ms unter 20 */
        void bild(long abstand) {
            bilder++;
            if (abstand > 33_000_000L) {
                bilderUeber33++;
            }
            if (abstand > 50_000_000L) {
                bilderUeber50++;
            }
            laengstesBild = Math.max(laengstesBild, abstand);
        }

        void tick(long nanos) {
            ticks++;
            if (nanos > LANGER_TICK_NANOS) {
                langeTicks++;
            }
            laengsterTick = Math.max(laengsterTick, nanos);
        }

        /** Ein Aufrufstapel, wie {@code Thread.getStackTrace()} ihn liefert: Index 0 ist die innerste Stelle */
        void abtasten(StackTraceElement[] stapel) {
            if (stapel == null || stapel.length == 0) {
                return;
            }
            proben++;
            int innen = -1;
            int aussen = -1;
            for (int i = 0; i < stapel.length; i++) {
                if (stapel[i].getClassName().startsWith(MOD)) {
                    if (innen < 0) {
                        innen = i;
                    }
                    aussen = i;
                }
            }
            if (innen < 0) {
                StackTraceElement oben = stapel[0];
                fremd.merge(kurz(oben.getClassName()) + "." + oben.getMethodName(), 1, Integer::sum);
                return;
            }
            mod++;
            einstieg.merge(kurz(stapel[aussen].getClassName()) + "." + stapel[aussen].getMethodName(), 1, Integer::sum);
            innerste.merge(kurz(stapel[innen].getClassName()) + "." + stapel[innen].getMethodName() + ":" + stapel[innen].getLineNumber(), 1, Integer::sum);
        }

        String bericht(String gc, String speicher) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(java.util.Locale.ROOT, "window: %d samples, ShokiMod %.1f %% (%d), other %.1f %%; ticks %d, over 50 ms: %d, longest %d ms; GC: %s; game thread allocates %s%n",
                    proben, prozent(mod), mod, prozent(proben - mod), ticks, langeTicks, laengsterTick / 1_000_000L, gc, speicher));
            sb.append(String.format(java.util.Locale.ROOT, "  frames: %d (%.0f per second), over 33 ms: %d, over 50 ms: %d, longest %d ms%n",
                    bilder, bilder / (FENSTER_MILLIS / 1000.0), bilderUeber33, bilderUeber50, laengstesBild / 1_000_000L));
            zeilen(sb, "  entry points in ShokiMod (who started it):", einstieg);
            zeilen(sb, "  innermost ShokiMod frames (where the time lay):", innerste);
            zeilen(sb, "  busiest outside ShokiMod:", fremd);
            return sb.toString().stripTrailing();
        }

        private double prozent(int n) {
            return proben == 0 ? 0.0 : 100.0 * n / proben;
        }

        private void zeilen(StringBuilder sb, String titel, Map<String, Integer> karte) {
            if (karte.isEmpty()) {
                return;
            }
            sb.append(titel).append('\n');
            List<Map.Entry<String, Integer>> alle = new ArrayList<>(karte.entrySet());
            alle.sort((a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < Math.min(ZEILEN, alle.size()); i++) {
                sb.append(String.format(java.util.Locale.ROOT, "    %5d  %5.1f %%  %s%n", alle.get(i).getValue(), prozent(alle.get(i).getValue()), alle.get(i).getKey()));
            }
        }

        /** Klassenname ohne das Paket von ShokiMod, damit die Zeilen kurz bleiben */
        private static String kurz(String klasse) {
            return klasse.startsWith(MOD) ? klasse.substring(MOD.length()) : klasse;
        }
    }
}
