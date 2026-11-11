package kg.bakai.console.controller;

import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.enums.StopFactorStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static kg.bakai.console.controller.ConsoleUser.HEADER;
import static kg.bakai.console.controller.ConsoleUser.orUnknown;

@Tag(name = "Console: stop factors of a group", description = "Stop factors of the current version of a group and their "
        + "conditions. Every change creates a new group version and returns the group: the stop factors get new ids, "
        + "take them from the response. changeReason is required.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factors/{groupId}/factors")
public class StopFactorFactorsConsoleController {

    private static final String CHANGE_REASON = "Why the change is made, kept in the version history";

    private final StopFactorConsoleService stopFactorConsoleService;

    @GetMapping
    public List<StopFactorDto> getFactors(@PathVariable long groupId) {
        return stopFactorConsoleService.getFactors(groupId);
    }

    @GetMapping("/{factorId}")
    public StopFactorDto getFactor(@PathVariable long groupId, @PathVariable long factorId) {
        return stopFactorConsoleService.getFactor(groupId, factorId);
    }

    @Operation(summary = "Add a stop factor after the others",
            description = "A name left out is the metric's name template rendered with the params")
    @PostMapping
    public StopFactorResponse addFactor(@PathVariable long groupId,
                                        @Valid @RequestBody StopFactorDto factor,
                                        @Parameter(description = CHANGE_REASON) @RequestParam String changeReason,
                                        @RequestHeader(name = HEADER, required = false) String user) {
        return stopFactorConsoleService.addFactor(groupId, factor, changeReason, orUnknown(user));
    }

    @Operation(summary = "Replace a stop factor: name, params, conditions, active",
            description = "A name left out is kept when the params stay, otherwise rendered from the new params")
    @PutMapping("/{factorId}")
    public StopFactorResponse updateFactor(@PathVariable long groupId,
                                           @PathVariable long factorId,
                                           @Valid @RequestBody StopFactorDto factor,
                                           @Parameter(description = CHANGE_REASON) @RequestParam String changeReason,
                                           @RequestHeader(name = HEADER, required = false) String user) {
        return stopFactorConsoleService.updateFactor(groupId, factorId, factor, changeReason, orUnknown(user));
    }

    @Operation(summary = "Remove a stop factor", description = "The only stop factor of a group cannot be removed")
    @DeleteMapping("/{factorId}")
    public StopFactorResponse deleteFactor(@PathVariable long groupId,
                                           @PathVariable long factorId,
                                           @Parameter(description = CHANGE_REASON) @RequestParam String changeReason,
                                           @RequestHeader(name = HEADER, required = false) String user) {
        return stopFactorConsoleService.deleteFactor(groupId, factorId, changeReason, orUnknown(user));
    }

    @GetMapping("/{factorId}/conditions")
    public List<ConditionDto> getConditions(@PathVariable long groupId, @PathVariable long factorId) {
        return stopFactorConsoleService.getConditions(groupId, factorId);
    }

    @Operation(summary = "Set the condition of a result", description = "Adds the REJECTED or UNDERWRITER condition "
            + "of the body's result, or replaces it. Operators: GET /api/v1/admin/metrics/calculators, allowedOperators")
    @PutMapping("/{factorId}/conditions")
    public StopFactorResponse saveCondition(@PathVariable long groupId,
                                            @PathVariable long factorId,
                                            @Valid @RequestBody ConditionDto condition,
                                            @Parameter(description = CHANGE_REASON) @RequestParam String changeReason,
                                            @RequestHeader(name = HEADER, required = false) String user) {
        return stopFactorConsoleService.saveCondition(groupId, factorId, condition, changeReason, orUnknown(user));
    }

    @Operation(summary = "Remove the condition of a result", description = "A stop factor without conditions is checked "
            + "and saved, but decides nothing")
    @DeleteMapping("/{factorId}/conditions/{result}")
    public StopFactorResponse deleteCondition(@PathVariable long groupId,
                                              @PathVariable long factorId,
                                              @PathVariable StopFactorStatus result,
                                              @Parameter(description = CHANGE_REASON) @RequestParam String changeReason,
                                              @RequestHeader(name = HEADER, required = false) String user) {
        return stopFactorConsoleService.deleteCondition(groupId, factorId, result, changeReason, orUnknown(user));
    }
}
