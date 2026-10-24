package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Schaltet die Ansicht von vorn ab - nach Odins "Camera".
 *
 * Herkunft: Odin (BSD-3-Clause, Odtheking), {@code features/impl/render/Camera.kt}. Dieselbe Regel: Steht die Kamera am Ende eines
 * Ticks auf der dritten Person von vorn, kommt sie zurueck in die erste Person. Der Tastendruck (F5) schaltet im Spiel in einer
 * Reihe weiter - erste Person, dritte Person von hinten, dritte Person von vorn -, und der Tick laeuft vor dem Zeichnen; die Ansicht
 * von vorn wird deshalb nie gezeichnet, und der naechste Druck faengt wieder bei der von hinten an.
 *
 * Kostet nichts, solange die Funktion aus ist: ein Schalter, mehr wird nicht gelesen.
 */
public final class FrontCamera {

    private FrontCamera() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FrontCamera::tick);
    }

    private static void tick(Minecraft client) {
        if (!ModConfig.INSTANCE.odin.noFrontCamera) return;
        if (client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }
}
