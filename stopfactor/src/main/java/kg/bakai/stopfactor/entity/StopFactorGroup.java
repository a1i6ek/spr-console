package kg.bakai.stopfactor.entity;

import jakarta.persistence.*;
import kg.bakai.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A box: all stop factors as they were checked from {@link #validFrom} to {@link #validTo}. Exactly one box is current
 * (validTo null). Any change of a stop factor makes the next box with copies of all of them, the change applied, and
 * closes this one; the closed boxes stay for the checks made with their stop factors. Nobody sees the boxes in the
 * console: they are the history.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stop_factor_group", schema = "stop_factors")
public class StopFactorGroup extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_group_id_seq")
    @SequenceGenerator(name = "stop_factor_group_id_seq", sequenceName = "stop_factor_group_id_generator", schema = "public", allocationSize = 1)
    private Long id;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDateTime validFrom;

    /** null - the current box. */
    @Column(name = "valid_to")
    private LocalDateTime validTo;

    /** Why the box was made: the change of a stop factor. */
    @Column(name = "change_reason", updatable = false)
    private String changeReason;

    /** Its stop factors, in check order; saved with the box. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    @OrderBy("orderNumber, factorId")
    private List<StopFactor> factors = new ArrayList<>();

    /** The first box, empty. */
    public static StopFactorGroup first(LocalDateTime validFrom, String reason) {
        StopFactorGroup box = new StopFactorGroup();
        box.versionNumber = 1;
        box.validFrom = validFrom;
        box.changeReason = reason;
        return box;
    }

    /** The box after this one, valid from now, empty: the caller puts the stop factors in. */
    public StopFactorGroup next(LocalDateTime now, String reason) {
        StopFactorGroup next = first(now, reason);
        next.versionNumber = versionNumber + 1;
        return next;
    }

    public void add(StopFactor factor) {
        factors.add(factor);
    }

    public boolean isCurrent() {
        return validTo == null;
    }
}
