package com.lx862.mtrsurveyor;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.TransportMode;
import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.com.google.gson.JsonObject;

class ProbeTest {
    @Test
    void probe() {
        final ClientData data = new ClientData();
        final Platform platform = new Platform(new Position(0, 64, 0), new Position(100, 64, 0), TransportMode.TRAIN, data);
        System.out.println("[PROBE] default toString: " + platform);
        System.out.println("[PROBE] default id: " + platform.getId());

        final JsonObject json = new JsonObject();
        json.addProperty("id", 777);
        json.addProperty("name", "Probe");
        json.addProperty("color", 7829367);
        platform.updateData(new JsonReader(json));
        System.out.println("[PROBE] after updateData id=" + platform.getId() + " name=" + platform.getName());
        System.out.println("[PROBE] serialized: " + platform);
    }
}
