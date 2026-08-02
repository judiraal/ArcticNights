package com.judiraal.arcticnights.compat.cold_sweat;

import com.judiraal.arcticnights.test.FixtureSupport;
import com.momosoftworks.coldsweat.api.event.core.init.DefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.BiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.BlockTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.CaveBiomeTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.ElevationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.EntitiesTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.InventoryItemsTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.SublevelBlockTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ColdSweatModifierOrderFixture {
    private ColdSweatModifierOrderFixture() {
    }

    public static void run() {
        checkReplacementKeepsColdSweatSlot();
        checkCaveResponseFallbacksStayBeforeLocalSources();
        checkFallbackPrecedesSpecializedBlockScanner();
        checkHandlerRunsAfterDefaultContributors();
    }

    private static void checkReplacementKeepsColdSweatSlot() {
        List<TempModifier> modifiers = new ArrayList<>(List.of(
                new BiomeTempModifier(),
                new ElevationTempModifier(),
                new CaveBiomeTempModifier(),
                new BlockTempModifier(),
                new SublevelBlockTempModifier(),
                new EntitiesTempModifier(),
                new InventoryItemsTempModifier()
        ));

        CaveTargetTempModifier caveResponse = new CaveTargetTempModifier();
        boolean caveResponseAdded = Temperature.addModifier(
                modifiers,
                caveResponse,
                ColdSweatCompat.caveResponsePlacement(),
                null,
                null
        );
        ArcticBlockTempModifier replacement = new ArcticBlockTempModifier();
        boolean added = Temperature.addModifier(
                modifiers,
                replacement,
                ColdSweatCompat.blockTemperaturePlacement(),
                null,
                null
        );
        modifiers.removeIf(modifier -> modifier.getClass() == BlockTempModifier.class);

        FixtureSupport.check(caveResponseAdded, "Arctic cave response should be inserted");
        FixtureSupport.check(
                modifiers.indexOf(caveResponse) == 3,
                "Arctic cave response should run after Cold Sweat's cave-biome blend"
        );
        FixtureSupport.check(added, "Arctic block-temperature replacement should be inserted");
        FixtureSupport.check(
                modifiers.indexOf(replacement) == 4,
                "Arctic block-temperature replacement should retain Cold Sweat's original slot"
        );
        FixtureSupport.check(
                modifiers.get(5) instanceof SublevelBlockTempModifier,
                "Sable sublevel block-temperature modifier should remain after the normal-world replacement"
        );
    }

    private static void checkCaveResponseFallbacksStayBeforeLocalSources() {
        List<TempModifier> depthOnlyModifiers = new ArrayList<>(List.of(
                new BiomeTempModifier(),
                new ElevationTempModifier(),
                new BlockTempModifier(),
                new EntitiesTempModifier()
        ));
        CaveTargetTempModifier afterDepth = new CaveTargetTempModifier();
        boolean addedAfterDepth = Temperature.addModifier(
                depthOnlyModifiers,
                afterDepth,
                ColdSweatCompat.caveResponsePlacement(),
                null,
                null
        );
        FixtureSupport.check(addedAfterDepth, "Arctic cave response should fall back after elevation");
        FixtureSupport.check(
                depthOnlyModifiers.indexOf(afterDepth) == 2,
                "Arctic cave response should follow elevation when Cold Sweat has no cave-biome modifier"
        );

        List<TempModifier> localOnlyModifiers = new ArrayList<>(List.of(
                new BiomeTempModifier(),
                new BlockTempModifier(),
                new EntitiesTempModifier()
        ));
        CaveTargetTempModifier beforeBlocks = new CaveTargetTempModifier();
        boolean addedBeforeBlocks = Temperature.addModifier(
                localOnlyModifiers,
                beforeBlocks,
                ColdSweatCompat.caveResponsePlacement(),
                null,
                null
        );
        FixtureSupport.check(addedBeforeBlocks, "Arctic cave response should fall back before block sources");
        FixtureSupport.check(
                localOnlyModifiers.indexOf(beforeBlocks) == 1,
                "Arctic cave response should precede local block sources when depth modifiers are absent"
        );
    }

    private static void checkFallbackPrecedesSpecializedBlockScanner() {
        List<TempModifier> modifiers = new ArrayList<>(List.of(
                new CaveBiomeTempModifier(),
                new SublevelBlockTempModifier(),
                new EntitiesTempModifier()
        ));

        ArcticBlockTempModifier replacement = new ArcticBlockTempModifier();
        boolean added = Temperature.addModifier(
                modifiers,
                replacement,
                ColdSweatCompat.blockTemperaturePlacement(),
                null,
                null
        );

        FixtureSupport.check(added, "Arctic block-temperature fallback should be inserted");
        FixtureSupport.check(
                modifiers.indexOf(replacement) == 1,
                "Arctic block-temperature fallback should run before specialized block scanners"
        );
    }

    private static void checkHandlerRunsAfterDefaultContributors() {
        try {
            Method handler = ColdSweatCompat.class.getDeclaredMethod(
                    "addDefaultModifiers",
                    DefaultTempModifiersEvent.class
            );
            SubscribeEvent subscription = handler.getAnnotation(SubscribeEvent.class);
            FixtureSupport.check(subscription != null, "Cold Sweat compatibility handler should remain subscribed");
            FixtureSupport.check(
                    subscription.priority() == EventPriority.LOWEST,
                    "Cold Sweat compatibility handler should run after default modifier contributors"
            );
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing Cold Sweat default-modifier handler", e);
        }
    }
}
