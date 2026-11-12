package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.dto.StopFactorProjection;
import kg.bakai.stopfactor.entity.StopFactorCheck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StopFactorCheckRepository extends JpaRepository<StopFactorCheck, Long> {

    /** Results of a scoring, one per stop factor, with the group version and the rule it was checked with. */
    @Query(value = """
            SELECT c.id                      AS "id",
                   COALESCE(g.root_id, g.id) AS "groupId",
                   g.id                      AS "groupVersionId",
                   g.version_number          AS "groupVersionNumber",
                   g.name                    AS "groupName",
                   f.id                      AS "stopFactorId",
                   f.name                    AS "stopFactorName",
                   m.id                      AS "metricId",
                   m.title                   AS "metricTitle",
                   f.params::text            AS "params",
                   c.product_id              AS "productId",
                   c.result                  AS "checkResult",
                   c.checked_value           AS "checkedValue",
                   rej.value_type            AS "rejectedValueType",
                   rej.operator              AS "rejectedOperator",
                   rej.value                 AS "rejectedValue",
                   rej.value_to              AS "rejectedValueTo",
                   uw.value_type             AS "underwriterValueType",
                   uw.operator               AS "underwriterOperator",
                   uw.value                  AS "underwriterValue",
                   uw.value_to               AS "underwriterValueTo"
            FROM stop_factors.stop_factor_check c
            JOIN stop_factors.stop_factor f ON f.id = c.stop_factor_id
            JOIN stop_factors.stop_factor_group g ON g.id = f.group_id
            JOIN stop_factors.stop_factor_metric m ON m.id = f.metric_id
            LEFT JOIN stop_factors.stop_factor_condition rej ON rej.stop_factor_id = f.id AND rej.result = 'REJECTED'
            LEFT JOIN stop_factors.stop_factor_condition uw ON uw.stop_factor_id = f.id AND uw.result = 'UNDERWRITER'
            WHERE c.scoring_id = :scoringId
            ORDER BY g.order_number, g.id, f.order_number, f.id
            """, nativeQuery = true)
    List<StopFactorProjection> findByScoringIdForProjection(long scoringId);

    @Modifying
    @Query(value = "DELETE FROM stop_factors.stop_factor_check WHERE scoring_id = :scoringId", nativeQuery = true)
    void deleteByScoringId(long scoringId);
}
