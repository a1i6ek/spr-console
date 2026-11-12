package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Stop factor groups", description = "A group with its stop factors. A change of the group or of any of its "
        + "stop factors creates a new version with copies of all of them; checks keep the version they ran with.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factor-groups")
public class StopFactorGroupController {

    private static final String CHANGE_REASON = "Why the change is made, kept in the version history";

    private final StopFactorConsoleService stopFactorService;

    @Operation(summary = "Current versions of the groups, in display order")
    @GetMapping
    public List<StopFactorGroupDto> list(
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Groups with a stop factor that checks applications of this product")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Groups with a stop factor of this segment or of every segment")
            @RequestParam(required = false) ProductSegment segment,
            @Parameter(description = "Groups with a stop factor of this metric")
            @RequestParam(required = false) Long metricId) {
        return stopFactorService.getAll(active, productId, segment, metricId);
    }

    @Operation(summary = "Current version of a group")
    @GetMapping("/{groupId}")
    public StopFactorGroupDto get(@PathVariable long groupId) {
        return stopFactorService.get(groupId);
    }

    @Operation(summary = "Every version of a group, newest first")
    @GetMapping("/{groupId}/versions")
    public List<StopFactorGroupDto> versions(@PathVariable long groupId) {
        return stopFactorService.getVersions(groupId);
    }

    @Operation(summary = "Create a group with its stop factors")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StopFactorGroupDto create(@Valid @RequestBody StopFactorGroupRequest group) {
        return stopFactorService.create(group);
    }

    @Operation(summary = "Replace a group", description = "A new version with these fields and stop factors; the current "
            + "version is returned unchanged when nothing differs. changeReason is required.")
    @PutMapping("/{groupId}")
    public StopFactorGroupDto update(@PathVariable long groupId, @Valid @RequestBody StopFactorGroupRequest group) {
        return stopFactorService.update(groupId, group);
    }

    @Operation(summary = "Turn a group on or off", description = "A new version with the same stop factors")
    @PatchMapping("/{groupId}/status")
    public StopFactorGroupDto changeStatus(@PathVariable long groupId,
                                           @RequestParam boolean active,
                                           @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.changeStatus(groupId, active, changeReason);
    }

    @Operation(summary = "Change the display order", description = "Body: every group id in the new order. Changed in "
            + "place, without new versions: the order does not affect decisions.")
    @PutMapping("/order")
    public List<StopFactorGroupDto> reorder(@RequestBody List<Long> groupIds) {
        return stopFactorService.reorder(groupIds);
    }

    @Operation(summary = "Delete a group", description = "Removes it from the console and from checks; its versions stay")
    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long groupId,
                       @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        stopFactorService.delete(groupId, changeReason);
    }

    @Operation(summary = "Add a stop factor", description = "A new version of the group: take the stop factor ids from the response")
    @PostMapping("/{groupId}/stop-factors")
    public StopFactorGroupDto addFactor(@PathVariable long groupId,
                                        @Valid @RequestBody StopFactorDto factor,
                                        @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.addFactor(groupId, factor, changeReason);
    }

    @Operation(summary = "Replace a stop factor", description = "A new version of the group: take the stop factor ids from the response")
    @PutMapping("/{groupId}/stop-factors/{factorId}")
    public StopFactorGroupDto updateFactor(@PathVariable long groupId,
                                           @PathVariable long factorId,
                                           @Valid @RequestBody StopFactorDto factor,
                                           @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.updateFactor(groupId, factorId, factor, changeReason);
    }

    @Operation(summary = "Remove a stop factor", description = "A new version of the group; the only stop factor stays")
    @DeleteMapping("/{groupId}/stop-factors/{factorId}")
    public StopFactorGroupDto deleteFactor(@PathVariable long groupId,
                                           @PathVariable long factorId,
                                           @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.deleteFactor(groupId, factorId, changeReason);
    }
}
