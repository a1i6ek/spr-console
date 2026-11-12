package kg.bakai.stopfactor.controller;

import kg.bakai.stopfactor.service.StopFactorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Results of the stop factor checks of SPR scorings. The check itself is not a REST endpoint: SPR calls
 * {@link StopFactorService#check} with the KIB / OB reports.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stop-factors/checks")
public class StopFactorCheckController {

    private final StopFactorService stopFactorService;

    /** One row per stop factor, with the group version it was checked with. */
    @GetMapping("/{scoringId}")
    public ResponseEntity<?> listByScoring(@PathVariable long scoringId) {
        return ResponseEntity.ok(stopFactorService.findByScoringId(scoringId));
    }
}
