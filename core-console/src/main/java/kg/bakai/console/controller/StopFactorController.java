package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Stop factors", description = "Stop factors of the current version of a group. Every change creates a new "
        + "group version and returns it: the stop factors get new ids, take them from the response.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factor-groups/{groupId}/stop-factors")
public class StopFactorController {

    private static final String CHANGE_REASON = "Why the change is made, kept in the version history";

    private final StopFactorConsoleService stopFactorService;

    @Operation(summary = "Stop factors of a group")
    @GetMapping
    public List<StopFactorDto> list(@PathVariable long groupId) {
        return stopFactorService.getFactors(groupId);
    }

    @Operation(summary = "One stop factor")
    @GetMapping("/{stopFactorId}")
    public StopFactorDto get(@PathVariable long groupId, @PathVariable long stopFactorId) {
        return stopFactorService.getFactor(groupId, stopFactorId);
    }

    @Operation(summary = "Add a stop factor after the others",
            description = "A name left out is the metric's name template rendered with the params")
    @PostMapping
    public StopFactorResponse add(@PathVariable long groupId,
                                  @Valid @RequestBody StopFactorDto stopFactor,
                                  @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.addFactor(groupId, stopFactor, changeReason);
    }

    @Operation(summary = "Replace a stop factor", description = "Name, params, conditions, status, segment and products. "
            + "A name left out is kept when the params stay, otherwise rendered from the new params.")
    @PutMapping("/{stopFactorId}")
    public StopFactorResponse update(@PathVariable long groupId,
                                     @PathVariable long stopFactorId,
                                     @Valid @RequestBody StopFactorDto stopFactor,
                                     @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.updateFactor(groupId, stopFactorId, stopFactor, changeReason);
    }

    @Operation(summary = "Remove a stop factor", description = "The only stop factor of a group cannot be removed")
    @DeleteMapping("/{stopFactorId}")
    public StopFactorResponse remove(@PathVariable long groupId,
                                     @PathVariable long stopFactorId,
                                     @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.deleteFactor(groupId, stopFactorId, changeReason);
    }
}
