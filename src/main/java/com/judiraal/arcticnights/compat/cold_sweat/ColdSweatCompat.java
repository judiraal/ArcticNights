package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.compat.ConditionalEventBusSubscriber;
import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.CaveBiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@ConditionalEventBusSubscriber(dependencies = {"cold_sweat"})
public final class ColdSweatCompat {
    public static final ResourceLocation OUTDOOR_CLIMATE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "outdoor_climate");
    public static final ResourceLocation BLOCK_TEMP_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "nearby_blocks");
    public static final ResourceLocation CAVE_TARGET_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, "cave_target");

    private ColdSweatCompat() {
    }

    @SubscribeEvent
    public static void registerTempModifiers(TempModifierRegisterEvent event) {
        event.register(OUTDOOR_CLIMATE_MODIFIER, OutdoorClimateTempModifier::new);
        event.register(CAVE_TARGET_MODIFIER, CaveTargetTempModifier::new);
        event.register(BLOCK_TEMP_MODIFIER, ArcticBlockTempModifier::new);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void addDefaultModifiers(DefaultTempModifiersEvent event) {
        event.removeModifiers(Temperature.Trait.WORLD, modifier ->
                OUTDOOR_CLIMATE_MODIFIER.equals(modifier.getID()) || modifier instanceof OutdoorClimateTempModifier
                        || CAVE_TARGET_MODIFIER.equals(modifier.getID()) || modifier instanceof CaveTargetTempModifier
                        || BLOCK_TEMP_MODIFIER.equals(modifier.getID()) || modifier instanceof ArcticBlockTempModifier);
        if (!ArcticNightsFeatures.climateSystem()) return;

        Placement outdoorPlacement = Placement
                .of(Mode.ADD_AFTER, Order.FIRST, modifier -> modifier instanceof BiomeTempModifier)
                .orElse(Placement.FIRST)
                .noDuplicates(Matcher.SAME_CLASS);

        event.addModifierById(
                Temperature.Trait.WORLD,
                OUTDOOR_CLIMATE_MODIFIER,
                modifier -> modifier.tickRate(20),
                outdoorPlacement
        );
        event.addModifierById(
                Temperature.Trait.WORLD,
                CAVE_TARGET_MODIFIER,
                modifier -> modifier.tickRate(20),
                caveResponsePlacement()
        );
        event.addModifierById(
                Temperature.Trait.WORLD,
                BLOCK_TEMP_MODIFIER,
                modifier -> modifier.tickRate(20),
                blockTemperaturePlacement()
        );
        // Insert first so the replacement inherits Cold Sweat's original position. Removing first caused cave and
        // elevation modifiers to run after nearby blocks, erasing local lava and ice temperatures.
        // Keep subclasses such as Cold Sweat's Sable sublevel scanner; only replace the normal world scanner.
        event.removeModifiers(Temperature.Trait.WORLD, modifier -> modifier.getClass() == BlockTempModifier.class);
    }

    static Placement blockTemperaturePlacement() {
        return Placement
                .of(Mode.ADD_BEFORE, Order.FIRST, modifier -> modifier.getClass() == BlockTempModifier.class)
                .orElse(Placement
                        .of(Mode.ADD_BEFORE, Order.FIRST, modifier -> modifier instanceof BlockTempModifier)
                        .orElse(Placement.LAST))
                .noDuplicates(Matcher.SAME_CLASS);
    }

    static Placement caveResponsePlacement() {
        return Placement
                .of(Mode.ADD_AFTER, Order.LAST, modifier -> modifier instanceof CaveBiomeTempModifier)
                .orElse(Placement
                        .of(Mode.ADD_AFTER, Order.LAST, modifier -> modifier instanceof ElevationTempModifier)
                        .orElse(Placement
                                .of(Mode.ADD_BEFORE, Order.FIRST, modifier -> modifier instanceof BlockTempModifier)
                                .orElse(Placement.LAST)))
                .noDuplicates(Matcher.SAME_CLASS);
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
