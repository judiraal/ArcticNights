package com.judiraal.arcticnights.test;

import com.judiraal.arcticnights.compat.OptionalModHookContractFixture;
import com.judiraal.arcticnights.util.CaveTemperatureProfileFixture;
import com.judiraal.arcticnights.util.SpawnEcologyFixture;
import com.judiraal.arcticnights.util.TornadoSeasonPolicyFixture;

public final class ArcticNightsFixtureMain {
    private ArcticNightsFixtureMain() {
    }

    public static void main(String[] args) {
        OptionalModHookContractFixture.run();
        CaveTemperatureProfileFixture.run();
        SpawnEcologyFixture.run();
        TornadoSeasonPolicyFixture.run();
    }
}
