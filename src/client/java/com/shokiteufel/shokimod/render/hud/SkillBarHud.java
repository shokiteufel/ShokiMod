package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ColourText;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SkillBarCategory;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory.SkillInfo;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.SkillProgress;
import com.shokiteufel.shokimod.util.SkillLevels.Skill;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Der Balken zum Fortschritt der Faehigkeit.
 *
 * Herkunft der Idee: SkyHanni ("Skill Progress > Progress Bar"): Ein Balken, der sich mit dem Fortschritt zur naechsten Stufe fuellt, in einer
 * einstellbaren Farbe oder im Regenbogen (Chroma), mit einstellbarer Breite und Hoehe. Mit "Overflow > Progress bar" zeigt er den Fortschritt
 * zur naechsten Stufe ueber der Obergrenze. Er steht nur da, solange auch die Zeile darueber ({@link SkillProgressHud}) da ist; im HUD-Editor
 * immer, mit einem halbvollen Balken.
 *
 * Er ist ein Kasten des HUD-Editors wie die anderen (Lage und Groesse), nur dass er seine Flaeche selbst zeichnet; der Untergrund-Regler
 * gilt hier nicht. Der Regenbogen laeuft mit dem Tick (SafariHud baut den Kasten dann mit jedem Tick neu), nicht mit der Uhr im Zeichnen: Das
 * Zeichnen liest nur fertige Zahlen.
 */
public final class SkillBarHud {

    /** Der Rand um den Balken und der leere Teil dahinter */
    private static final int FRAME = 0xFF000000;
    private static final int EMPTY = 0xFF3A3A3A;
    /** So viele Stuecke hat ein Balken im Regenbogen (je Stueck ein Farbton) - nicht je Bildpunkt, das waeren bis tausend Aufrufe je Bild */
    static final int CHROMA_SEGMENTS = 48;
    /** Ein voller Lauf durch den Regenbogen dauert so viele Ticks (sechs Sekunden) */
    static final int CHROMA_PERIOD_TICKS = 120;

    private SkillBarHud() {
    }

    /** Soll der Balken jetzt gezeichnet werden? Die Zeile darueber muss da sein und der Balken eingeschaltet */
    public static boolean shownNow() {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.skillProgress.enabled || !c.skillProgress.bar.enabled) return false;
        return SkillProgressHud.hasContent(System.currentTimeMillis()) || HudEditorScreen.isOpen();
    }

    /** Der Anteil, den der Balken zeigt: im Editor ohne gelesenen Stand die Haelfte */
    static double fraction() {
        SkillProgressCategory c = ModConfig.INSTANCE.skillProgress;
        Skill skill = SkillProgress.activeSkill();
        SkillInfo info = skill == null ? null : SkillProgress.infoOf(skill);
        if (info == null) return 0.5;
        return SkillProgress.barFraction(info, c.overflow.inProgressBar);
    }

    /** Die Farbe eines Stuecks des Regenbogens: der Farbton laeuft ueber die Breite und mit den Ticks */
    static int chromaColour(int segment, int segments, long tick) {
        float hue = ((float) segment / segments + (tick % CHROMA_PERIOD_TICKS) / (float) CHROMA_PERIOD_TICKS) % 1.0f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0xFFFFFF);
    }

    public static HudPanel build() {
        SkillBarCategory bar = ModConfig.INSTANCE.skillProgress.bar;
        int[] palette = null;
        if (bar.chroma) {
            long tick = SkillProgress.tickTime();
            palette = new int[CHROMA_SEGMENTS];
            for (int s = 0; s < CHROMA_SEGMENTS; s++) palette[s] = chromaColour(s, CHROMA_SEGMENTS, tick);
        }
        return new BarPanel(fraction(), bar.width, bar.height, 0xFF000000 | ColourText.rgb(bar.colour, 0xFF0000), palette);
    }

    /** Ein Kasten, der keine Zeilen hat, sondern einen Balken: er misst und zeichnet sich selbst */
    private static final class BarPanel extends HudPanel {

        private final double fraction;
        private final int barWidth;
        private final int barHeight;
        private final int colour;
        /** Die Farben der Stuecke im Regenbogen, oder null fuer eine feste Farbe */
        private final int[] palette;

        BarPanel(double fraction, int barWidth, int barHeight, int colour, int[] palette) {
            this.fraction = fraction;
            this.barWidth = Math.max(1, barWidth);
            this.barHeight = Math.max(1, barHeight);
            this.colour = colour;
            this.palette = palette;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public int width(Font font) {
            return barWidth;
        }

        @Override
        public int height() {
            return barHeight;
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, Font font, int left, int top) {
            draw(graphics, left, top);
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, Font font, int left, int top, float alpha) {
            draw(graphics, left, top);
        }

        private void draw(GuiGraphicsExtractor graphics, int left, int top) {
            int right = left + barWidth;
            int bottom = top + barHeight;
            graphics.fill(left, top, right, bottom, FRAME);
            // Der Rand ist einen Bildpunkt breit; bei einem sehr flachen Balken bleibt trotzdem etwas zum Fuellen
            int inLeft = left + 1;
            int inTop = top + 1;
            int inRight = right - 1;
            int inBottom = bottom - 1;
            if (inRight <= inLeft || inBottom <= inTop) return;
            graphics.fill(inLeft, inTop, inRight, inBottom, EMPTY);
            int inner = inRight - inLeft;
            int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * inner);
            if (filled <= 0) return;
            if (palette == null) {
                graphics.fill(inLeft, inTop, inLeft + filled, inBottom, colour);
                return;
            }
            for (int s = 0; s < palette.length; s++) {
                int from = inLeft + (int) ((long) s * inner / palette.length);
                int to = inLeft + (int) ((long) (s + 1) * inner / palette.length);
                if (from >= inLeft + filled) break;
                graphics.fill(from, inTop, Math.min(to, inLeft + filled), inBottom, palette[s]);
            }
        }
    }
}
