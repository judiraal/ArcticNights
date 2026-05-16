package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.CaveBiomeTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Mode;
import com.momosoftworks.coldsweat.api.util.placement.Order;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;

@ConditionalEventBusSubscriber(dependencies = {"cold_sweat"})
public final class ColdSweatCompat {
    public static final ResourceLocation OUTDOOR_CLIMATE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "outdoor_climate");

    private ColdSweatCompat() {
    }

    @SubscribeEvent
    public static void registerTempModifiers(TempModifierRegisterEvent event) {
        event.register(OUTDOOR_CLIMATE_MODIFIER, OutdoorClimateTempModifier::new);
    }

    @SubscribeEvent
    public static void addDefaultModifiers(DefaultTempModifiersEvent event) {
        Placement beforeBlock = Placement
                .of(Mode.ADD_BEFORE, Order.FIRST, modifier -> modifier instanceof BlockTempModifier)
                .orElse(Placement.LAST);
        Placement placement = Placement
                .of(Mode.ADD_BEFORE, Order.FIRST, modifier -> modifier instanceof CaveBiomeTempModifier)
                .orElse(beforeBlock)
                .noDuplicates(Matcher.SAME_CLASS);

        event.addModifierById(
                Temperature.Trait.WORLD,
                OUTDOOR_CLIMATE_MODIFIER,
                modifier -> modifier.tickRate(20),
                placement
        );
    }
}
