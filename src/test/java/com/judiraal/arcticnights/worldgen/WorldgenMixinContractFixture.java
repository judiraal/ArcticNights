package com.judiraal.arcticnights.worldgen;

import com.judiraal.arcticnights.test.FixtureSupport;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class WorldgenMixinContractFixture {
    private static final String RANDOM_STATE_CREATE_DESCRIPTOR =
            "(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)"
                    + "Lnet/minecraft/world/level/levelgen/RandomState;";

    private WorldgenMixinContractFixture() {
    }

    public static void run() {
        checkChunkMapTarget();
        checkSamplingHelper();
        checkMixinRegistration();
    }

    private static void checkChunkMapTarget() {
        ClassNode chunkMap = readClass("net/minecraft/server/level/ChunkMap");
        long matchingCalls = chunkMap.methods.stream()
                .filter(method -> method.name.equals("<init>"))
                .flatMap(method -> method.instructions.iterator().hasNext()
                        ? java.util.stream.StreamSupport.stream(
                                java.util.Spliterators.spliteratorUnknownSize(method.instructions.iterator(), 0),
                                false
                        )
                        : java.util.stream.Stream.empty())
                .filter(WorldgenMixinContractFixture::isRandomStateCreate)
                .count();

        FixtureSupport.check(
                matchingCalls == 2,
                "ChunkMap should retain the two expected RandomState.create calls; found " + matchingCalls
        );
    }

    private static boolean isRandomStateCreate(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode method
                && method.owner.equals("net/minecraft/world/level/levelgen/RandomState")
                && method.name.equals("create")
                && method.desc.equals(RANDOM_STATE_CREATE_DESCRIPTOR);
    }

    private static void checkSamplingHelper() {
        ClassNode helper = readClass("com/judiraal/arcticnights/worldgen/ArcticBiomeSampling");
        checkMethod(
                helper,
                "withBandedTemperature",
                "(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
        );
        checkMethod(
                helper,
                "withBandedTemperature",
                "(Lnet/minecraft/world/level/levelgen/NoiseRouter;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"
        );
    }

    private static void checkMixinRegistration() {
        String mixinConfig = readResource("arcticnights.mixin.json");
        FixtureSupport.check(
                mixinConfig.contains("minecraft.ChunkMapMixin"),
                "the Arctic random-state mixin must be registered"
        );
        FixtureSupport.check(
                !mixinConfig.contains("minecraft.NoiseBasedChunkGeneratorMixin"),
                "the superseded per-chunk biome mixin must not remain registered"
        );
    }

    private static ClassNode readClass(String internalName) {
        String resource = internalName + ".class";
        try (InputStream input = WorldgenMixinContractFixture.class.getClassLoader().getResourceAsStream(resource)) {
            FixtureSupport.check(input != null, "missing class " + internalName);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect class " + internalName, e);
        }
    }

    private static String readResource(String resource) {
        try (InputStream input = WorldgenMixinContractFixture.class.getClassLoader().getResourceAsStream(resource)) {
            FixtureSupport.check(input != null, "missing resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect resource " + resource, e);
        }
    }

    private static void checkMethod(ClassNode owner, String name, String descriptor) {
        boolean found = owner.methods.stream()
                .anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
        FixtureSupport.check(found, "missing method " + owner.name + "." + name + descriptor);
    }
}
