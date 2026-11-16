package kg.bakai.stopfactor.service;

import kg.bakai.stopfactor.dto.StopFactorDecision;
import kg.bakai.spr.core.kib.dto.ConsolidationDto;
import kg.bakai.spr.core.kib.dto.IndividualReport;
import kg.bakai.spr.core.kib.dto.response.CustomReport;
import kg.bakai.spr.core.ob.dto.response.TotalDebtFinalResponse;
import kg.bakai.stopfactor.dto.StopFactorProjection;

import java.time.LocalDate;
import java.util.List;

public interface StopFactorService {
    StopFactorDecision check(long scoringId, CustomReport customReport, IndividualReport individualReport, ConsolidationDto consolidationDto, TotalDebtFinalResponse totalDebt, LocalDate requestDate);

    List<StopFactorProjection> findByScoringId(Long scoringId);
}
