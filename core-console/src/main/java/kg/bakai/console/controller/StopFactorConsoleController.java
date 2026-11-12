package kg.bakai.console.controller;

import jakarta.validation.Valid;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stop factors across the groups. Reads span every current group version, so an admin finds a factor by its metric,
 * product or name without walking the groups one by one. Every change is made as a new version of the group the
 * factor belongs to, the same as through the group endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/stop-factors")
public class StopFactorConsoleController {

    private final StopFactorConsoleService stopFactorService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long metricId,
            @RequestParam(required = false) ProductSegment segment,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(stopFactorService.searchFactors(groupId, metricId, segment, productId, active, search));
    }

    @GetMapping("/{factorId}")
    public ResponseEntity<?> get(@PathVariable long factorId) {
        return ResponseEntity.ok(stopFactorService.getFactor(factorId));
    }

    @PutMapping("/{factorId}")
    public ResponseEntity<?> update(@PathVariable long factorId,
                                    @Valid @RequestBody StopFactorRequest factor,
                                    @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.updateFactor(factorId, factor, changeReason));
    }

    @PatchMapping("/{factorId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable long factorId,
                                          @RequestParam boolean active,
                                          @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.changeFactorStatus(factorId, active, changeReason));
    }
}
