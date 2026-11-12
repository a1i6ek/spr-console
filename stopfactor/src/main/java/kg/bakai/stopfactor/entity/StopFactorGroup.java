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
 * One version of a group of stop factors. A change of any of its stop factors closes the version (valid_to) and
 * creates the next one with copies of all of them, so a check keeps pointing to the rules it ran with. The group id
 * is the id of its first version.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "stop_factor_group", schema = "stop_factors")
public class StopFactorGroup extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stop_factor_group_id_seq")
    @SequenceGenerator(name = "stop_factor_group_id_seq", sequenceName = "stop_factor_group_id_generator", schema = "public", allocationSize = 1)
    private Long id;

    /** The first version; null for the first version itself. */
    @Column(name = "root_id", updatable = false)
    private Long rootId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber = 1;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    /** Order of the groups in the console; changed in place, it does not affect decisions. */
    @Column(name = "order_number", nullable = false)
    private int orderNumber;

    /** An inactive group checks none of its stop factors. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "change_reason")
    private String changeReason;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNumber, id")
    private List<StopFactor> factors = new ArrayList<>();

    /** The id of the group: the id of its first version, the same for all its versions. */
    public Long getGroupId() {
        return rootId != null ? rootId : id;
    }

    public boolean isCurrent() {
        return validTo == null;
    }

    /** Adds a stop factor after the others. */
    public StopFactor addFactor() {
        StopFactor factor = new StopFactor(this, factors.size() + 1);
        factors.add(factor);
        return factor;
    }

    /** The next version, valid from now, with copies of every stop factor; the caller closes this one. */
    public StopFactorGroup newVersion(LocalDateTime now, String reason) {
        StopFactorGroup next = new StopFactorGroup();
        next.rootId = getGroupId();
        next.versionNumber = versionNumber + 1;
        next.validFrom = now;
        next.changeReason = reason;
        next.name = name;
        next.description = description;
        next.orderNumber = orderNumber;
        next.active = active;
        factors.forEach(factor -> factor.copyTo(next.addFactor()));
        return next;
    }

    /** Removes every stop factor, to set the stop factors of a new version anew. */
    public void removeFactors() {
        factors.clear();
    }
}
