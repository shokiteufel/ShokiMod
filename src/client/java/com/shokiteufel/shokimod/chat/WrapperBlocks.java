package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

/**
 * Die Bloecke zwischen zwei langen Linien ({@code ▬} 64 Mal): die Truhen der Bergbau-Inseln, die Kristalle und Laeufe des Crystal Nucleus, der Tod
 * Arachnes und das Level-Up der Events. Bei SkyHanni unterscheidet die Farbe der Linie, zu welchem Block sie gehoert (gelb/rosa, tuerkis, lila, gruen,
 * weiss); im Klartext sehen alle gleich aus. Deshalb entscheidet hier die erste Zeile im Block, was er ist:
 *
 * <ul>
 * <li>"CRYSTAL FOUND (n/5)": ein gefundener Kristall,</li>
 * <li>"CHEST LOCKPICKED", "LOOT CHEST COLLECTED" oder "REWARDS": eine Truhe,</li>
 * <li>"ARACHNE DOWN!": der Tod Arachnes,</li>
 * <li>eine Zeile des Level-Ups eines Events,</li>
 * <li>sonst, im Crystal Hollows mit der Zusammenfassung eines Laufs an: die Zusammenfassung.</li>
 * </ul>
 *
 * Leere Zeilen am Anfang entscheiden nichts. Die oeffnende Linie ist da, bevor man weiss, was folgt: Sie geht weg, wenn irgendein Block an der Stelle
 * in Frage kommt - ist es dann doch ein fremder Block, fehlt ihm die obere Linie (die untere bleibt, denn sie ist als fremd erkannt). Bei den Events
 * geht nur die untere Linie mit, denn sonst verlore jeder fremde Block in SkyBlock seine obere.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PowderMiningChatFilter}, {@code CrystalNucleusChatFilter}, {@code ArachneChatMessageHider}): Dort
 * schaltet jede Linie einen Zustand um ("unclosedRewards", "unclosedCrystalCollected", "unclosedRunCompleted", "hideArachneDeadMessage"). Die Umsetzung ist
 * eigene Arbeit: ein gemeinsamer Zustand statt vier, die Entscheidung an der ersten Zeile, und eine Notbremse - ein Block, der nicht endet (die
 * schliessende Linie fehlt), gilt nach zehn Sekunden oder 60 Zeilen als beendet, damit nie der ganze Chat verschwindet.
 */
final class WrapperBlocks {

    enum Type {
        NONE, PENDING, POWDER, CRYSTAL, RUN, ARACHNE, EVENT, FOREIGN
    }

    /** Wie lange ein Block hoechstens dauert; eine Truhe ist in Millisekunden da */
    static final long MAX_BLOCK_MS = 10_000L;
    /** Wie viele Zeilen ein Block hoechstens hat */
    static final int MAX_LINES = 60;
    /** Die Linie hat 64 Zeichen */
    static final int WRAPPER_LENGTH = 64;

    private final CrystalNucleusFilter nucleus;

    private Type type = Type.NONE;
    private long openedAt = 0L;
    private int lines = 0;

    WrapperBlocks(CrystalNucleusFilter nucleus) {
        this.nucleus = nucleus;
    }

    Type type() {
        return type;
    }

    void reset() {
        type = Type.NONE;
        lines = 0;
    }

    /** Ist die Zeile eine lange Linie? */
    static boolean isWrapper(String line) {
        if (line.length() != WRAPPER_LENGTH || line.charAt(0) != '▬') return false;
        for (int i = 1; i < WRAPPER_LENGTH; i++) {
            if (line.charAt(i) != '▬') return false;
        }
        return true;
    }

    /** Welche Bloecke kommen an dieser Stelle mit den eingeschalteten Schaltern in Frage? */
    private static final class Candidates {
        boolean powder;
        boolean crystal;
        boolean run;
        boolean arachne;
        boolean event;

        /** Geht die oeffnende Linie weg? (Bei den Events nicht, siehe oben) */
        boolean hidesOpening() {
            return powder || crystal || run || arachne;
        }

        boolean any() {
            return hidesOpening() || event;
        }
    }

    private static Candidates candidates(ChatTabCategory t, ChatPlace p) {
        ChatFilterTypesCategory c = t.filterTypes;
        Candidates k = new Candidates();
        k.event = c.eventLevelUp;
        if (c.powderEnabled && p.skyblock() && ChatPlace.isMining(p.area())) k.powder = true;
        if (c.nucleusEnabled && p.skyblock() && ChatPlace.isCrystalHollows(p.area())) {
            k.crystal = c.nucleusCrystalCollected;
            k.run = c.nucleusRunCompleted;
        }
        if (t.hideArachne && p.skyblock() && ChatPlace.isSpiderDen(p.area()) && !p.sanctuary()) k.arachne = true;
        return k;
    }

    /**
     * Eine Zeile. null heisst: Der Block-Zustand hat dazu nichts zu sagen, die anderen Filter sind dran.
     *
     * @param now die Uhr in Millisekunden
     */
    ChatVerdict handle(String line, ChatTabCategory t, ChatPlace p, long now) {
        if (type != Type.NONE && (now - openedAt > MAX_BLOCK_MS || lines > MAX_LINES)) reset();
        boolean wrapper = isWrapper(line);

        if (type == Type.NONE) {
            if (!wrapper) return null;
            Candidates k = candidates(t, p);
            if (!k.any()) return null;
            type = Type.PENDING;
            openedAt = now;
            lines = 0;
            return k.hidesOpening() ? ChatVerdict.HIDE : null;
        }

        lines++;
        if (wrapper) return closing(t, p);
        if (type == Type.PENDING) {
            Candidates k = candidates(t, p);
            if (line.isBlank()) return k.hidesOpening() ? ChatVerdict.HIDE : null;
            type = classify(line, k);
        }
        return inside(line, t);
    }

    /** Was der Block ist, nach seiner ersten Zeile mit Inhalt */
    private Type classify(String line, Candidates k) {
        if (CrystalNucleusFilter.isCrystalCount(line)) return k.crystal ? Type.CRYSTAL : Type.FOREIGN;
        if (PowderMiningFilter.isBlockHeader(line)) return k.powder ? Type.POWDER : Type.FOREIGN;
        if (line.trim().equals("ARACHNE DOWN!")) return k.arachne ? Type.ARACHNE : Type.FOREIGN;
        if (ChatRule.any(GeneralChatRules.EVENT_LEVEL_UP, line)) return k.event ? Type.EVENT : Type.FOREIGN;
        return k.run ? Type.RUN : Type.FOREIGN;
    }

    /** Die schliessende Linie */
    private ChatVerdict closing(ChatTabCategory t, ChatPlace p) {
        Type was = type;
        reset();
        ChatFilterTypesCategory c = t.filterTypes;
        switch (was) {
            case POWDER:
                return c.powderEnabled ? ChatVerdict.HIDE : null;
            case CRYSTAL:
                return c.nucleusEnabled && c.nucleusCrystalCollected ? ChatVerdict.HIDE : null;
            case RUN:
                return c.nucleusEnabled && c.nucleusRunCompleted ? ChatVerdict.HIDE : null;
            case ARACHNE:
                return t.hideArachne ? ChatVerdict.HIDE : null;
            case EVENT:
                return c.eventLevelUp ? ChatVerdict.HIDE : null;
            case PENDING:
                // Zwei Linien hintereinander: wie die oeffnende
                return candidates(t, p).hidesOpening() ? ChatVerdict.HIDE : null;
            default:
                return null;
        }
    }

    /** Eine Zeile im Block, je nach Art */
    private ChatVerdict inside(String line, ChatTabCategory t) {
        ChatFilterTypesCategory c = t.filterTypes;
        switch (type) {
            case POWDER:
                return c.powderEnabled ? PowderMiningFilter.inside(line, c) : null;
            case CRYSTAL:
                return c.nucleusEnabled && c.nucleusCrystalCollected ? nucleus.crystalCollectedLine(line) : null;
            case RUN:
                return c.nucleusEnabled && c.nucleusRunCompleted ? nucleus.runCompletedLine(line) : null;
            case ARACHNE:
                return t.hideArachne ? ChatVerdict.HIDE : null;
            case EVENT:
                return c.eventLevelUp && ChatRule.any(GeneralChatRules.EVENT_LEVEL_UP, line) ? ChatVerdict.HIDE : null;
            default:
                return null;
        }
    }
}
