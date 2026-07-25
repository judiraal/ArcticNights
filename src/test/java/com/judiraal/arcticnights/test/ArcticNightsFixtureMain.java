package com.judiraal.arcticnights.test;

import com.judiraal.arcticnights.compat.OptionalModHookContractFixture;
import com.judiraal.arcticnights.util.TornadoSeasonPolicyFixture;

public final class ArcticNightsFixtureMain {
    private ArcticNightsFixtureMain() {
    }

    public static void main(String[] args) {
        OptionalModHookContractFixture.run();
        TornadoSeasonPolicyFixture.run();
    }
}
