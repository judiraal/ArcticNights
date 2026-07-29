package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.test.FixtureSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class CaveTemperatureProfileFixture {
    private CaveTemperatureProfileFixture() {
    }

    public static void run() {
        checkProfile(CaveTemperatureProfile.COLD, "cold", -10.0D);
        checkProfile(CaveTemperatureProfile.COOL, "cool", -3.0D);
        checkProfile(CaveTemperatureProfile.MILD_COOL, "mild_cool", -2.0D);
        checkProfile(CaveTemperatureProfile.WARM, "warm", 8.0D);
        checkProfile(CaveTemperatureProfile.HOT, "hot", 12.0D);

        checkTagContains("cold", "terralith:cave/frostfire_caves");
        checkTagContains("cool", "terralith:cave/deep_caves");
        checkTagContains("mild_cool", "minecraft:dripstone_caves");
        checkTagContains("warm", "terralith:cave/thermal_caves");
        checkTagContains("hot", "terralith:cave/mantle_caves");
    }

    private static void checkProfile(CaveTemperatureProfile profile, String path, double expectedCelsius) {
        FixtureSupport.check(
                profile.biomeTag().location().toString().equals("arcticnights:cave_temperature/" + path),
                profile + " should use the expected biome tag"
        );
        FixtureSupport.check(
                Double.compare(profile.celsiusOffset(), expectedCelsius) == 0,
                profile + " should use a " + expectedCelsius + " C offset"
        );
    }

    private static void checkTagContains(String profile, String biomeId) {
        String resource = "data/arcticnights/tags/worldgen/biome/cave_temperature/" + profile + ".json";
        String contents = readResource(resource);
        FixtureSupport.check(
                contents.contains("\"" + biomeId + "\""),
                resource + " should contain " + biomeId
        );
    }

    private static String readResource(String resource) {
        try (InputStream input = CaveTemperatureProfileFixture.class.getClassLoader().getResourceAsStream(resource)) {
            FixtureSupport.check(input != null, "missing cave temperature profile resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect cave temperature profile " + resource, e);
        }
    }
}
