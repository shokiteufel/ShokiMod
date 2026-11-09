package com.shokiteufel.shokimod.verkehr;

import java.util.Arrays;
import java.util.UUID;

/** Ergebnis der Einordnung eines Pakets: Beziehung, Basis und die gefundenen Subjekte. */
public record Einordnung(Beziehung beziehung, Basis basis, int[] ids, UUID[] uuids) {
    @Override
    public String toString() {
        return beziehung + "/" + basis + Arrays.toString(ids) + Arrays.toString(uuids);
    }
}
