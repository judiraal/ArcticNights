package com.judiraal.arcticnights.mixin.minecraft;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface LevelRandomAccessor {
    @Accessor("randValue")
    int arcticnights$randValue();

    @Accessor("randValue")
    void arcticnights$randValue(int randValue);
}
