package kg.bakai.stopfactor.entity;

import jakarta.persistence.*;
import kg.bakai.common.entity.BaseEntity;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The result of one stop factor for one scoring. It points to the stop factor row of the group version it ran with;
 * those rows never change, so the check always shows the rule it was made with.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_check", schema = "stop_factors")
public class StopFactorCheck extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_check_id_seq")
    @SequenceGenerator(name = "stop_factor_check_id_seq", sequenceName = "stop_factor_check_id_generator", schema = "public", allocationSize = 1)
    private Long id;

    @Column(name = "scoring_id", nullable = false)
    private Long scoringId;

    /** Product of public.product (SPR), null for a scoring without product. */
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stop_factor_id", nullable = false)
    private StopFactor stopFactor;

    @Column(name = "checked_value", nullable = false, length = Integer.MAX_VALUE)
    private String checkedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private StopFactorStatus result = StopFactorStatus.APPROVED;

    public StopFactorCheck(long scoringId, Long productId, StopFactor stopFactor, String checkedValue) {
        this.scoringId = scoringId;
        this.productId = productId;
        this.stopFactor = stopFactor;
        this.checkedValue = checkedValue;
    }
}
