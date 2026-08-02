package com.judiraal.arcticnights.test;

import com.judiraal.arcticnights.compat.OptionalModHookContractFixture;
import com.judiraal.arcticnights.compat.cold_sweat.ColdSweatModifierOrderFixture;
import com.judiraal.arcticnights.util.CaveTemperatureProfileFixture;
import com.judiraal.arcticnights.util.SpawnEcologyFixture;
import com.judiraal.arcticnights.util.TornadoSeasonPolicyFixture;
import com.judiraal.arcticnights.worldgen.WorldgenMixinContractFixture;

public final class ArcticNightsFixtureMain {
    private ArcticNightsFixtureMain() {
    }

    public static void main(String[] args) {
        OptionalModHookContractFixture.run();
        ColdSweatModifierOrderFixture.run();
        WorldgenMixinContractFixture.run();
        CaveTemperatureProfileFixture.run();
        SpawnEcologyFixture.run();
        TornadoSeasonPolicyFixture.run();
    }
}
