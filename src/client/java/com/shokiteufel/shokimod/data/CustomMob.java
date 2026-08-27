package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.item.DyeColor;

import java.util.List;
import java.util.Locale;

/**
 * ユーザーが自分で追加したモブ。
 *
 * MobVisual を実装しているので、エリアごとの enum と同じ経路で
 * Highlight / Tracer / Nameplate に流れる。
 *
 * 判定は2通り。
 *   NAME … ネームタグの部分一致。体力表示が変わり続けるので完全一致にはしない
 *   TYPE … エンティティ型。Critter Safari のように名前を持たないモブ用
 */
public class CustomMob implements MobVisual {

    public static final int DEFAULT_COLOR = 0xFF5555;

    public enum Mode {
        NAME,
        TYPE
    }

    @Expose
    public Mode mode = Mode.NAME;

    /** NAME モードの検索語 */
    @Expose
    public String pattern = "";

    /** TYPE モードの対象。EntityType.getDescriptionId() をそのまま持つ */
    @Expose
    public String typeId = "";

    /** TYPE モードで、透明な個体だけを狙うか。Hypixel の当たり判定モブがこれにあたる */
    @Expose
    public boolean invisibleOnly = false;

    /**
     * TYPE モードの追加条件。同じ型でも見分けが要る場合に使う。
     * 現状は TropicalFish の色(DyeColor 名)。空なら型だけで判定する。
     * Hypixel は Timil / Ember / Solar を同じ熱帯魚の色違いで作っているため、
     * これが無いと3種をまとめて拾ってしまう。
     */
    @Expose
    public String variant = "";

    /** 一覧に出す見出し。ユーザーが自由に書き換えられる */
    @Expose
    public String label = "";

    /**
     * 取り込んだ時点での既定の呼び名。ShokiMod が判別できた場合はその呼び名が入る。
     * 「Default」ボタンで label をここへ戻すために保持する。
     */
    @Expose
    public String defaultLabel = "";

    @Expose
    public int color = DEFAULT_COLOR;

    @Expose
    public boolean enabled = true;

    public CustomMob() {
    }

    public static CustomMob byName(String pattern, int color) {
        CustomMob m = new CustomMob();
        m.mode = Mode.NAME;
        m.pattern = pattern;
        m.label = pattern;
        m.defaultLabel = pattern;
        m.color = color;
        return m;
    }

    public static CustomMob byType(String typeId, String label, boolean invisibleOnly,
                                   String variant, int color) {
        CustomMob m = new CustomMob();
        m.mode = Mode.TYPE;
        m.typeId = typeId;
        m.label = label;
        m.invisibleOnly = invisibleOnly;
        m.variant = variant == null ? "" : variant;
        m.defaultLabel = label == null ? "" : label;
        m.color = color;
        return m;
    }

    /**
     * 追加条件の取り出し。ShokiMod 本体と同じく熱帯魚は色で見分ける。
     * 対象外の型では空を返し、型だけの判定になる。
     */
    public static String variantOf(Entity entity) {
        if (entity instanceof TropicalFish fish) {
            DyeColor base = fish.getBaseColor();
            if (base != null) return base.name();
        }
        return "";
    }

    @Override
    public String label() {
        if (label != null && !label.isEmpty()) return label;
        return fallbackLabel();
    }

    /** 「Default」ボタンで戻す先。取り込み時の呼び名、無ければ検索条件そのもの */
    public String fallbackLabel() {
        if (defaultLabel != null && !defaultLabel.isEmpty()) return defaultLabel;
        return mode == Mode.TYPE ? typeId : pattern;
    }

    @Override
    public int glowColorRGB() {
        return color;
    }

    // 個別のオン・オフはリストから外さずに切り替えたいので、機能側の判定に条件を足す
    @Override
    public boolean highlight() {
        return enabled && MobVisual.super.highlight();
    }

    @Override
    public boolean tracer() {
        return enabled && MobVisual.super.tracer();
    }

    @Override
    public boolean nameplate() {
        return enabled && MobVisual.super.nameplate();
    }

    public boolean isUsable() {
        if (!enabled) return false;
        return mode == Mode.TYPE
                ? typeId != null && !typeId.isEmpty()
                : pattern != null && !normalize(pattern).isEmpty();
    }

    public boolean isNameMode() {
        return mode != Mode.TYPE;
    }

    /**
     * 比較用の正規化。
     *
     * Hypixel はリソースパックのアイコンを私用領域の文字として名前に混ぜてくる。
     * 空白ではないので trim() では落ちず、そのまま保存すると永久に一致しない。
     *
     * 範囲を手で書くと BMP の U+E000-U+F8FF しか拾えないため、Unicode カテゴリで指定する。
     * Co = 私用領域(補助面も含む)、Cf = 書式用の不可視文字。
     * この書き方は crittermod の SafariLocation.strip() から借りた。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("§.", "")
                .replaceAll("[\\p{Cf}\\p{Co}]", "")
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * ネームタグから安定した部分だけを取り出す。
     * 例: "[Lv1] Graveyard Zombie 100/100❤" -> "Graveyard Zombie"
     */
    public static String cleanPattern(String rawName) {
        String s = normalize(rawName);
        s = s.replaceAll("^\\s*\\[[^\\]]*\\]\\s*", "");
        s = s.replaceAll("\\s*[\\d.,]+\\s*/\\s*[\\d.,]+.*$", "");
        s = s.replaceAll("\\s*[\\d.,]+[kKmM]?\\s*[❤♥].*$", "");
        s = s.replaceAll("[❤♥]", "");
        return s.trim();
    }

    /** NAME モードの照合。両辺を同じ土俵に載せてから比べる */
    public boolean matches(String rawName) {
        String needle = normalize(pattern);
        if (needle.isEmpty()) {
            return false;
        }
        return normalize(rawName).toLowerCase(Locale.ROOT)
                .contains(needle.toLowerCase(Locale.ROOT));
    }

    /** TYPE モードの照合。型・透明条件・追加条件をすべて満たすか */
    public boolean matchesType(Entity entity) {
        if (typeId == null || typeId.isEmpty()) return false;
        if (!typeId.equals(entity.getType().getDescriptionId())) return false;
        if (invisibleOnly && !entity.isInvisible()) return false;
        if (variant == null || variant.isEmpty()) return true;

        // 熱帯魚は本体色と模様色のどちらかが合えば同じ種とみなす(ShokiMod 本体と同じ扱い)
        if (entity instanceof TropicalFish fish) {
            return matchesColor(fish.getBaseColor()) || matchesColor(fish.getPatternColor());
        }
        return variant.equals(variantOf(entity));
    }

    private boolean matchesColor(DyeColor color) {
        return color != null && variant.equalsIgnoreCase(color.name());
    }
}
