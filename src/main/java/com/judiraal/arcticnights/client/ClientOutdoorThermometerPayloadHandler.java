package com.judiraal.arcticnights.client;

import com.judiraal.arcticnights.network.OutdoorThermometerPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientOutdoorThermometerPayloadHandler {
    private ClientOutdoorThermometerPayloadHandler() {
    }

    public static void handle(OutdoorThermometerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOutdoorThermometer.apply(payload));
    }
}
