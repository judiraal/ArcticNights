package com.judiraal.arcticnights.util;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.world.entity.EntityType;

public interface LocalMobCapExtender {
    Object2FloatMap<EntityType<?>> arcticnights$entityTypeCounter();
}
