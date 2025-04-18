package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.LocalMobCapExtender;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LocalMobCapCalculator.class)
public class LocalMobCapCalculatorMixin implements LocalMobCapExtender {
    @Unique
    private final Object2FloatMap<EntityType<?>> arcticnights$entityTypeCounter = new Object2FloatOpenHashMap<>();

    @Override
    public Object2FloatMap<EntityType<?>> arcticnights$entityTypeCounter() {
        return arcticnights$entityTypeCounter;
    }
}
