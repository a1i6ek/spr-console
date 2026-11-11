package kg.bakai.stopfactor.repository;

import kg.bakai.stopfactor.entity.StopFactorCheck;
import kg.bakai.stopfactor.dto.StopFactorProjection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StopFactorCheckRepository extends JpaRepository<StopFactorCheck, Long> {

    /** Results of a scoring, one per stop factor, with the conditions of the version each was made with. */
    @Query(value = """
            select
                coalesce(g.root_id, g.id) as "stopFactorId",
                g.id as "stopFactorVersionId",
                g.version_number as "versionNumber",
                case when (select count(*) from stop_factors.stop_factor o where o.group_id = g.id) = 1
                     then (select o.name from stop_factors.stop_factor o where o.group_id = g.id order by o.order_number limit 1)
                     else (select m.title from stop_factors.stop_factor o
                           join stop_factors.stop_factor_metric m on m.id = o.metric_id
                           where o.group_id = g.id order by o.order_number limit 1)
                end as "stopFactorName",
                f.id as "factorId",
                f.name as "factorName",
                c.result as "checkResult",
                c.checked_value as "checkedValue",
                rej.value as "rejectedValue",
                rej.value_type as "rejectedValueType",
                rej.operator as "rejectedOperator",
                uw.value as "underwriterValue",
                uw.value_type as "underwriterValueType",
                uw.operator as "underwriterOperator",
                uw.value_to as "underwriterValueTo"
            from stop_factors.stop_factor_check c
            join stop_factors.stop_factor f on f.id = c.stop_factor_id
            join stop_factors.stop_factor_group g on g.id = f.group_id
            left join stop_factors.stop_factor_condition rej on rej.stop_factor_id = f.id and rej.result = 'REJECTED'
            left join stop_factors.stop_factor_condition uw on uw.stop_factor_id = f.id and uw.result = 'UNDERWRITER'
            where c.scoring_id = :scoringId
            order by g.order_number, g.id, f.order_number
            """, nativeQuery = true)
    List<StopFactorProjection> findByScoringIdForProjection(long scoringId);

    @Modifying
    @Query("delete from StopFactorCheck c where c.scoringId = :scoringId")
    void deleteByScoringId(long scoringId);
}
