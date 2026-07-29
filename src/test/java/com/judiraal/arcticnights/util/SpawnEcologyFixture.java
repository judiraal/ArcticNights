package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.test.FixtureSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class SpawnEcologyFixture {
    private SpawnEcologyFixture() {
    }

    public static void run() {
        FixtureSupport.check(
                SpawnEcology.UPPER_CAVE_REQUIRE_COLD.location().toString().equals("arcticnights:upper_cave_require_cold"),
                "upper-cave cold requirement should use the documented entity tag"
        );
        FixtureSupport.check(
                !SpawnEcology.upperCaveColdRequirementSatisfied(32, 0.8F, 0.1F, false),
                "temperate enclosed caves at Y32 should deny cold-required upper-cave mobs"
        );
        FixtureSupport.check(
                !SpawnEcology.upperCaveColdRequirementSatisfied(64, 0.8F, 0.1F, false),
                "temperate enclosed upper caves should deny cold-required upper-cave mobs"
        );
        FixtureSupport.check(
                SpawnEcology.upperCaveColdRequirementSatisfied(31, 0.8F, 0.1F, false),
                "deep caves below Y32 should preserve existing spawning"
        );
        FixtureSupport.check(
                SpawnEcology.upperCaveColdRequirementSatisfied(48, 0.0F, 0.1F, false),
                "open-sky surface positions should preserve existing spawning"
        );
        FixtureSupport.check(
                SpawnEcology.upperCaveColdRequirementSatisfied(48, 0.8F, 0.0F, false),
                "freezing upper caves should allow cold-required mobs"
        );
        FixtureSupport.check(
                SpawnEcology.upperCaveColdRequirementSatisfied(48, 0.8F, 0.1F, true),
                "the dedicated cold cave profile should allow cold-required mobs"
        );

        String resource = "data/arcticnights/tags/entity_type/upper_cave_require_cold.json";
        String contents = readResource(resource);
        FixtureSupport.check(
                contents.contains("\"#minecraft:zombies\""),
                resource + " should default to the vanilla zombie family"
        );
    }

    private static String readResource(String resource) {
        try (InputStream input = SpawnEcologyFixture.class.getClassLoader().getResourceAsStream(resource)) {
            FixtureSupport.check(input != null, "missing spawn ecology resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect spawn ecology resource " + resource, e);
        }
    }
}
