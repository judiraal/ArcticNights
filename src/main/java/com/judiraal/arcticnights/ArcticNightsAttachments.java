package com.judiraal.arcticnights;

import com.judiraal.arcticnights.util.ClimateTickrateChunkStamp;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ArcticNightsAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArcticNights.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ClimateTickrateChunkStamp>> TICKRATE_CATCHUP =
            ATTACHMENTS.register("tickrate_catchup", () -> AttachmentType.serializable(ClimateTickrateChunkStamp::new).build());

    private ArcticNightsAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
