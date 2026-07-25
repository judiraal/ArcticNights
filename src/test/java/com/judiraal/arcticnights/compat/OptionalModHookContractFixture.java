package com.judiraal.arcticnights.compat;

import com.judiraal.arcticnights.test.FixtureSupport;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

public final class OptionalModHookContractFixture {
    private OptionalModHookContractFixture() {
    }

    public static void run() {
        checkWeather2();
        checkSereneSeasons();
        checkMixinRegistration();
    }

    private static void checkWeather2() {
        ClassNode storm = readClass("weather2/weathersystem/storm/StormObject");
        checkField(storm, "stormType", "I");
        checkField(storm, "naturallySpawned", "Z");
        checkField(storm, "weatherMachineControlled", "Z");
        checkField(storm, "TYPE_LAND", "I");
        checkField(storm, "STATE_HAIL", "I");
        checkField(storm, "STATE_FORMING", "I");
        checkMethod(storm, "rollDiceOnMaxIntensity", "()I");

        ClassNode weatherObject = readClass("weather2/weathersystem/storm/WeatherObject");
        checkField(weatherObject, "manager", "Lweather2/weathersystem/WeatherManager;");
        ClassNode manager = readClass("weather2/weathersystem/WeatherManager");
        checkMethod(manager, "getWorld", "()Lnet/minecraft/world/level/Level;");
    }

    private static void checkSereneSeasons() {
        ClassNode helper = readClass("sereneseasons/api/season/SeasonHelper");
        checkMethod(helper, "getSeasonState", "(Lnet/minecraft/world/level/Level;)Lsereneseasons/api/season/ISeasonState;");

        ClassNode state = readClass("sereneseasons/api/season/ISeasonState");
        checkMethod(state, "getCycleDuration", "()I");
        checkMethod(state, "getSubSeason", "()Lsereneseasons/api/season/Season$SubSeason;");
    }

    private static void checkMixinRegistration() {
        String mixinConfig = readResource("arcticnights.mixin.json");
        FixtureSupport.check(mixinConfig.contains("weather2.StormObjectTornadoSeasonMixin"),
                "tornado season mixin must be registered");
    }

    private static ClassNode readClass(String internalName) {
        String resource = internalName + ".class";
        try (InputStream input = OptionalModHookContractFixture.class.getClassLoader().getResourceAsStream(resource)) {
            FixtureSupport.check(input != null, "missing optional-mod class " + internalName);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect optional-mod class " + internalName, e);
        }
    }

    private static String readResource(String resource) {
        try (InputStream input = OptionalModHookContractFixture.class.getClassLoader().getResourceAsStream(resource)) {
            FixtureSupport.check(input != null, "missing resource " + resource);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect resource " + resource, e);
        }
    }

    private static void checkMethod(ClassNode owner, String name, String descriptor) {
        boolean found = owner.methods.stream()
                .anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
        FixtureSupport.check(found, "missing method " + owner.name + "." + name + descriptor);
    }

    private static void checkField(ClassNode owner, String name, String descriptor) {
        boolean found = owner.fields.stream()
                .anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor));
        FixtureSupport.check(found, "missing field " + owner.name + "." + name + ":" + descriptor);
    }
}
