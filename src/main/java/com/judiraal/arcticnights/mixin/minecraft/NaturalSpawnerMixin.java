package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ArcticSpawner;
import com.judiraal.arcticnights.util.LocalMobCapExtender;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
    @Redirect(method = "createState", at = @At(value = "INVOKE", target = "net/minecraft/world/entity/Mob.requiresCustomPersistence ()Z"))
    private static boolean arcticnights$increaseMonsterSpawn(Mob mob, @Local(argsOnly = true) LocalMobCapCalculator calculator) {
        if (mob.requiresCustomPersistence()) return true;
        if (!ArcticNightsFeatures.arcticSpawning()) return false;
        if (mob.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            var factor = ArcticSpawner.spawnFactor(mob.getType(), mob.blockPosition(), level);
            if (factor > 0.0F && factor < 0.5F) {
                var entityTypeCounter = ((LocalMobCapExtender)calculator).arcticnights$entityTypeCounter();
                var count = entityTypeCounter.getOrDefault(mob.getType(), 0F);
                if (count >= 1F) return true;
                entityTypeCounter.put(mob.getType(), count + 1F);
            } else if (factor > 1F) {
                var entityTypeCounter = ((LocalMobCapExtender)calculator).arcticnights$entityTypeCounter();
                var count = entityTypeCounter.getOrDefault(mob.getType(), 0F);
                count += 1F / factor;
                var result = count < 1F;
                if (!result) count -= 1F;
                entityTypeCounter.put(mob.getType(), count);
                return result;
            }
        }
        return false;
    }
}
