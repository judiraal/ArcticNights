package com.judiraal.arcticnights.mixin.weather2;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import weather2.command.WeatherCommand;

@Mixin(value = WeatherCommand.class, remap = false)
public class WeatherCommandMixin {
    @Redirect(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;register(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/tree/LiteralCommandNode;"
            )
    )
    private static <S> LiteralCommandNode<S> arcticnights$forceOpOnly(CommandDispatcher dispatcher, LiteralArgumentBuilder<S> builder) {
        builder.requires(src -> ((CommandSourceStack) src).hasPermission(2));
        return dispatcher.register(builder);
    }
}