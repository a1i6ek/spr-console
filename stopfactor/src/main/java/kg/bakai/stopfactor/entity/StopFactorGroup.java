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
import java.util.stream.Collectors;

/**
 * A group of stop factors, the versioning unit: if ANY stop factor of a group changes, the group version is closed
 * (valid_to) and a NEW group version is created with ALL stop factors copied, so a check keeps the exact setup it
 * ran with. Like an order header it holds only the description, status and order; each stop factor keeps its own
 * metric, params, conditions, segment and products. The group name is derived from its stop factors.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_group", schema = "stop_factors")
public class StopFactorGroup extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_group_id_seq")
    @SequenceGenerator(name = "stop_factor_group_id_seq", sequenceName = "stop_factor_group_id_generator", schema = "stop_factors", allocationSize = 1)
    private Long id;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "order_number")
    private int orderNumber;

    @Column(name = "root_id", updatable = false)
    private Long rootId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    /** The stop factors of this version, in order. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNumber")
    private List<StopFactor> factors = new ArrayList<>();

    /**
     * The display name: like its only stop factor, otherwise the titles of its metrics in the order of its stop
     * factors - one title when they all measure the same metric.
     */
    public String getName() {
        if (factors.size() == 1) {
            return factors.getFirst().getName();
        }
        return factors.stream()
                .map(factor -> factor.getMetric().getTitle())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    /** Moves the stop factors of a metric version to its next version: the metric was changed, the group follows it. */
    public void useMetric(StopFactorMetric previous, StopFactorMetric next) {
        factors.stream()
                .filter(factor -> factor.getMetric().getId().equals(previous.getId()))
                .forEach(factor -> factor.setMetric(next));
    }

    public boolean isCurrent() {
        return validTo == null;
    }

    /** Removes every stop factor, to set the stop factors of a new version anew. */
    public void removeFactors() {
        factors.clear();
    }

    /** Adds a stop factor after the existing ones. */
    public StopFactor addFactor() {
        StopFactor factor = new StopFactor(this, factors.size() + 1);
        factors.add(factor);
        return factor;
    }

    public Long getGroupId() {
        return rootId != null ? rootId : id;
    }

    public StopFactorGroup newVersion(LocalDateTime now, String reason) {
        StopFactorGroup next = new StopFactorGroup();
        next.rootId = getGroupId();                  // always the FIRST version, never the previous one
        next.versionNumber = this.versionNumber + 1;
        next.validFrom = now;
        next.changeReason = reason;
        next.description = this.description;
        next.isActive = this.isActive;
        next.orderNumber = this.orderNumber;
        this.factors.forEach(factor -> next.factors.add(factor.copyTo(next)));
        return next;
    }
}
