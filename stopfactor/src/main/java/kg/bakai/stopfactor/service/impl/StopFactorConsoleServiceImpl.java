package kg.bakai.stopfactor.service.impl;

import kg.bakai.stopfactor.validator.StopFactorDefinitionValidator;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.util.TextUtils;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorCondition;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;

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

    private static final String CREATED_REASON = "Создание группы стоп-факторов";
    private static final String REASON_REQUIRED = "Укажите причину изменения: она сохраняется в истории версий.";

    private final StopFactorGroupRepository groupRepository;
    private final StopFactorMetricRepository metricRepository;
    private final StopFactorGroupVersions groupVersions;
    private final StopFactorDefinitionValidator validator;

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorResponse> getAll(Boolean active, Long metricId, Long productId, ProductSegment segment, LocalDateTime at) {
        // a product that does not exist gets no stop factors with a segment or a product limit
        ProductSegment productSegment = productId == null ? null : groupRepository.findProductsByIds(List.of(productId)).stream()
                .findFirst().map(ProductProjection::getSegment).orElse(null);
        // a group is listed when any of its stop factors matches the product and the segment
        List<StopFactorGroup> versions = at == null ? groupRepository.findAllCurrentWithFactorsAndMetric() : groupRepository.findAllAt(at);
        List<StopFactorGroup> groups = versions.stream()
                .filter(g -> active == null || g.isActive() == active)
                .filter(g -> metricId == null || g.getFactors().stream().anyMatch(f -> f.getMetric().getMetricId().equals(metricId)))
                .filter(g -> productId == null || g.getFactors().stream().anyMatch(f -> f.appliesTo(productId, productSegment)))
                .filter(g -> segment == null || g.getFactors().stream()
                        .anyMatch(f -> f.getSegment() == null || f.getSegment() == segment))
                .toList();
        return toResponses(groups);
    }

    @Override
    @Transactional(readOnly = true)
    public StopFactorResponse get(long groupId) {
        return toResponses(List.of(findCurrent(groupId))).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorResponse> getVersions(long groupId) {
        List<StopFactorGroup> versions = groupRepository.findVersions(groupId);
        if (versions.isEmpty()) {
            throw notFound(groupId);
        }
        return toResponses(versions);
    }

    @Override
    @Transactional
    public StopFactorResponse create(StopFactorRequest request) {
        List<String> errors = new ArrayList<>();
        Definition definition = validateDefinition(request, Set.of(), errors);
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }

        StopFactorGroup group = new StopFactorGroup();
        addFactors(group, definition.factors(), Map.of());
        group.setDescription(blankToNull(request.description()));
        group.setActive(request.active() == null || request.active());
        group.setOrderNumber(request.orderNumber() != null
                ? request.orderNumber()
                : groupRepository.findMaxCurrentOrderNumber() + 1);
        group.setValidFrom(LocalDateTime.now());
        group.setChangeReason(request.changeReason() != null && !request.changeReason().isBlank()
                ? request.changeReason().trim()
                : CREATED_REASON);
        group.setCreatedBy("");

        StopFactorGroup saved = groupRepository.save(group);
        log.info("Группа стоп-факторов {} '{}' ({} стоп-факторов) создана", saved.getGroupId(), saved.getName(),
                saved.getFactors().size());
        return get(saved.getGroupId());
    }

    @Override
    @Transactional
    public StopFactorResponse update(long groupId, StopFactorRequest request) {
        StopFactorGroup current = groupVersions.lockCurrent(groupId);
        int orderNumber = request.orderNumber() != null ? request.orderNumber() : current.getOrderNumber();
        boolean active = request.active() != null ? request.active() : current.isActive();

        List<String> errors = new ArrayList<>();
        if (request.changeReason() == null || request.changeReason().isBlank()) {
            errors.add(REASON_REQUIRED);
        }
        Set<String> currentFactors = current.getFactors().stream().map(StopFactorConsoleServiceImpl::identityKey).collect(Collectors.toSet());
        Definition definition = validateDefinition(request, currentFactors, errors);
        if (active && !current.isActive()) {
            definition.factors().stream()
                    .filter(f -> f.active() && f.metric() != null && !f.metric().isActive())
                    .map(f -> metricOff(f.metric()))
                    .distinct()
                    .forEach(errors::add);
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
        List<FactorDefinition> factors = definition.factors();
        boolean factorsChanged = !factorKeys(factors).equals(factorKeysOf(current.getFactors()))
                || renamesFactors(factors, current.getFactors());
        String description = blankToNull(request.description());

        boolean unchanged = !factorsChanged
                && Objects.equals(description, current.getDescription())
                && orderNumber == current.getOrderNumber()
                && active == current.isActive();
        if (unchanged) {
            log.info("Группа {}: изменений нет, новая версия не создана", groupId);
            return get(groupId);
        }

        StopFactorGroup next = groupVersions.next(current, request.changeReason());
        if (factorsChanged) {
            Map<String, String> currentNames = current.getFactors().stream()
                    .collect(Collectors.toMap(StopFactorConsoleServiceImpl::identityKey, StopFactor::getName, (a, b) -> a));
            next.removeFactors();
            addFactors(next, factors, currentNames);
        }
        next.setDescription(description);
        next.setOrderNumber(orderNumber);
        next.setActive(active);
        StopFactorGroup saved = groupRepository.save(next);
        log.info("Группа {} обновлена до версии {}: {}", groupId, saved.getVersionNumber(), request.changeReason());
        return get(groupId);
    }

    @Override
    @Transactional
    public StopFactorResponse changeStatus(long groupId, boolean active, String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        StopFactorGroup current = groupVersions.lockCurrent(groupId);
        if (current.isActive() == active) {
            return get(groupId);
        }
        if (active) {
            List<String> metricsOff = current.getFactors().stream()
                    .filter(f -> f.isActive() && !f.getMetric().isActive())
                    .map(f -> metricOff(f.getMetric()))
                    .distinct()
                    .toList();
            if (!metricsOff.isEmpty()) {
                throw new BusinessValidationException(metricsOff);
            }
        }

        StopFactorGroup next = groupVersions.next(current, changeReason);
        next.setActive(active);
        StopFactorGroup saved = groupRepository.save(next);
        log.info("Группа {} {} (версия {}): {}", groupId, active ? "активирована" : "деактивирована",
                saved.getVersionNumber(), changeReason);
        return get(groupId);
    }

    @Override
    @Transactional
    // The order does not change any decision, so it is updated in place without a new version
    public List<StopFactorResponse> reorder(List<Long> groupIds) {
        List<StopFactorGroup> currents = groupRepository.findAllCurrentWithFactorsAndMetric();
        Map<Long, StopFactorGroup> byGroupId = currents.stream()
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
        byGroupId.keySet().stream()
                .filter(id -> !requested.contains(id))
                .sorted()
                .forEach(id -> errors.add("В списке нет группы №" + id + ": порядок задаётся сразу для всех групп."));
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }

        int order = 1;
        for (Long groupId : requested) {
            byGroupId.get(groupId).setOrderNumber(order++);
        }
        groupRepository.flush();
        log.info("Порядок групп изменен: {}", requested);
        return toResponses(groupRepository.findAllCurrentWithFactorsAndMetric());
    }

    @Override
    @Transactional
    public void delete(long groupId, String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessValidationException("Укажите причину удаления: она сохраняется в истории версий.");
        }
        StopFactorGroup current = groupVersions.lockCurrent(groupId);
        StopFactorGroup last = groupVersions.next(current, changeReason);
        last.setActive(false);
        last.setValidTo(last.getValidFrom());
        StopFactorGroup saved = groupRepository.save(last);
        log.info("Группа {} удалена (версия {}): {}", groupId, saved.getVersionNumber(), changeReason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorDto> getFactors(long groupId) {
        return get(groupId).factors();
    }

    @Override
    @Transactional(readOnly = true)
    public StopFactorDto getFactor(long groupId, long factorId) {
        List<StopFactorDto> factors = getFactors(groupId);
        return factors.get(indexOfFactor(groupId, factors, factorId));
    }

    @Override
    @Transactional
    public StopFactorResponse addFactor(long groupId, StopFactorDto factor, String changeReason) {
        return changeFactors(groupId, factors -> {
            factors.add(factor);
            return factors;
        }, changeReason);
    }

    @Override
    @Transactional
    public StopFactorResponse updateFactor(long groupId, long factorId, StopFactorDto factor, String changeReason) {
        return changeFactors(groupId, factors -> {
            factors.set(indexOfFactor(groupId, factors, factorId), factor);
            return factors;
        }, changeReason);
    }

    @Override
    @Transactional
    public StopFactorResponse deleteFactor(long groupId, long factorId, String changeReason) {
        return changeFactors(groupId, factors -> {
            int index = indexOfFactor(groupId, factors, factorId);
            if (factors.size() == 1) {
                throw new BusinessValidationException("Нельзя удалить единственный стоп-фактор группы №" + groupId
                        + ". Чтобы прекратить проверку, отключите или удалите группу.");
            }
            factors.remove(index);
            return factors;
        }, changeReason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConditionDto> getConditions(long groupId, long factorId) {
        return getFactor(groupId, factorId).conditions();
    }

    @Override
    @Transactional
    public StopFactorResponse saveCondition(long groupId, long factorId, ConditionDto condition, String changeReason) {
        return changeFactor(groupId, factorId, conditions -> {
            conditions.removeIf(c -> c.result() == condition.result());
            conditions.add(condition);
            return conditions;
        }, changeReason);
    }

    @Override
    @Transactional
    public StopFactorResponse deleteCondition(long groupId, long factorId, StopFactorStatus result, String changeReason) {
        return changeFactor(groupId, factorId, conditions -> {
            if (!conditions.removeIf(c -> c.result() == result)) {
                throw new NotFoundException("У стоп-фактора нет условия «" + result.getLabel() + "».");
            }
            return conditions;
        }, changeReason);
    }

    /** Changes the conditions of one stop factor; it keeps its name, params, status, segment and products. */
    private StopFactorResponse changeFactor(long groupId, long factorId, UnaryOperator<List<ConditionDto>> change,
                                            String changeReason) {
        return changeFactors(groupId, factors -> {
            int index = indexOfFactor(groupId, factors, factorId);
            StopFactorDto factor = factors.get(index);
            factors.set(index, factor.withConditions(change.apply(new ArrayList<>(factor.conditions()))));
            return factors;
        }, changeReason);
    }

    /**
     * Changes the stop factors of the current version through {@link #update}, so they are validated and versioned
     * the same way. The group and the other stop factors keep their names.
     */
    private StopFactorResponse changeFactors(long groupId, UnaryOperator<List<StopFactorDto>> change, String changeReason) {
        groupVersions.lockCurrent(groupId);
        StopFactorResponse current = get(groupId);
        List<StopFactorDto> factors = change.apply(new ArrayList<>(current.factors()));
        return update(groupId, new StopFactorRequest(current.description(), current.orderNumber(), current.active(),
                factors, changeReason));
    }

    private static int indexOfFactor(long groupId, List<StopFactorDto> factors, long factorId) {
        for (int i = 0; i < factors.size(); i++) {
            if (factors.get(i).id() != null && factors.get(i).id() == factorId) {
                return i;
            }
        }
        throw new NotFoundException("Стоп-фактор не найден в текущей версии группы №" + groupId
                + ". Возможно, группа уже изменена: обновите страницу и повторите действие.");
    }

    /** A stop factor name given in the request that differs from the current one; a name left out keeps the current one. */
    private static boolean renamesFactors(List<FactorDefinition> factors, List<StopFactor> current) {
        if (factors.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < factors.size(); i++) {
            String name = factors.get(i).name();
            if (name != null && !name.equals(current.get(i).getName())) {
                return true;
            }
        }
        return false;
    }

    /** Stop factors of a request, normalized; problems are added to errors. */
    private record Definition(List<FactorDefinition> factors) {
    }

    /** A stop factor of a request, normalized; name is null when the request left it out, metric when it is unknown. */
    private record FactorDefinition(StopFactorMetric metric, String name, Map<String, Object> params, List<ConditionDto> conditions,
                                    boolean active, Set<Long> productIds, ProductSegment segment) {

        String identityKey() {
            return StopFactorConsoleServiceImpl.identityKey(metric.getMetricId(), params, segment, productIds);
        }
    }

    /**
     * Validates every stop factor against the current version of its own metric. A disabled metric cannot be given to
     * a new or changed stop factor; a stop factor kept as it is (currentFactors) stays even if its metric was disabled.
     */
    private Definition validateDefinition(StopFactorRequest request, Set<String> currentFactors, List<String> errors) {
        List<StopFactorDto> requestFactors = request.factors() != null ? request.factors() : List.of();
        if (requestFactors.isEmpty()) {
            errors.add("Добавьте хотя бы один стоп-фактор.");
        }
        Map<Long, StopFactorMetric> metrics = currentMetrics(requestFactors);
        List<FactorDefinition> factors = new ArrayList<>();
        Map<String, Integer> factorByIdentity = new HashMap<>();
        for (int i = 0; i < requestFactors.size(); i++) {
            StopFactorDto factor = requestFactors.get(i);
            List<String> factorErrors = new ArrayList<>();
            StopFactorMetric metric = factor.metricId() != null ? metrics.get(factor.metricId()) : null;
            if (factor.metricId() == null) {
                factorErrors.add("Укажите метрику.");
            } else if (metric == null) {
                factorErrors.add("Метрика с id " + factor.metricId() + " не найдена.");
            }
            if (metric == null) {
                errors.addAll(prefixed(i, factorErrors));
                continue;
            }

            Map<String, Object> params = validator.validateParams(metric, factor.params(), factorErrors);
            List<ConditionDto> conditions = validator.validateConditions(metric,
                    factor.conditions() != null ? factor.conditions() : List.of(), factorErrors);
            Set<Long> productIds = factor.productIds() != null ? factor.productIds() : Set.of();
            ProductSegment segment = validator.validateProducts(productIds, factor.segment(), factorErrors);
            FactorDefinition definition = new FactorDefinition(metric, blankToNull(factor.name()), params, conditions,
                    factor.active() == null || factor.active(), productIds, segment);
            if (!metric.isActive() && definition.active() && !currentFactors.contains(definition.identityKey())) {
                factorErrors.add("Метрика «" + metric.getTitle() + "» отключена. Включите её на странице метрик или выберите другую.");
            }
            errors.addAll(prefixed(i, factorErrors));
            if (factorErrors.isEmpty()) {
                // the same params of one metric may be checked with other limits, e.g. once for RETAIL and once for MSB
                Integer same = factorByIdentity.putIfAbsent(definition.identityKey(), i);
                if (same != null) {
                    errors.add("Стоп-фактор " + (i + 1) + " повторяет стоп-фактор " + (same + 1)
                            + ": у них одинаковые метрика, параметры, сегмент и продукты."
                            + " Измените параметры или ограничения одного из стоп-факторов или удалите лишний.");
                }
            }
            factors.add(definition);
        }
        return new Definition(factors);
    }

    /** Current versions of the metrics of the request, by metric id. */
    private Map<Long, StopFactorMetric> currentMetrics(List<StopFactorDto> factors) {
        Set<Long> metricIds = factors.stream().map(StopFactorDto::metricId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (metricIds.isEmpty()) {
            return Map.of();
        }
        return metricRepository.findAllCurrent(metricIds).stream()
                .collect(Collectors.toMap(StopFactorMetric::getMetricId, m -> m));
    }

    private static List<String> prefixed(int index, List<String> factorErrors) {
        return factorErrors.stream().map(error -> "Стоп-фактор " + (index + 1) + ": " + TextUtils.lowerFirst(error)).toList();
    }

    /**
     * A stop factor name left out is the name of the current stop factor with the same metric, params and limits, so a
     * change of one stop factor does not rename the others, or the metric's name template rendered with the params.
     */
    private static void addFactors(StopFactorGroup group, List<FactorDefinition> factors, Map<String, String> currentNames) {
        for (FactorDefinition definition : factors) {
            StopFactor factor = group.addFactor();
            factor.setMetric(definition.metric());
            factor.setParams(definition.params());
            factor.setActive(definition.active());
            factor.setSegment(definition.segment());
            factor.setProductIds(new HashSet<>(definition.productIds()));
            String name = definition.name() != null ? definition.name() : currentNames.get(definition.identityKey());
            factor.setName(name != null
                    ? name
                    : TextUtils.renderTemplate(definition.metric().getNameTemplate(), definition.params()));
            definition.conditions().forEach(c ->
                    factor.addCondition(c.result(), c.valueType(), c.value(), c.operator(), c.valueTo()));
        }
    }

    private StopFactorGroup findCurrent(long groupId) {
        return groupRepository.findCurrent(groupId).orElseThrow(() -> StopFactorGroupVersions.notFound(groupId));
    }

    private static NotFoundException notFound(long groupId) {
        return StopFactorGroupVersions.notFound(groupId);
    }

    private static String metricOff(StopFactorMetric metric) {
        return "Группу нельзя включить: метрика «" + metric.getTitle() + "» отключена. Сначала включите метрику.";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** What makes two stop factor lists the same: order, metric, params, limits and conditions (names follow from them). */
    private static List<String> factorKeys(List<FactorDefinition> factors) {
        return factors.stream()
                .map(factor -> factor.active() + "|" + factor.identityKey() + "|" + conditionKeys(factor.conditions().stream()
                        .map(c -> c.result() + "|" + c.valueType() + "|" + c.value() + "|" + c.operator() + "|" + c.valueTo())
                        .toList()))
                .toList();
    }

    private static List<String> factorKeysOf(List<StopFactor> factors) {
        return factors.stream()
                .map(factor -> factor.isActive() + "|" + identityKey(factor) + "|" + conditionKeys(factor.getConditions().stream()
                        .map(c -> c.getResult() + "|" + c.getValueType() + "|" + c.getValue() + "|" + c.getOperator() + "|" + c.getValueTo())
                        .toList()))
                .toList();
    }

    /** Metric, params and limits of a stop factor: two stop factors of a group may not share them. */
    private static String identityKey(Long metricId, Map<String, Object> params, ProductSegment segment, Set<Long> productIds) {
        return metricId + "|" + new TreeMap<>(params) + "|" + segment + "|" + new TreeSet<>(productIds);
    }

    private static String identityKey(StopFactor factor) {
        return identityKey(factor.getMetric().getMetricId(), factor.getParams(), factor.getSegment(), factor.getProductIds());
    }

    private static Set<String> conditionKeys(List<String> keys) {
        return new TreeSet<>(keys);
    }

    private List<StopFactorResponse> toResponses(List<StopFactorGroup> groups) {
        return groups.stream().map(StopFactorConsoleServiceImpl::toResponse).toList();
    }

    private static StopFactorResponse toResponse(StopFactorGroup g) {
        return new StopFactorResponse(
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
                g.getChangeReason(),
                g.getCreatedBy(),
                g.getCreatedDate()
        );
    }

    private static StopFactorDto toDto(StopFactor factor) {
        StopFactorMetric metric = factor.getMetric();
        return new StopFactorDto(
                factor.getId(),
                metric.getMetricId(),
                metric.getId(),
                metric.getTitle(),
                factor.getName(),
                new TreeMap<>(factor.getParams()),
                factor.getConditions().stream()
                        .sorted(Comparator.comparing(StopFactorCondition::getResult))
                        .map(c -> new ConditionDto(c.getId(), c.getResult(), c.getValueType(), c.getValue(),
                                c.getOperator(), c.getValueTo()))
                        .toList(),
                factor.isActive(),
                new TreeSet<>(factor.getProductIds()),
                factor.getSegment());
    }
}
