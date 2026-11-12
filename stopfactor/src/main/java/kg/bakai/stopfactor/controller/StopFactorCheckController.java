package kg.bakai.stopfactor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.service.StopFactorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Results of the stop factor checks of SPR scorings. The check itself is not a REST endpoint: SPR calls
 * {@link StopFactorService#check} with the KIB / OB reports.
 */
@Tag(name = "Scoring checks", description = "Results of the stop factor checks of SPR scorings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stop-factors/checks")
public class StopFactorCheckController {

    private final StopFactorService stopFactorService;

    @Operation(summary = "Saved results of a scoring", description = "One row per stop factor, with the group version it was checked with")
    @GetMapping("/{scoringId}")
    public List<StopFactorProjection> listByScoring(@PathVariable long scoringId) {
        return stopFactorService.findByScoringId(scoringId);
    }
}
