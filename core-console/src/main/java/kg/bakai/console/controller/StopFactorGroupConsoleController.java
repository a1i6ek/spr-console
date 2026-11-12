package kg.bakai.console.controller;

import jakarta.validation.Valid;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stop factor groups. A group with its stop factors; a change of the group or of any of its stop factors creates
 * a new version with copies of all of them, the previous version is closed and kept for the checks made with it.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factor-groups")
public class StopFactorGroupConsoleController {

    private final StopFactorConsoleService stopFactorService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) ProductSegment segment,
            @RequestParam(required = false) Long metricId) {
        return ResponseEntity.ok(stopFactorService.getAll(active, productId, segment, metricId));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<?> get(@PathVariable long groupId) {
        return ResponseEntity.ok(stopFactorService.get(groupId));
    }

    /** Every version of a group, newest first. */
    @GetMapping("/{groupId}/versions")
    public ResponseEntity<?> versions(@PathVariable long groupId) {
        return ResponseEntity.ok(stopFactorService.getVersions(groupId));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody StopFactorGroupRequest group) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stopFactorService.create(group));
    }

    /** Replaces a group: a new version with these fields and stop factors; nothing is created when nothing
     * differs. changeReason is required. */
    @PutMapping("/{groupId}")
    public ResponseEntity<?> update(@PathVariable long groupId, @Valid @RequestBody StopFactorGroupRequest group) {
        return ResponseEntity.ok(stopFactorService.update(groupId, group));
    }

    /** Turns a group on or off: a new version with the same stop factors. */
    @PatchMapping("/{groupId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable long groupId,
                                          @RequestParam boolean active,
                                          @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.changeStatus(groupId, active, changeReason));
    }

    /** Body: every group id in the new order. Changed in place, without new versions: the order does not affect
     * decisions. */
    @PutMapping("/order")
    public ResponseEntity<?> reorder(@RequestBody List<Long> groupIds) {
        return ResponseEntity.ok(stopFactorService.reorder(groupIds));
    }

    /** Removes a group from the console and from checks; its versions stay for the checks made with them. */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> delete(@PathVariable long groupId, @RequestParam String changeReason) {
        stopFactorService.delete(groupId, changeReason);
        return ResponseEntity.noContent().build();
    }

    /** Adds a stop factor: a new version of the group, take the stop factor ids from the response. */
    @PostMapping("/{groupId}/stop-factors")
    public ResponseEntity<?> addFactor(@PathVariable long groupId,
                                       @Valid @RequestBody StopFactorDto factor,
                                       @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.addFactor(groupId, factor, changeReason));
    }

    /** Replaces a stop factor: a new version of the group, take the stop factor ids from the response. */
    @PutMapping("/{groupId}/stop-factors/{factorId}")
    public ResponseEntity<?> updateFactor(@PathVariable long groupId,
                                          @PathVariable long factorId,
                                          @Valid @RequestBody StopFactorDto factor,
                                          @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.updateFactor(groupId, factorId, factor, changeReason));
    }

    /** Removes a stop factor: a new version of the group; the only stop factor stays. */
    @DeleteMapping("/{groupId}/stop-factors/{factorId}")
    public ResponseEntity<?> deleteFactor(@PathVariable long groupId,
                                          @PathVariable long factorId,
                                          @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.deleteFactor(groupId, factorId, changeReason));
    }
}
