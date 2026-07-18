package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Mode;
import com.momosoftworks.coldsweat.api.util.placement.Order;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import com.momosoftworks.coldsweat.compat.CompatManager;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.momosoftworks.coldsweat.core.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;

@ConditionalEventBusSubscriber(dependencies = {"cold_sweat"})
public final class ColdSweatCompat {
    public static final ResourceLocation OUTDOOR_CLIMATE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "outdoor_climate");
    public static final ResourceLocation BLOCK_TEMP_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "nearby_blocks");

    private ColdSweatCompat() {
    }

    @SubscribeEvent
    public static void registerTempModifiers(TempModifierRegisterEvent event) {
        event.register(OUTDOOR_CLIMATE_MODIFIER, OutdoorClimateTempModifier::new);
        event.register(BLOCK_TEMP_MODIFIER, ArcticBlockTempModifier::new);
    }

    @SubscribeEvent
    public static void addDefaultModifiers(DefaultTempModifiersEvent event) {
        event.removeModifiers(Temperature.Trait.WORLD, modifier ->
                OUTDOOR_CLIMATE_MODIFIER.equals(modifier.getID()) || modifier instanceof OutdoorClimateTempModifier
                        || BLOCK_TEMP_MODIFIER.equals(modifier.getID()) || modifier instanceof ArcticBlockTempModifier);
        if (!ArcticNightsFeatures.climateSystem()) return;
        // Keep Cold Sweat subclasses such as its Sable sublevel scanner; only replace the normal world scanner.
        event.removeModifiers(Temperature.Trait.WORLD, modifier -> modifier.getClass() == BlockTempModifier.class);

        Placement outdoorPlacement = Placement
                .of(Mode.ADD_AFTER, Order.FIRST, modifier -> modifier instanceof BiomeTempModifier)
                .orElse(Placement.FIRST)
                .noDuplicates(Matcher.SAME_CLASS);
        Placement blockPlacement = Placement
                .of(Mode.ADD_AFTER, Order.LAST, modifier -> modifier instanceof OutdoorClimateTempModifier)
                .orElse(Placement.LAST)
                .noDuplicates(Matcher.SAME_CLASS);

        event.addModifierById(
                Temperature.Trait.WORLD,
                OUTDOOR_CLIMATE_MODIFIER,
                modifier -> modifier.tickRate(20),
                outdoorPlacement
        );
        event.addModifierById(
                Temperature.Trait.WORLD,
                BLOCK_TEMP_MODIFIER,
                modifier -> modifier.tickRate(20),
                blockPlacement
        );
    }

    public static boolean showsAdvancedWorldTemperature(Player player) {
        if (!ConfigSettings.REQUIRE_THERMOMETER.get() || player.isCreative()) return true;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().items.get(slot).getItem() == ModItems.THERMOMETER.value()) return true;
        }
        return player.getOffhandItem().getItem() == ModItems.THERMOMETER.value()
                || CompatManager.Curios.hasCurio(player, ModItems.THERMOMETER.value());
    }
}
