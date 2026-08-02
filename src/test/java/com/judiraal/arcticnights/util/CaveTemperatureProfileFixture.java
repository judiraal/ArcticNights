package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.test.FixtureSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class CaveTemperatureProfileFixture {
    private CaveTemperatureProfileFixture() {
    }

    public static void run() {
        checkProfile(CaveTemperatureProfile.COLD, "cold", 0.0D, true);
        checkProfile(CaveTemperatureProfile.COOL, "cool", -3.0D, false);
        checkProfile(CaveTemperatureProfile.MILD_COOL, "mild_cool", -2.0D, false);
        checkProfile(CaveTemperatureProfile.WARM, "warm", 8.0D, false);
        checkProfile(CaveTemperatureProfile.HOT, "hot", 12.0D, false);
        checkColdResponse();

        checkTagContains("cold", "terralith:cave/frostfire_caves");
        checkTagContains("cool", "terralith:cave/deep_caves");
        checkTagContains("mild_cool", "minecraft:dripstone_caves");
        checkTagContains("warm", "terralith:cave/thermal_caves");
        checkTagContains("hot", "terralith:cave/mantle_caves");
    }

    private static void checkProfile(
            CaveTemperatureProfile profile,
            String path,
            double expectedCelsius,
            boolean expectsResponse
    ) {
        FixtureSupport.check(
                profile.biomeTag().location().toString().equals("arcticnights:cave_temperature/" + path),
                profile + " should use the expected biome tag"
        );
        FixtureSupport.check(
                Double.compare(profile.celsiusOffset(), expectedCelsius) == 0,
                profile + " should use a " + expectedCelsius + " C offset"
        );
        FixtureSupport.check(
                profile.temperatureResponse().isPresent() == expectsResponse,
                profile + " should have the expected optional temperature response"
        );
    }

    private static void checkColdResponse() {
        CaveTemperatureProfile.CelsiusTemperatureResponse response = CaveTemperatureProfile.COLD
                .temperatureResponse()
                .orElseThrow();
        FixtureSupport.check(response.sourceMinimumCelsius() == 10.0D, "cold response source should start at 10 C");
        FixtureSupport.check(response.sourceMaximumCelsius() == 40.0D, "cold response source should end at 40 C");
        FixtureSupport.check(response.resultMinimumCelsius() == 0.0D, "cold response result should start at 0 C");
        FixtureSupport.check(response.resultMaximumCelsius() == 12.0D, "cold response result should end at 12 C");

        checkResponse(response, 0.0D, -4.0D);
        checkResponse(response, 10.0D, 0.0D);
        checkResponse(response, 18.1D, 3.24D);
        checkResponse(response, 20.0D, 4.0D);
        checkResponse(response, 27.9D, 7.16D);
        checkResponse(response, 40.0D, 12.0D);
        checkResponse(response, 50.0D, 16.0D);
    }

    private static void checkResponse(
            CaveTemperatureProfile.CelsiusTemperatureResponse response,
            double sourceCelsius,
            double expectedResultCelsius
    ) {
        double sourceMinecraft = ClimateService.minecraftTemperatureFromCelsius(sourceCelsius);
        double resultMinecraft = response.applyMinecraftTemperature(sourceMinecraft);
        double resultCelsius = resultMinecraft * ClimateService.COLD_SWEAT_CELSIUS_PER_MC;
        FixtureSupport.check(
                Math.abs(resultCelsius - expectedResultCelsius) < 0.00001D,
                "cold response should map " + sourceCelsius + " C to " + expectedResultCelsius + " C, got " + resultCelsius
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
