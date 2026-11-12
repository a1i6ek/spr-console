package kg.bakai.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Stop factor groups", description = "A group is the versioned unit: every change creates a new version "
        + "with all its stop factors, checks keep the version they were made with")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factor-groups")
public class StopFactorGroupController {

    private static final String CHANGE_REASON = "Why the change is made, kept in the version history";

    private final StopFactorConsoleService stopFactorService;

    @Operation(summary = "Current groups, or the group versions valid at a moment",
            description = "A group is listed when any of its stop factors matches. With at, every stop factor shows the "
                    + "metric version it used then.")
    @GetMapping
    public List<StopFactorResponse> list(
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Groups with a stop factor of this metric")
            @RequestParam(required = false) Long metricId,
            @Parameter(description = "Stop factors that check applications of this product")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Stop factors of this segment, including the ones without a segment")
            @RequestParam(required = false) ProductSegment segment,
            @Parameter(description = "Moment of the history, e.g. 2026-11-01T00:00:00; left out - the current versions")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return stopFactorService.getAll(active, metricId, productId, segment, at);
    }

    @Operation(summary = "Current version of a group")
    @GetMapping("/{groupId}")
    public StopFactorResponse get(@PathVariable long groupId) {
        return stopFactorService.get(groupId);
    }

    @Operation(summary = "All versions of a group, newest first")
    @GetMapping("/{groupId}/versions")
    public List<StopFactorResponse> listVersions(@PathVariable long groupId) {
        return stopFactorService.getVersions(groupId);
    }

    @Operation(summary = "Create a group with its stop factors")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StopFactorResponse create(@Valid @RequestBody StopFactorRequest group) {
        return stopFactorService.create(group);
    }

    @Operation(summary = "Replace a group", description = "Creates a new version and closes the current one; returns the "
            + "current version unchanged when nothing differs. changeReason is required.")
    @PutMapping("/{groupId}")
    public StopFactorResponse update(@PathVariable long groupId,
                                     @Valid @RequestBody StopFactorRequest group) {
        return stopFactorService.update(groupId, group);
    }

    @Operation(summary = "Activate or deactivate a group", description = "Creates a new version with the same stop factors")
    @PatchMapping("/{groupId}/status")
    public StopFactorResponse changeStatus(@PathVariable long groupId,
                                           @RequestParam boolean active,
                                           @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        return stopFactorService.changeStatus(groupId, active, changeReason);
    }

    @Operation(summary = "Change the display order", description = "Body: every current group id in the new order, "
            + "e.g. [1, 2, 13, 3]. Updated in place without new versions: the order does not affect decisions.")
    @PutMapping("/order")
    public List<StopFactorResponse> reorder(@RequestBody List<Long> groupIds) {
        return stopFactorService.reorder(groupIds);
    }

    @Operation(summary = "Delete a group", description = "Removes it from the console and from checks. Its versions stay, "
            + "a last inactive version records the reason.")
    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long groupId,
                       @Parameter(description = CHANGE_REASON) @RequestParam String changeReason) {
        stopFactorService.delete(groupId, changeReason);
    }
}
