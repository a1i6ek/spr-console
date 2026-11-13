package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorFormatter;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopFactorConsoleServiceImpl implements StopFactorConsoleService {

    private static final String CREATED_REASON = "Создание группы";
    private static final String REASON_REQUIRED = "Укажите причину изменения: она сохраняется в истории версий.";

    private final StopFactorGroupRepository groupRepository;
    private final StopFactorMetricRepository metricRepository;
    private final StopFactorRepository factorRepository;
    private final StopFactorValidation validator;

    // ================================================================================================
    // Reading
    // ================================================================================================

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorGroupDto> getAll(Boolean active, Long productId, ProductSegment segment, Long metricId) {
        // a product that does not exist gets no stop factors with a segment or a product limit
        ProductSegment productSegment = productId == null ? null : groupRepository.findProductsByIds(List.of(productId))
                .stream().findFirst().map(ProductProjection::getSegment).orElse(null);
        return groupRepository.findAllCurrent().stream()
                .filter(g -> active == null || g.isActive() == active)
                .filter(g -> productId == null || g.getFactors().stream().anyMatch(f -> f.appliesTo(productId, productSegment)))
                .filter(g -> segment == null || g.getFactors().stream().anyMatch(f -> f.getSegment() == null || f.getSegment() == segment))
                .filter(g -> metricId == null || g.getFactors().stream().anyMatch(f -> f.getMetric().getId().equals(metricId)))
                .map(StopFactorConsoleServiceImpl::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StopFactorGroupDto get(long groupId) {
        return toDto(findCurrent(groupId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorGroupDto> getVersions(long groupId) {
        List<StopFactorGroup> versions = groupRepository.findVersions(groupId);
        if (versions.isEmpty()) {
            throw notFound(groupId);
        }
        return versions.stream().map(StopFactorConsoleServiceImpl::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorResponse> searchFactors(Long groupId, Long metricId, ProductSegment segment, Long productId,
                                              Boolean active, String search) {
        // a product that does not exist gets no factors with a segment or a product limit
        ProductSegment productSegment = productId == null ? null : groupRepository.findProductsByIds(List.of(productId))
                .stream().findFirst().map(ProductProjection::getSegment).orElse(null);
        String needle = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        return groupRepository.findAllCurrent().stream()
                .filter(g -> groupId == null || g.getGroupId().equals(groupId))
                .flatMap(g -> g.getFactors().stream())
                .filter(f -> metricId == null || f.getMetric().getId().equals(metricId))
                .filter(f -> segment == null || f.getSegment() == null || f.getSegment() == segment)
                .filter(f -> productId == null || f.appliesTo(productId, productSegment))
                .filter(f -> active == null || f.isActive() == active)
                .filter(f -> needle.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || f.getDescription() != null && f.getDescription().toLowerCase(Locale.ROOT).contains(needle))
                .map(StopFactorConsoleServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StopFactorResponse getFactor(long factorId) {
        return toView(findFactor(factorId));
    }

    // ================================================================================================
    // Groups
    // ================================================================================================

    @Override
    @Transactional
    public StopFactorGroupDto create(StopFactorGroupRequest request) {
        List<ValidFactor> factors = validate(request, null, Set.of());

        StopFactorGroup group = new StopFactorGroup();
        group.setName(request.name().trim());
        group.setDescription(blankToNull(request.description()));
        group.setActive(request.active() == null || request.active());
        group.setOrderNumber(request.orderNumber() != null ? request.orderNumber() : groupRepository.findMaxCurrentOrderNumber() + 1);
        group.setValidFrom(LocalDateTime.now());
        group.setChangeReason(blankToNull(request.changeReason()) != null ? request.changeReason().trim() : CREATED_REASON);
        addFactors(group, factors);

        StopFactorGroup saved = groupRepository.saveAndFlush(group);
        log.info("Группа {} '{}' создана ({} стоп-факторов)", saved.getId(), saved.getName(), saved.getFactors().size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public StopFactorGroupDto update(long groupId, StopFactorGroupRequest request) {
        return applyUpdate(groupId, lockCurrent(groupId), request);
    }

    /**
     * The update of a group whose current version the caller has already locked. Every change of a group ends here.
     * Private, and called directly: calling the public @Transactional {@link #update} from inside this class would
     * bypass the Spring proxy, so its annotation would not apply.
     */
    private StopFactorGroupDto applyUpdate(long groupId, StopFactorGroup current, StopFactorGroupRequest request) {
        if (blankToNull(request.changeReason()) == null) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        Set<String> currentKeys = current.getFactors().stream().map(StopFactorConsoleServiceImpl::key).collect(Collectors.toSet());
        List<ValidFactor> factors = validate(request, groupId, currentKeys);

        String name = request.name().trim();
        String description = blankToNull(request.description());
        int orderNumber = request.orderNumber() != null ? request.orderNumber() : current.getOrderNumber();
        boolean active = request.active() != null ? request.active() : current.isActive();
        boolean unchanged = name.equals(current.getName())
                && Objects.equals(description, current.getDescription())
                && orderNumber == current.getOrderNumber()
                && active == current.isActive()
                && factors.stream().map(f -> key(f.factor())).toList()
                        .equals(current.getFactors().stream().map(StopFactorConsoleServiceImpl::key).toList());
        if (unchanged) {
            log.info("Группа {}: изменений нет, новая версия не создана", groupId);
            return toDto(current);
        }

        StopFactorGroup next = nextVersion(current, request.changeReason());
        next.removeFactors();
        addFactors(next, factors);
        next.setName(name);
        next.setDescription(description);
        next.setOrderNumber(orderNumber);
        next.setActive(active);
        StopFactorGroup saved = groupRepository.saveAndFlush(next);
        log.info("Группа {} обновлена до версии {}: {}", groupId, saved.getVersionNumber(), request.changeReason());
        return toDto(saved);
    }

    @Override
    @Transactional
    public StopFactorGroupDto changeStatus(long groupId, boolean active, String changeReason) {
        if (blankToNull(changeReason) == null) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        StopFactorGroup current = lockCurrent(groupId);
        if (current.isActive() == active) {
            return toDto(current);
        }
        StopFactorGroup next = nextVersion(current, changeReason);
        next.setActive(active);
        StopFactorGroup saved = groupRepository.saveAndFlush(next);
        log.info("Группа {} {} (версия {}): {}", groupId, active ? "включена" : "отключена", saved.getVersionNumber(), changeReason);
        return toDto(saved);
    }

    @Override
    @Transactional
    public List<StopFactorGroupDto> reorder(List<Long> groupIds) {
        Map<Long, StopFactorGroup> byGroupId = groupRepository.findAllCurrent().stream()
                .collect(Collectors.toMap(StopFactorGroup::getGroupId, g -> g));

        List<String> errors = new ArrayList<>();
        Set<Long> requested = new LinkedHashSet<>();
        for (Long groupId : groupIds) {
            if (groupId == null) {
                errors.add("Список групп содержит пустое значение.");
            } else if (!requested.add(groupId)) {
                errors.add("Группа №" + groupId + " указана в списке несколько раз.");
            } else if (!byGroupId.containsKey(groupId)) {
                errors.add("Группа №" + groupId + " не найдена.");
            }
        }
        byGroupId.keySet().stream().filter(id -> !requested.contains(id)).sorted()
                .forEach(id -> errors.add("В списке нет группы №" + id + ": порядок задаётся сразу для всех групп."));
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }

        int order = 1;
        for (Long groupId : requested) {
            byGroupId.get(groupId).setOrderNumber(order++);
        }
        groupRepository.flush();
        log.info("Порядок групп изменён: {}", requested);
        return requested.stream().map(byGroupId::get).map(StopFactorConsoleServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public void delete(long groupId, String changeReason) {
        if (blankToNull(changeReason) == null) {
            throw new BusinessValidationException("Укажите причину удаления: она сохраняется в истории версий.");
        }
        StopFactorGroup current = lockCurrent(groupId);
        // a last, inactive version records the reason and is closed at once
        StopFactorGroup last = nextVersion(current, changeReason);
        last.setActive(false);
        last.setValidTo(last.getValidFrom());
        groupRepository.saveAndFlush(last);
        log.info("Группа {} удалена: {}", groupId, changeReason);
    }

    // ================================================================================================
    // Stop factors of a group
    // ================================================================================================

    @Override
    @Transactional
    public StopFactorGroupDto addFactor(long groupId, StopFactorDto factor, String changeReason) {
        return changeFactors(groupId, factors -> {
            factors.add(factor);
            return factors;
        }, changeReason);
    }

    @Override
    @Transactional
    public StopFactorGroupDto updateFactor(long groupId, long factorId, StopFactorDto factor, String changeReason) {
        return replaceFactor(groupId, factorId, factor, changeReason);
    }

    @Override
    @Transactional
    public StopFactorGroupDto deleteFactor(long groupId, long factorId, String changeReason) {
        return changeFactors(groupId, factors -> {
            int index = indexOf(groupId, factors, factorId);
            if (factors.size() == 1) {
                throw new BusinessValidationException("Нельзя удалить единственный стоп-фактор группы №" + groupId
                        + ". Чтобы прекратить проверку, отключите или удалите группу.");
            }
            factors.remove(index);
            return factors;
        }, changeReason);
    }

    /** Changes the stop factors of the current version through {@link #applyUpdate}, validated and versioned the same way. */
    private StopFactorGroupDto changeFactors(long groupId, UnaryOperator<List<StopFactorDto>> change, String changeReason) {
        StopFactorGroup locked = lockCurrent(groupId);
        StopFactorGroupDto current = toDto(locked);
        List<StopFactorDto> factors = change.apply(new ArrayList<>(current.factors()));
        return applyUpdate(groupId, locked, new StopFactorGroupRequest(current.name(), current.description(),
                current.orderNumber(), current.active(), factors, changeReason));
    }

    /** Replaces one stop factor of the current version; shared by both updateFactor methods. */
    private StopFactorGroupDto replaceFactor(long groupId, long factorId, StopFactorDto factor, String changeReason) {
        return changeFactors(groupId, factors -> {
            factors.set(indexOf(groupId, factors, factorId), factor);
            return factors;
        }, changeReason);
    }

    private static int indexOf(long groupId, List<StopFactorDto> factors, long factorId) {
        for (int i = 0; i < factors.size(); i++) {
            if (factors.get(i).id() != null && factors.get(i).id() == factorId) {
                return i;
            }
        }
        throw new NotFoundException("Стоп-фактор №" + factorId + " не найден в текущей версии группы №" + groupId
                + ". Возможно, группа уже изменена: обновите страницу и повторите действие.");
    }

    // ================================================================================================
    // Stop factors across the groups
    // ================================================================================================

    @Override
    @Transactional
    public StopFactorResponse updateFactor(long factorId, StopFactorRequest request, String changeReason) {
        if (blankToNull(changeReason) == null) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        StopFactor factor = findEditableFactor(factorId);
        long groupId = factor.getGroup().getGroupId();
        // the new version keeps the factor in its place: the replacement comes back at the same index
        int index = factor.getOrderNumber() - 1;
        StopFactorDto replacement = new StopFactorDto(
                factorId,
                request.metricId(),
                null,
                request.name(),
                request.description(),
                request.active(),
                request.segment(),
                request.params() != null ? request.params() : Map.of(),
                request.conditions() != null ? request.conditions() : List.of(),
                request.productIds() != null ? request.productIds() : Set.of(),
                null);
        StopFactorGroupDto updated = replaceFactor(groupId, factorId, replacement, changeReason);
        return toView(updated.factors().get(index), updated);
    }

    @Override
    @Transactional
    public StopFactorResponse changeFactorStatus(long factorId, boolean active, String changeReason) {
        if (blankToNull(changeReason) == null) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        StopFactor factor = findEditableFactor(factorId);
        long groupId = factor.getGroup().getGroupId();
        StopFactorGroupDto current = toDto(findCurrent(groupId));
        int index = indexOf(groupId, current.factors(), factorId);
        StopFactorDto f = current.factors().get(index);
        if (f.active() == active) {
            return toView(f, current);
        }
        StopFactorDto changed = new StopFactorDto(f.id(), f.metricId(), f.metricTitle(), f.name(), f.description(),
                active, f.segment(), f.params(), f.conditions(), f.productIds(), f.summary());
        StopFactorGroupDto updated = replaceFactor(groupId, factorId, changed, changeReason);
        return toView(updated.factors().get(index), updated);
    }

    /** A factor that can be changed: it exists and belongs to the current version of its group. */
    private StopFactor findEditableFactor(long factorId) {
        StopFactor factor = findFactor(factorId);
        if (!factor.getGroup().isCurrent()) {
            throw new NotFoundException("Стоп-фактор №" + factorId + " относится к закрытой версии группы №"
                    + factor.getGroup().getGroupId() + ". Измените его в текущей версии группы.");
        }
        return factor;
    }

    private StopFactor findFactor(long factorId) {
        return factorRepository.findById(factorId)
                .orElseThrow(() -> new NotFoundException("Стоп-фактор №" + factorId + " не найден."));
    }

    // ================================================================================================
    // Validation and versions
    // ================================================================================================

    /** A stop factor of a request, normalized, with its metric. */
    private record ValidFactor(StopFactorDto factor, StopFactorMetric metric) {
    }

    /**
     * Validates the group and every stop factor against its metric. An inactive metric cannot be given to a new or
     * changed stop factor; one kept as it is (currentKeys) stays.
     */
    private List<ValidFactor> validate(StopFactorGroupRequest request, Long groupId, Set<String> currentKeys) {
        List<String> errors = new ArrayList<>();
        if (request.name() == null || request.name().isBlank()) {
            errors.add("Укажите название группы.");
        } else {
            String name = request.name().trim();
            groupRepository.findAllCurrent().stream()
                    .filter(g -> g.getName().equals(name) && !g.getGroupId().equals(groupId))
                    .findFirst()
                    .ifPresent(g -> errors.add("Группа «" + name + "» уже есть (№" + g.getGroupId() + ")."));
        }
        List<StopFactorDto> requested = request.factors() != null ? request.factors() : List.of();
        if (requested.isEmpty()) {
            errors.add("Добавьте хотя бы один стоп-фактор.");
        }

        Map<Long, StopFactorMetric> metrics = metricRepository.findAllById(requested.stream()
                        .map(StopFactorDto::metricId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(StopFactorMetric::getId, m -> m));
        List<ValidFactor> factors = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            StopFactorDto factor = requested.get(i);
            List<String> factorErrors = new ArrayList<>();
            StopFactorMetric metric = factor.metricId() != null ? metrics.get(factor.metricId()) : null;
            if (metric == null) {
                factorErrors.add(factor.metricId() == null ? "Укажите метрику." : "Метрика №" + factor.metricId() + " не найдена.");
            } else {
                StopFactorDto valid = validator.validate(factor, metric, factorErrors);
                if (valid != null && !metric.isActive() && valid.active() && !currentKeys.contains(key(valid))) {
                    factorErrors.add("Метрика «" + metric.getTitle() + "» отключена. Включите её или выберите другую.");
                }
                if (valid != null) {
                    factors.add(new ValidFactor(valid, metric));
                }
            }
            int number = i + 1;
            factorErrors.forEach(error -> errors.add("Стоп-фактор " + number + ": " + lowerFirst(error)));
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
        return factors;
    }

    /** The current version of a group, after locking its first version so concurrent changes run one after another. */
    private StopFactorGroup lockCurrent(long groupId) {
        groupRepository.lockGroup(groupId).orElseThrow(() -> notFound(groupId));
        return findCurrent(groupId);
    }

    /**
     * Closes the current version and returns the next one, not saved yet, with copies of every stop factor. The
     * current version is flushed first: a group has one current version (ux_stop_factor_group_current_version).
     */
    private StopFactorGroup nextVersion(StopFactorGroup current, String changeReason) {
        LocalDateTime now = LocalDateTime.now();
        current.setValidTo(now);
        groupRepository.saveAndFlush(current);
        return current.newVersion(now, changeReason.trim());
    }

    private static void addFactors(StopFactorGroup group, List<ValidFactor> factors) {
        for (ValidFactor valid : factors) {
            StopFactorDto f = valid.factor();
            StopFactor factor = group.addFactor();
            factor.setMetric(valid.metric());
            factor.setName(f.name());
            factor.setDescription(f.description());
            factor.setActive(f.active());
            factor.setSegment(f.segment());
            factor.setParams(new LinkedHashMap<>(f.params()));
            factor.setProductIds(new HashSet<>(f.productIds()));
            f.conditions().forEach(c -> factor.addCondition(c.result(), c.operator(), c.value(), c.valueTo(), c.valueType()));
        }
    }

    private StopFactorGroup findCurrent(long groupId) {
        return groupRepository.findCurrent(groupId).orElseThrow(() -> notFound(groupId));
    }

    private static NotFoundException notFound(long groupId) {
        return new NotFoundException("Группа стоп-факторов №" + groupId + " не найдена.");
    }

    // ================================================================================================
    // Mapping
    // ================================================================================================

    /** What makes two stop factors the same rule. */
    private static String key(StopFactorDto f) {
        return String.join("|", String.valueOf(f.metricId()), f.name(), String.valueOf(f.description()),
                String.valueOf(f.active()), String.valueOf(f.segment()), new TreeMap<>(f.params()).toString(),
                f.conditions().stream().sorted(Comparator.comparing(ConditionDto::result)).toList().toString(),
                new TreeSet<>(f.productIds()).toString());
    }

    private static String key(StopFactor factor) {
        return key(toDto(factor));
    }

    private static StopFactorGroupDto toDto(StopFactorGroup g) {
        return new StopFactorGroupDto(
                g.getGroupId(),
                g.getId(),
                g.getVersionNumber(),
                g.isCurrent(),
                g.getName(),
                g.getDescription(),
                g.isActive(),
                g.getOrderNumber(),
                g.getFactors().stream().map(StopFactorConsoleServiceImpl::toDto).toList(),
                g.getValidFrom(),
                g.getValidTo(),
                g.getChangeReason());
    }

    private static StopFactorDto toDto(StopFactor f) {
        return new StopFactorDto(
                f.getId(),
                f.getMetric().getId(),
                f.getMetric().getTitle(),
                f.getName(),
                f.getDescription(),
                f.isActive(),
                f.getSegment(),
                new TreeMap<>(f.getParams()),
                f.getConditions().stream()
                        .map(c -> new ConditionDto(c.getResult(), c.getOperator(), c.getValue(), c.getValueTo(), c.getValueType()))
                        .sorted(Comparator.comparing(ConditionDto::result))
                        .toList(),
                new TreeSet<>(f.getProductIds()),
                StopFactorFormatter.summary(f.getMetric().getTitle(), f.getMetric().getParams(), f.getParams()));
    }

    /** A factor with where it lives: the group version and the metric. */
    private static StopFactorResponse toView(StopFactor f) {
        return new StopFactorResponse(
                f.getId(),
                f.getName(),
                f.getDescription(),
                f.isActive(),
                f.getSegment(),
                new TreeMap<>(f.getParams()),
                f.getConditions().stream()
                        .map(c -> new ConditionDto(c.getResult(), c.getOperator(), c.getValue(), c.getValueTo(), c.getValueType()))
                        .sorted(Comparator.comparing(ConditionDto::result))
                        .toList(),
                new TreeSet<>(f.getProductIds()),
                new StopFactorResponse.MetricRef(f.getMetric().getId(), f.getMetric().getTitle()),
                new StopFactorResponse.GroupRef(f.getGroup().getGroupId(), f.getGroup().getId(), f.getGroup().getVersionNumber(),
                        f.getGroup().getName(), f.getGroup().isActive(), f.getGroup().getOrderNumber()),
                StopFactorFormatter.summary(f.getMetric().getTitle(), f.getMetric().getParams(), f.getParams()));
    }

    /** A factor of a group DTO as a view, with the group it belongs to. */
    private static StopFactorResponse toView(StopFactorDto f, StopFactorGroupDto g) {
        return new StopFactorResponse(
                f.id(),
                f.name(),
                f.description(),
                f.active(),
                f.segment(),
                f.params(),
                f.conditions(),
                f.productIds(),
                new StopFactorResponse.MetricRef(f.metricId(), f.metricTitle()),
                new StopFactorResponse.GroupRef(g.groupId(), g.versionId(), g.versionNumber(), g.name(), g.active(),
                        g.orderNumber()),
                f.summary());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "Укажите «Период с»." continued after "Стоп-фактор 2: "; an abbreviation (КИБ) keeps its case. */
    private static String lowerFirst(String sentence) {
        if (sentence.length() < 2 || !Character.isUpperCase(sentence.charAt(0)) || Character.isUpperCase(sentence.charAt(1))) {
            return sentence;
        }
        return Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
    }
}
