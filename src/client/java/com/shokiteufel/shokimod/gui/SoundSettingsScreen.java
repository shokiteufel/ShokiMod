package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.SoundVolumes;
import com.shokiteufel.shokimod.util.TextCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Sound Settings (Alerts): die Lautstaerke jeder Datei im Sound-Ordner, einzeln.
 *
 * Eine Zeile je Datei: Name, Regler von 0 bis 100, ein Knopf zum Anhoeren, ein Knopf zum Zuruecksetzen. Die Lautstaerke gehoert zur Datei
 * und gilt, wo immer sie gespielt wird (SoundVolumes, CustomSoundPlayer.play) - zusaetzlich zum Alert Volume und zur Lautstaerke der Regel.
 * Gespeichert wird beim Schliessen.
 */
public class SoundSettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 56;
    private static final int SLIDER_WIDTH = 150;
    private static final int PLAY_WIDTH = 22;
    private static final int RESET_WIDTH = 40;
    private static final int GAP = 4;
    private static final int MAX_LABEL = 200;
    /** So schmal wird der Name hoechstens: Die Zeile darunter (Knoepfe) braucht gut 350 Punkte und darf sich nicht ueberlappen */
    private static final int MIN_LABEL = 126;

    /** So oft wird der Ordner im Hintergrund hoechstens neu gelesen, damit eine Datei, die man hineinlegt, in der Zeile oben auftaucht */
    private static final long NEU_LESEN_NANOS = 1_000_000_000L;

    private final Screen parent;
    private int page;

    /**
     * Die Dateien im Sound-Ordner, einmal gelesen und gehalten. availableSounds() oeffnet das Verzeichnis, filtert und sortiert bei jedem
     * Aufruf - und das Fenster fragte es in jedem Bild mehrfach (Seitenzahl, Zaehler, Seite). Neu gelesen wird bei jedem init() (Neuaufbau,
     * "Rescan", "Reset") und danach hoechstens einmal je Sekunde, denn der Ordner kann sich von aussen aendern (der Knopf "Open folder"
     * fuehrt dorthin).
     */
    private List<String> dateien = List.of();
    private long dateienGelesen;
    private boolean dateienDa;

    // Die Zeilen der gezeigten Seite: gekuerzte Namen (Textbreite messen und kuerzen) und die Farbe "von 100 abweichend". Neu gebaut, wenn eine
    // andere Dateiliste, Seite, Zeilenzahl, Namensbreite oder Schrift gilt; die Farben zusaetzlich, wenn ein Regler bewegt wird.
    private List<String> seitenDateien = List.of();
    private String[] seitenNamen = new String[0];
    private boolean[] seitenGeaendert = new boolean[0];
    private List<String> seiteQuelle;
    private int seiteNummer = -1;
    private int seiteZeilen = -1;
    private int seiteBreite = -1;
    private Font seiteSchrift;

    // Die Zeilen oben: gebaut nur, wenn sich die Zahl der Dateien, die Fensterbreite oder die Schrift aendert
    private final FensterText.Gemerkt kopf = new FensterText.Gemerkt();
    private final FensterText.Gemerkt hinweis = new FensterText.Gemerkt();

    public SoundSettingsScreen(Screen parent) {
        super(Component.literal("Sound settings"));
        this.parent = parent;
    }

    /** Den Ordner jetzt lesen. Bleibt der Inhalt gleich, bleibt auch dasselbe Listenobjekt, damit sich nichts grundlos neu baut */
    private void dateienLesen() {
        List<String> neu = CustomSoundPlayer.availableSounds();
        if (!dateienDa || !neu.equals(dateien)) dateien = neu;
        dateienGelesen = System.nanoTime();
        dateienDa = true;
    }

    private List<String> soundDateien() {
        if (!dateienDa || System.nanoTime() - dateienGelesen > NEU_LESEN_NANOS) dateienLesen();
        return dateien;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (soundDateien().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    /** So breit wird der Dateiname; auf einem schmalen Fenster schrumpft er, der Rest der Zeile nicht */
    private int labelWidth() {
        int rest = SLIDER_WIDTH + PLAY_WIDTH + RESET_WIDTH + 3 * GAP;
        return Math.max(MIN_LABEL, Math.min(MAX_LABEL, width - 40 - rest));
    }

    private int rowWidth() {
        return labelWidth() + GAP + SLIDER_WIDTH + GAP + PLAY_WIDTH + GAP + RESET_WIDTH;
    }

    private int left() {
        return width / 2 - rowWidth() / 2;
    }

    private List<String> pageFiles() {
        return seitenTeil(soundDateien());
    }

    /** Die Dateien der gezeigten Seite aus einer Dateiliste */
    private List<String> seitenTeil(List<String> files) {
        int from = Math.min(files.size(), page * rowsPerPage());
        int to = Math.min(files.size(), from + rowsPerPage());
        return files.subList(from, to);
    }

    /**
     * Die Zeilen der gezeigten Seite, fertig fuers Bild: gekuerzter Name und Farbe je Datei. Gerechnet wird nur, wenn sich die Dateiliste, die
     * Seite, die Zeilenzahl, die Breite des Namens oder die Schrift aendert - nicht in jedem Bild.
     */
    private void seiteAktualisieren() {
        List<String> quelle = soundDateien();
        int zeilen = rowsPerPage();
        int breite = labelWidth() - 4;
        if (quelle == seiteQuelle && page == seiteNummer && zeilen == seiteZeilen && breite == seiteBreite && font == seiteSchrift) {
            return;
        }
        List<String> teil = seitenTeil(quelle);
        String[] namen = new String[teil.size()];
        for (int i = 0; i < namen.length; i++) {
            namen[i] = fit(teil.get(i), breite);
        }
        seitenDateien = teil;
        seitenNamen = namen;
        seiteQuelle = quelle;
        seiteNummer = page;
        seiteZeilen = zeilen;
        seiteBreite = breite;
        seiteSchrift = font;
        geaendertAktualisieren();
    }

    /** Welche Dateien von 100 abweichen (gelb geschrieben): gelesen beim Neuaufbau der Seite und wenn ein Regler bewegt wird, nicht in jedem Bild */
    private void geaendertAktualisieren() {
        int n = seitenDateien.size();
        boolean[] g = seitenGeaendert.length == n ? seitenGeaendert : new boolean[n];
        for (int i = 0; i < n; i++) {
            g[i] = SoundVolumes.percent(seitenDateien.get(i)) < SoundVolumes.FULL;
        }
        seitenGeaendert = g;
    }

    /** Die zusammengesetzten Texte oben: gebaut nur, wenn sich die Zahl der Dateien, die Breite oder die Schrift aendert */
    private void texteAktualisieren(int count, int room) {
        long schluessel = count * 100_000L + room;
        if (!kopf.gilt(schluessel, font)) {
            kopf.setze(schluessel, font, Component.literal(
                            fit(count + " file(s) in config/shokimod/sounds  ·  wav, mp3, ogg", room))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!hinweis.gilt(room, font)) {
            hinweis.setze(room, font, Component.literal(
                            fit("Counts wherever a file is used, on top of Alert Volume", room))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void init() {
        // Jeder Neuaufbau (Rescan, Reset, Fenstergroesse) liest den Ordner und die Lautstaerken neu
        dateienLesen();
        seiteQuelle = null;
        page = Math.min(page, pageCount() - 1);

        int left = left();
        List<String> files = pageFiles();
        for (int i = 0; i < files.size(); i++) {
            String file = files.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left + labelWidth() + GAP;

            addRenderableWidget(new FileSlider(x, y, file, this::geaendertAktualisieren));

            Button play = Button.builder(Component.literal("▶"), b ->
                    // Wie die Datei als Alarm klingt: mit dem Alert Volume, Regel auf 100
                    CustomSoundPlayer.playAt(file, AlertVolume.factor() * SoundVolumes.factor(file),
                            CustomSoundPlayer.PREVIEW_CHANNEL)
            ).bounds(x + SLIDER_WIDTH + GAP, y, PLAY_WIDTH, 20).build();
            play.setTooltip(Tooltip.create(Component.literal("Hear it at this volume - with the Alert Volume, as a rule at 100")));
            addRenderableWidget(play);

            Button reset = Button.builder(Component.literal("Reset"), b -> {
                SoundVolumes.reset(file);
                rebuild();
            }).bounds(x + SLIDER_WIDTH + GAP + PLAY_WIDTH + GAP, y, RESET_WIDTH, 20).build();
            addRenderableWidget(reset);
        }

        int y = height - 30;
        int rowLeft = left;
        // Links Ordner, Neu lesen, Zuruecksetzen (zusammen 228 breit); rechts Blaettern und Fertig (zusammen 118) - bei der kleinsten
        // Zeilenbreite (MIN_LABEL) bleiben dazwischen noch gut 100 Punkte
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b ->
                net.minecraft.util.Util.getPlatform().openPath(CustomSoundPlayer.SOUND_DIRECTORY)
        ).bounds(rowLeft, y, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Rescan"), b -> rebuild())
                .bounds(rowLeft + 94, y, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
            SoundVolumes.resetAll();
            rebuild();
        }).bounds(rowLeft + 158, y, 70, 20).build());

        int right = rowLeft + rowWidth();
        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(right - 118, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(right - 94, y, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(right - 70, y, 70, 20).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    /**
     * Den Dateinamen auf die Breite kuerzen, mit "…" am Ende. Das Ergebnis haelt das Fenster fest (seiteAktualisieren, texteAktualisieren);
     * gerechnet wird also nur bei neuem Namen oder neuer Breite. Die erste Messung geht ueber den Zwischenspeicher, das schrittweise Kuerzen
     * misst direkt, damit die Zwischenschritte dessen Tabelle nicht fuellen.
     */
    private String fit(String name, int maxWidth) {
        if (TextCache.breite(font, name) <= maxWidth) return name;
        String cut = name;
        while (cut.length() > 1 && font.width(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 12, 0xFFFFFFFF);

        // Dateiliste, gekuerzte Namen und die beiden Zeilen oben liegen fertig vor und werden nur neu gebaut, wenn sich ihre Quellen aendern
        int count = soundDateien().size();
        texteAktualisieren(count, width - 20);
        graphics.centeredText(font, kopf.component(), centerX, 26, 0xFFAAAAAA);
        graphics.centeredText(font, hinweis.component(), centerX, 38, 0xFF808080);

        int left = left();
        seiteAktualisieren();
        String[] namen = seitenNamen;
        boolean[] geaendert = seitenGeaendert;
        for (int i = 0; i < namen.length; i++) {
            graphics.text(font, namen[i], left, LIST_TOP + i * ROW_HEIGHT + 6,
                    geaendert[i] ? 0xFFFFFF55 : 0xFFAAAAAA, false);
        }

        if (count == 0) {
            graphics.centeredText(font, FensterText.grau("Folder is empty - put a file in, then press Rescan"),
                    centerX, LIST_TOP + 14, 0xFFAAAAAA);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 46, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    /** Regler von 0 bis 100 fuer eine Datei; schreibt jede Bewegung in SoundVolumes */
    private static class FileSlider extends AbstractSliderButton {
        private final String file;
        /** Wird nach jeder Bewegung gerufen: die Zeile des Fensters merkt sich daraus, ob die Datei von 100 abweicht */
        private final Runnable danach;

        FileSlider(int x, int y, String file, Runnable danach) {
            super(x, y, SLIDER_WIDTH, 20, Component.empty(), SoundVolumes.percent(file) / 100.0);
            this.file = file;
            this.danach = danach;
            updateMessage();
        }

        private int percent() {
            return (int) Math.round(value * 100.0);
        }

        @Override
        protected void updateMessage() {
            int percent = percent();
            setMessage(Component.literal(percent == 0 ? "Muted" : percent + "%"));
        }

        @Override
        protected void applyValue() {
            SoundVolumes.set(file, percent());
            if (danach != null) danach.run();
        }
    }
}
