package kg.bakai.console.controller;

import jakarta.validation.Valid;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stop factors. Every change makes the next version of that one stop factor; its versions are its history, each
 * with the period it was checked with and how many scorings it checked.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/console/stop-factors")
public class StopFactorConsoleController {

    private final StopFactorConsoleService stopFactorService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Long metricId,
            @RequestParam(required = false) ProductSegment segment,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(stopFactorService.search(metricId, segment, productId, active, search));
    }

    @GetMapping("/{factorId}")
    public ResponseEntity<?> get(@PathVariable long factorId) {
        return ResponseEntity.ok(stopFactorService.get(factorId));
    }

    @GetMapping("/{factorId}/versions")
    public ResponseEntity<?> versions(@PathVariable long factorId) {
        return ResponseEntity.ok(stopFactorService.getVersions(factorId));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody StopFactorRequest factor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stopFactorService.create(factor));
    }

    @PutMapping("/{factorId}")
    public ResponseEntity<?> update(@PathVariable long factorId, @Valid @RequestBody StopFactorRequest factor) {
        return ResponseEntity.ok(stopFactorService.update(factorId, factor));
    }

    @PatchMapping("/{factorId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable long factorId,
                                          @RequestParam boolean active,
                                          @RequestParam String changeReason) {
        return ResponseEntity.ok(stopFactorService.changeStatus(factorId, active, changeReason));
    }

    @DeleteMapping("/{factorId}")
    public ResponseEntity<Void> delete(@PathVariable long factorId, @RequestParam String changeReason) {
        stopFactorService.delete(factorId, changeReason);
        return ResponseEntity.noContent().build();
    }
}
