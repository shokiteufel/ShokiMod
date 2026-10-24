package com.shokiteufel.shokimod.handler;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

/**
 * Die Felder eines Partikel-Pakets, an einer einzigen Stelle gelesen.
 *
 * Das Paket heisst seine Felder je nach Minecraft-Version anders (auf 26.1 und 26.2 getX(), auf 26.3 x() - ein Record).
 * Statt das in jeder Klasse zu wissen, die ein Paket liest, steht es nur hier: Auf jedem Zweig ist diese Datei die
 * einzige, die sich unterscheidet, und die Uebernahme einer neuen Version auf einen anderen Zweig kollidiert nicht mehr
 * in den Klassen, die das Paket auswerten.
 */
public final class ParticlePacketView {

    private ParticlePacketView() {
    }

    public static double x(ClientboundLevelParticlesPacket packet) {
        return packet.getX();
    }

    public static double y(ClientboundLevelParticlesPacket packet) {
        return packet.getY();
    }

    public static double z(ClientboundLevelParticlesPacket packet) {
        return packet.getZ();
    }

    public static ParticleOptions particle(ClientboundLevelParticlesPacket packet) {
        return packet.getParticle();
    }

    public static int count(ClientboundLevelParticlesPacket packet) {
        return packet.getCount();
    }

    public static double maxSpeed(ClientboundLevelParticlesPacket packet) {
        return packet.getMaxSpeed();
    }

    public static double xDist(ClientboundLevelParticlesPacket packet) {
        return packet.getXDist();
    }

    public static double yDist(ClientboundLevelParticlesPacket packet) {
        return packet.getYDist();
    }

    public static double zDist(ClientboundLevelParticlesPacket packet) {
        return packet.getZDist();
    }

    public static boolean overrideLimiter(ClientboundLevelParticlesPacket packet) {
        return packet.isOverrideLimiter();
    }
}
