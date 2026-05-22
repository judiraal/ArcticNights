package com.judiraal.arcticnights.mixin.cold_sweat;

import com.momosoftworks.coldsweat.util.math.RegistryMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(value = RegistryMultiMap.class, remap = false)
public abstract class RegistryMultiMapMixin<K, V> {
    @Shadow
    @Final
    private HashMap<K, LinkedHashSet<V>> internal;

    /**
     * @author Judiraal
     * @reason Avoid allocating an empty LinkedHashSet for every successful Cold Sweat block-temp registry lookup.
     */
    @Overwrite(remap = false)
    public Set<V> get(K key) {
        Set<V> values = this.internal.get(key);
        Set<V> defaultValues = this.internal.get(null);

        if (values == null) {
            if (defaultValues == null || defaultValues.isEmpty()) {
                return new LinkedHashSet<>();
            }
            return new LinkedHashSet<>(defaultValues);
        }

        if (defaultValues != null && !defaultValues.isEmpty() && values != defaultValues) {
            values.addAll(defaultValues);
        }
        return values;
    }
}
