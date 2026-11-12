package kg.bakai.console.testing;

import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.enums.StopFactorStatus;

public record TestCheckScenarioResult(
        String scenario,
        String description,
        StopFactorStatus expectedDecision,
        InternalStatusCode expectedInternalStatusCode,
        boolean asExpected,
        TestCheckResponse result
) {
}
