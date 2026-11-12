package kg.bakai.console.test;

import kg.bakai.spr.core.shared.InternalStatusCode;
import kg.bakai.stopfactor.enums.StopFactorStatus;

/**
 * A ready test application with the decision it must get on the local database (changelogs 1.1..1.4).
 */
public record TestCheckScenario(
        String name,
        String description,
        StopFactorStatus expectedDecision,
        InternalStatusCode expectedInternalStatusCode,
        TestCheckRequest request
) {
}
