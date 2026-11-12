package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Stop factor conditions", description = "REJECTED and UNDERWRITER conditions of a stop factor. Every change "
        + "creates a new group version and returns it.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factor-groups/{groupId}/stop-factors/{stopFactorId}/conditions")
public class StopFactorConditionController {

    private static final String CHANGE_REASON = "Why the change is made, kept in the version history";

    private final StopFactorConsoleService stopFactorService;

    @Operation(summary = "Conditions of a stop factor")
    @GetMapping
    public List<ConditionDto> list(@PathVariable long groupId, @PathVariable long stopFactorId) {
        return stopFactorService.getConditions(groupId, stopFactorId);
    }

    @Operation(summary = "Set the condition of a result", description = "Adds the REJECTED or UNDERWRITER condition of the "
            + "body's result or replaces it. Operators: GET " + "/api/v1/admin/metrics" + "/calculators, allowedOperators.")
    @PutMapping
    public StopFactorResponse save(@PathVariable long groupId,
                                   @PathVariable long stopFactorId,
                                   @Valid @RequestBody ConditionDto condition,
                                   @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.saveCondition(groupId, stopFactorId, condition, changeReason);
    }

    @Operation(summary = "Remove the condition of a result",
            description = "A stop factor without conditions is checked and saved, but decides nothing")
    @DeleteMapping("/{result}")
    public StopFactorResponse remove(@PathVariable long groupId,
                                     @PathVariable long stopFactorId,
                                     @PathVariable StopFactorStatus result,
                                     @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.deleteCondition(groupId, stopFactorId, result, changeReason);
    }
}
