package com.shokiteufel.shokimod.data;

/** Wo der Spieler gerade ist. Grundlage für den Ortsfilter der Chatregeln. */
public class GameState {

    public static class Server {
        // id/map はタブリストの "Server: " / "Area: " 行、gametype はサイドバーのタイトルから取得する。
        // 取得元が毎tick読めるため、locraw のように参加時1回きりで取りこぼす心配がない
        public static String id = "Unknown";
        public static String gametype = "Unknown";
        public static String map = "Unknown";

        public static void reset() {
            id = "Unknown";
            gametype = "Unknown";
            map = "Unknown";
        }

        // エリア判定はここに集約する。表示名(Area:行の値)が唯一の判定材料なので、
        // Hypixel側の名称が変わった場合もModConstantsの定数1箇所を直せば済むようにしておく
        public static boolean isSkyblock() {
            return ModConstants.GAME_TYPE_SKYBLOCK.equals(gametype);
        }

        public static boolean isSafari() {
            return ModConstants.MAP_SAFARI.equals(map);
        }

        public static boolean isMoongladeMarsh() {
            return ModConstants.MAP_MOONGLADE_MARSH.equals(map);
        }
    }

    public static void resetAll() {
        Server.reset();
    }
}
