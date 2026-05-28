package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.util.ClimateTickrateCatchup;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
    @Redirect(method = "tickChunks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"))
    private void arcticnights$tickChunkWithClimateCatchup(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        ClimateTickrateCatchup.tickVanillaChunk(level, chunk, randomTickSpeed);
    }
}
