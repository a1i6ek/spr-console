package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorFormatter;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.dto.StopFactorResponse.Checks;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.validation.StopFactorValidation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Stop factors in the console. Every change is made the same way ({@link #change}): the first box is locked, the
 * current box is closed and the next one gets copies of all its stop factors with the change applied. The changed
 * stop factor gets its next version number, the others keep theirs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopFactorConsoleServiceImpl implements StopFactorConsoleService {

    private static final String CREATED_REASON = "Создание стоп-фактора";
    private static final String REASON_REQUIRED = "Укажите причину изменения: она сохраняется в истории стоп-фактора.";

    private final StopFactorRepository factorRepository;
    private final StopFactorGroupRepository groupRepository;
    private final StopFactorMetricRepository metricRepository;
    private final StopFactorValidation validator;

    // ================================================================================================
    // Reading
    // ================================================================================================

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorResponse> search(Long metricId, ProductSegment segment, Long productId, Boolean active,
                                           String search) {
        // a product that does not exist gets no stop factors with a segment or a product limit
        ProductSegment productSegment = productId == null ? null : factorRepository.findProductsByIds(List.of(productId))
                .stream().findFirst().map(ProductProjection::getSegment).orElse(null);
        String needle = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        Map<Long, VersionStart> starts = versionStarts();
        return factorRepository.findAllCurrent().stream()
                .filter(f -> metricId == null || f.getMetric().getId().equals(metricId))
                .filter(f -> segment == null || f.getSegment() == null || f.getSegment() == segment)
                .filter(f -> productId == null || f.appliesTo(productId, productSegment))
                .filter(f -> active == null || f.isActive() == active)
                .filter(f -> needle.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || f.getDescription() != null && f.getDescription().toLowerCase(Locale.ROOT).contains(needle))
                .map(f -> current(f, starts))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StopFactorResponse get(long factorId) {
        return current(findCurrent(factorId), versionStarts());
    }

    /**
     * The versions of a stop factor, newest first, read from the boxes it was in: a version lasts from the first box
     * it was in to the last one. Its reason is the reason of the box it first appeared in; a stop factor missing from
     * the box after its last one was deleted with that box's reason.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StopFactorResponse> getVersions(long factorId) {
        List<StopFactor> rows = factorRepository.findHistory(factorId);
        if (rows.isEmpty()) {
            throw notFound(factorId);
        }
        Map<Integer, long[]> counts = new HashMap<>();
        for (Object[] row : factorRepository.countChecks(factorId)) {
            long[] c = counts.computeIfAbsent(((Number) row[0]).intValue(), v -> new long[3]);
            long n = ((Number) row[2]).longValue();
            c[0] += n;
            if (StopFactorStatus.REJECTED.name().equals(row[1])) c[1] += n;
            if (StopFactorStatus.UNDERWRITER.name().equals(row[1])) c[2] += n;
        }

        List<StopFactorResponse> versions = new ArrayList<>();
        int from = 0;
        for (int i = 1; i <= rows.size(); i++) {
            if (i < rows.size() && rows.get(i).getVersionNumber() == rows.get(from).getVersionNumber()) {
                continue;
            }
            StopFactor first = rows.get(from);
            StopFactor last = rows.get(i - 1);
            String endReason = i == rows.size() && !last.getGroup().isCurrent()
                    ? groupRepository.findByVersionNumber(last.getGroup().getVersionNumber() + 1)
                            .map(StopFactorGroup::getChangeReason).orElse(null)
                    : null;
            long[] c = counts.get(first.getVersionNumber());
            versions.add(toResponse(first, last.getGroup().isCurrent(), first.getGroup().getValidFrom(),
                    last.getGroup().getValidTo(), first.getGroup().getChangeReason(), endReason, c == null ? Checks.NONE : new Checks(c[0], c[1], c[2])));
            from = i;
        }
        Collections.reverse(versions);
        return versions;
    }

    // ================================================================================================
    // Writing
    // ================================================================================================

    @Override
    @Transactional
    public StopFactorResponse create(StopFactorRequest request) {
        String reason = blankToNull(request.changeReason()) != null ? request.changeReason().trim() : CREATED_REASON;
        StopFactorGroup current = lockCurrentBox();
        Valid valid = validate(request, null);

        long factorId = factorRepository.nextFactorId();
        int order = current.getFactors().stream().mapToInt(StopFactor::getOrderNumber).max().orElse(0) + 1;
        StopFactorGroup next = change(current, reason, null, null);
        StopFactor created = StopFactor.first(next, factorId, order);
        apply(created, valid);
        next.add(created);
        groupRepository.saveAndFlush(next);

        log.info("Стоп-фактор {} '{}' создан, ящик {}", factorId, created.getName(), next.getVersionNumber());
        return current(created, Map.of());
    }

    @Override
    @Transactional
    public StopFactorResponse update(long factorId, StopFactorRequest request) {
        requireReason(request.changeReason());
        StopFactorGroup box = lockCurrentBox();
        StopFactor current = findIn(box, factorId);
        Valid valid = validate(request, current);

        if (key(valid.factor()).equals(key(toDto(current)))) {
            log.info("Стоп-фактор {}: изменений нет, новый ящик не создан", factorId);
            return current(current, versionStarts());
        }
        StopFactor changed = save(box, request.changeReason(), factorId, f -> apply(f, valid));
        log.info("Стоп-фактор {} изменён, версия {}: {}", factorId, changed.getVersionNumber(), request.changeReason());
        return current(changed, Map.of());
    }

    @Override
    @Transactional
    public StopFactorResponse changeStatus(long factorId, boolean active, String changeReason) {
        requireReason(changeReason);
        StopFactorGroup box = lockCurrentBox();
        StopFactor current = findIn(box, factorId);
        if (current.isActive() == active) {
            return current(current, versionStarts());
        }
        // switching on is changing the rule: its metric must still be one a stop factor can be given
        if (active && !current.getMetric().isActive()) {
            throw new BusinessValidationException("Метрика «" + current.getMetric().getTitle()
                    + "» отключена. Включите её или выберите для стоп-фактора другую.");
        }
        StopFactor changed = save(box, changeReason, factorId, f -> f.setActive(active));
        log.info("Стоп-фактор {} {} (версия {}): {}", factorId, active ? "включён" : "отключён", changed.getVersionNumber(), changeReason);
        return current(changed, Map.of());
    }

    @Override
    @Transactional
    public void delete(long factorId, String changeReason) {
        requireReason(changeReason);
        StopFactorGroup box = lockCurrentBox();
        findIn(box, factorId);
        // the next box without it
        groupRepository.saveAndFlush(change(box, changeReason.trim(), factorId, null));
        log.info("Стоп-фактор {} удалён: {}", factorId, changeReason);
    }

    // ================================================================================================
    // Boxes
    // ================================================================================================

    /** The current box, after locking the first one so changes run one after another. */
    private StopFactorGroup lockCurrentBox() {
        groupRepository.lock();
        return groupRepository.findCurrent().orElseThrow(() -> new IllegalStateException("Нет текущего набора стоп-факторов"));
    }

    /**
     * Closes the current box and returns the next one, not saved yet, with copies of its stop factors. The stop factor
     * {@code factorId} gets its next version with {@code change} applied, or is left out when {@code change} is null
     * (deleted); with no {@code factorId} every stop factor is copied as it is. The current box is flushed first:
     * exactly one box is current (ux_stop_factor_group_current).
     */
    private StopFactorGroup change(StopFactorGroup current, String reason, Long factorId, Consumer<StopFactor> change) {
        LocalDateTime now = LocalDateTime.now();
        current.setValidTo(now);
        groupRepository.saveAndFlush(current);
        StopFactorGroup next = current.next(now, reason.trim());
        for (StopFactor factor : current.getFactors()) {
            if (!factor.getFactorId().equals(factorId)) {
                next.add(factor.copyTo(next));
            } else if (change != null) {
                StopFactor changed = factor.copyTo(next);
                changed.setVersionNumber(factor.getVersionNumber() + 1);
                change.accept(changed);
                next.add(changed);
            }
        }
        return next;
    }

    /** The next box with the change of one stop factor; returns that stop factor in it. */
    private StopFactor save(StopFactorGroup box, String reason, long factorId, Consumer<StopFactor> change) {
        StopFactorGroup next = groupRepository.saveAndFlush(change(box, reason, factorId, change));
        return findIn(next, factorId);
    }

    private static StopFactor findIn(StopFactorGroup box, long factorId) {
        return box.getFactors().stream().filter(f -> f.getFactorId() == factorId).findFirst()
                .orElseThrow(() -> notFound(factorId));
    }

    private StopFactor findCurrent(long factorId) {
        return factorRepository.findCurrent(factorId).orElseThrow(() -> notFound(factorId));
    }

    // ================================================================================================
    // Validation
    // ================================================================================================

    /** A request, normalized, with its metric. */
    private record Valid(StopFactorDto factor, StopFactorMetric metric) {
    }

    /**
     * Validates a request against its metric. An inactive metric cannot be given to a new or changed stop factor;
     * a stop factor that keeps the metric it has keeps it even when it was switched off.
     */
    private Valid validate(StopFactorRequest request, StopFactor current) {
        List<String> errors = new ArrayList<>();
        StopFactorMetric metric = request.metricId() == null ? null : metricRepository.findById(request.metricId()).orElse(null);
        StopFactorDto valid = null;
        if (metric == null) {
            errors.add(request.metricId() == null ? "Укажите метрику." : "Метрика №" + request.metricId() + " не найдена.");
        } else {
            valid = validator.validate(toDto(request), metric, errors);
            boolean keepsMetric = current != null && current.getMetric().getId().equals(metric.getId());
            if (valid != null && !metric.isActive() && valid.active() && !keepsMetric) {
                errors.add("Метрика «" + metric.getTitle() + "» отключена. Включите её или выберите другую.");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
        return new Valid(valid, metric);
    }

    /** The rule of a validated request, set on the changed copy. */
    private static void apply(StopFactor factor, Valid valid) {
        StopFactorDto f = valid.factor();
        factor.setMetric(valid.metric());
        factor.setName(f.name());
        factor.setDescription(f.description());
        factor.setActive(f.active());
        factor.setSegment(f.segment());
        factor.setParams(new LinkedHashMap<>(f.params()));
        factor.setProductIds(new HashSet<>(f.productIds()));
        factor.removeConditions();
        f.conditions().forEach(c -> factor.addCondition(c.result(), c.operator(), c.value(), c.valueTo(), c.valueType()));
    }

    private static NotFoundException notFound(long factorId) {
        return new NotFoundException("Стоп-фактор №" + factorId + " не найден.");
    }

    private static void requireReason(String changeReason) {
        if (blankToNull(changeReason) == null) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
    }

    // ================================================================================================
    // Mapping
    // ================================================================================================

    /** What makes two versions the same rule. */
    private static String key(StopFactorDto f) {
        return String.join("|", String.valueOf(f.metricId()), f.name(), String.valueOf(f.description()),
                String.valueOf(f.active()), String.valueOf(f.segment()), new TreeMap<>(f.params()).toString(),
                f.conditions().stream().sorted(Comparator.comparing(ConditionDto::result)).toList().toString(),
                new TreeSet<>(f.productIds()).toString());
    }

    private static StopFactorDto toDto(StopFactorRequest r) {
        return new StopFactorDto(null, r.metricId(), null, r.name(), r.description(), r.active(), r.segment(),
                r.params() != null ? r.params() : Map.of(),
                r.conditions() != null ? r.conditions() : List.of(),
                r.productIds() != null ? r.productIds() : Set.of(),
                null);
    }

    private static StopFactorDto toDto(StopFactor f) {
        return new StopFactorDto(f.getId(), f.getMetric().getId(), f.getMetric().getTitle(), f.getName(),
                f.getDescription(), f.isActive(), f.getSegment(), new TreeMap<>(f.getParams()), conditions(f),
                new TreeSet<>(f.getProductIds()), summary(f));
    }

    /** When and why the current version of a stop factor started: the first box it was in. */
    private record VersionStart(LocalDateTime validFrom, String changeReason) {
    }

    private Map<Long, VersionStart> versionStarts() {
        Map<Long, VersionStart> starts = new HashMap<>();
        for (Object[] row : factorRepository.findCurrentVersionStarts()) {
            Object from = row[1];
            LocalDateTime validFrom = from instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) from;
            starts.put(((Number) row[0]).longValue(), new VersionStart(validFrom, (String) row[2]));
        }
        return starts;
    }

    /**
     * A stop factor of the current box with when and why its current version started; a version made by this very
     * change started with its box.
     */
    private static StopFactorResponse current(StopFactor f, Map<Long, VersionStart> starts) {
        VersionStart start = starts.getOrDefault(f.getFactorId(),
                new VersionStart(f.getGroup().getValidFrom(), f.getGroup().getChangeReason()));
        return toResponse(f, true, start.validFrom(), null, start.changeReason(), null, null);
    }

    private static StopFactorResponse toResponse(StopFactor f, boolean current, LocalDateTime validFrom,
                                                 LocalDateTime validTo, String changeReason, String endReason,
                                                 Checks checks) {
        return new StopFactorResponse(
                f.getFactorId(),
                f.getId(),
                f.getVersionNumber(),
                current,
                new StopFactorResponse.MetricRef(f.getMetric().getId(), f.getMetric().getTitle()),
                f.getName(),
                f.getDescription(),
                f.isActive(),
                f.getSegment(),
                new TreeMap<>(f.getParams()),
                conditions(f),
                new TreeSet<>(f.getProductIds()),
                summary(f),
                f.getOrderNumber(),
                validFrom,
                validTo,
                changeReason,
                endReason,
                checks);
    }

    private static List<ConditionDto> conditions(StopFactor f) {
        return f.getConditions().stream()
                .map(c -> new ConditionDto(c.getResult(), c.getOperator(), c.getValue(), c.getValueTo(), c.getValueType()))
                .sorted(Comparator.comparing(ConditionDto::result))
                .toList();
    }

    private static String summary(StopFactor f) {
        return StopFactorFormatter.summary(f.getMetric().getTitle(), f.getMetric().getParams(), f.getParams());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
