package kg.bakai.stopfactor.service.impl;

import kg.bakai.stopfactor.validator.StopFactorDefinitionValidator;
import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.util.NameTemplateRenderer;
import kg.bakai.stopfactor.util.Sentences;
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
    private final StopFactorDefinitionValidator validator;

    @Override
    @Transactional(readOnly = true)
    public List<StopFactorResponse> getAll(Boolean active, Long metricId, Long productId, ProductSegment segment) {
        // a product that does not exist gets no groups with a segment or a product limit
        ProductSegment productSegment = productId == null ? null : groupRepository.findProductsByIds(List.of(productId)).stream()
                .findFirst().map(ProductProjection::getSegment).orElse(null);
        List<StopFactorGroup> groups = groupRepository.findAllCurrentWithFactorsAndMetric().stream()
                .filter(g -> active == null || g.isActive() == active)
                .filter(g -> metricId == null || g.getFactors().stream().anyMatch(f -> f.getMetric().getId().equals(metricId)))
                .filter(g -> productId == null || g.appliesTo(productId, productSegment))
                .filter(g -> segment == null || g.getSegment() == null || g.getSegment() == segment)
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
    public StopFactorResponse create(StopFactorRequest request, String user) {
        StopFactorMetric metric = metricRepository.findById(request.metricId())
                .orElseThrow(() -> new BusinessValidationException("Метрика с id " + request.metricId() + " не найдена."));

        List<String> errors = new ArrayList<>();
        if (!metric.isActive()) {
            errors.add("Метрика «" + metric.getTitle() + "» отключена. Включите её на странице метрик или выберите другую.");
        }
        Definition definition = validateDefinition(metric, request, errors);
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }

        StopFactorGroup group = new StopFactorGroup();
        addFactors(group, metric, definition.factors(), Map.of());
        group.setDescription(blankToNull(request.description()));
        group.setActive(request.active() == null || request.active());
        group.setOrderNumber(request.orderNumber() != null
                ? request.orderNumber()
                : groupRepository.findMaxCurrentOrderNumber() + 1);
        group.setProductIds(new HashSet<>(definition.productIds()));
        group.setSegment(request.segment());
        group.setValidFrom(LocalDateTime.now());
        group.setChangeReason(request.changeReason() != null && !request.changeReason().isBlank()
                ? request.changeReason().trim()
                : CREATED_REASON);
        group.setCreatedBy(user);

        StopFactorGroup saved = groupRepository.save(group);
        log.info("Группа стоп-факторов {} '{}' ({} стоп-факторов) создана пользователем {}", saved.getGroupId(), saved.getName(),
                saved.getFactors().size(), user);
        return get(saved.getGroupId());
    }

    @Override
    @Transactional
    public StopFactorResponse update(long groupId, StopFactorRequest request, String user) {
        StopFactorGroup current = lockAndFindCurrent(groupId);
        StopFactorMetric metric = current.getFactors().getFirst().getMetric();
        int orderNumber = request.orderNumber() != null ? request.orderNumber() : current.getOrderNumber();
        boolean active = request.active() != null ? request.active() : current.isActive();

        List<String> errors = new ArrayList<>();
        if (!metric.getId().equals(request.metricId())) {
            errors.add("Метрику группы изменить нельзя. Чтобы проверять другую метрику, создайте новую группу.");
        }
        if (request.changeReason() == null || request.changeReason().isBlank()) {
            errors.add(REASON_REQUIRED);
        }
        if (active && !current.isActive() && !metric.isActive()) {
            errors.add(metricOff(metric));
        }
        Definition definition = validateDefinition(metric, request, errors);
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
        List<FactorDefinition> factors = definition.factors();
        Set<Long> productIds = definition.productIds();
        boolean factorsChanged = !factorKeys(factors).equals(factorKeysOf(current.getFactors()))
                || renamesFactors(factors, current.getFactors());
        String description = blankToNull(request.description());

        boolean unchanged = !factorsChanged
                && Objects.equals(description, current.getDescription())
                && orderNumber == current.getOrderNumber()
                && active == current.isActive()
                && productIds.equals(current.getProductIds())
                && request.segment() == current.getSegment();
        if (unchanged) {
            log.info("Группа {}: изменений от {} нет, новая версия не создана", groupId, user);
            return get(groupId);
        }

        StopFactorGroup next = closeAndCreateNextVersion(current, request.changeReason(), user);
        if (factorsChanged) {
            Map<String, String> currentNames = current.getFactors().stream()
                    .collect(Collectors.toMap(factor -> paramsKey(factor.getParams()), StopFactor::getName, (a, b) -> a));
            next.removeFactors();
            addFactors(next, metric, factors, currentNames);
        }
        next.setDescription(description);
        next.setOrderNumber(orderNumber);
        next.setActive(active);
        next.setProductIds(new HashSet<>(productIds));
        next.setSegment(request.segment());
        StopFactorGroup saved = groupRepository.save(next);
        log.info("Группа {} обновлена до версии {} пользователем {}: {}", groupId, saved.getVersionNumber(), user, request.changeReason());
        return get(groupId);
    }

    @Override
    @Transactional
    public StopFactorResponse changeStatus(long groupId, boolean active, String changeReason, String user) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        StopFactorGroup current = lockAndFindCurrent(groupId);
        if (current.isActive() == active) {
            return get(groupId);
        }
        if (active && !current.getFactors().getFirst().getMetric().isActive()) {
            throw new BusinessValidationException(metricOff(current.getFactors().getFirst().getMetric()));
        }

        StopFactorGroup next = closeAndCreateNextVersion(current, changeReason, user);
        next.setActive(active);
        StopFactorGroup saved = groupRepository.save(next);
        log.info("Группа {} {} пользователем {} (версия {}): {}", groupId, active ? "активирована" : "деактивирована",
                user, saved.getVersionNumber(), changeReason);
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
    public void delete(long groupId, String changeReason, String user) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new BusinessValidationException("Укажите причину удаления: она сохраняется в истории версий.");
        }
        StopFactorGroup current = lockAndFindCurrent(groupId);
        StopFactorGroup last = closeAndCreateNextVersion(current, changeReason, user);
        last.setActive(false);
        last.setValidTo(last.getValidFrom());
        StopFactorGroup saved = groupRepository.save(last);
        log.info("Группа {} удалена пользователем {} (версия {}): {}", groupId, user, saved.getVersionNumber(), changeReason);
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
    public StopFactorResponse addFactor(long groupId, StopFactorDto factor, String changeReason, String user) {
        return changeFactors(groupId, factors -> {
            factors.add(factor);
            return factors;
        }, changeReason, user);
    }

    @Override
    @Transactional
    public StopFactorResponse updateFactor(long groupId, long factorId, StopFactorDto factor, String changeReason, String user) {
        return changeFactors(groupId, factors -> {
            factors.set(indexOfFactor(groupId, factors, factorId), factor);
            return factors;
        }, changeReason, user);
    }

    @Override
    @Transactional
    public StopFactorResponse deleteFactor(long groupId, long factorId, String changeReason, String user) {
        return changeFactors(groupId, factors -> {
            int index = indexOfFactor(groupId, factors, factorId);
            if (factors.size() == 1) {
                throw new BusinessValidationException("Нельзя удалить единственный стоп-фактор группы №" + groupId
                        + ". Чтобы прекратить проверку, отключите или удалите группу.");
            }
            factors.remove(index);
            return factors;
        }, changeReason, user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConditionDto> getConditions(long groupId, long factorId) {
        return getFactor(groupId, factorId).conditions();
    }

    @Override
    @Transactional
    public StopFactorResponse saveCondition(long groupId, long factorId, ConditionDto condition, String changeReason, String user) {
        return changeFactor(groupId, factorId, conditions -> {
            conditions.removeIf(c -> c.result() == condition.result());
            conditions.add(condition);
            return conditions;
        }, changeReason, user);
    }

    @Override
    @Transactional
    public StopFactorResponse deleteCondition(long groupId, long factorId, StopFactorStatus result, String changeReason, String user) {
        return changeFactor(groupId, factorId, conditions -> {
            if (!conditions.removeIf(c -> c.result() == result)) {
                throw new NotFoundException("У стоп-фактора нет условия «" + result.getLabel() + "».");
            }
            return conditions;
        }, changeReason, user);
    }

    /** Changes the conditions of one stop factor; it keeps its name, params and status. */
    private StopFactorResponse changeFactor(long groupId, long factorId, UnaryOperator<List<ConditionDto>> change,
                                            String changeReason, String user) {
        return changeFactors(groupId, factors -> {
            int index = indexOfFactor(groupId, factors, factorId);
            StopFactorDto factor = factors.get(index);
            factors.set(index, new StopFactorDto(factor.id(), factor.name(), factor.params(),
                    change.apply(new ArrayList<>(factor.conditions())), factor.active()));
            return factors;
        }, changeReason, user);
    }

    /**
     * Changes the stop factors of the current version through {@link #update}, so they are validated and versioned
     * the same way. The group and the other stop factors keep their names.
     */
    private StopFactorResponse changeFactors(long groupId, UnaryOperator<List<StopFactorDto>> change, String changeReason, String user) {
        lockAndFindCurrent(groupId);
        StopFactorResponse current = get(groupId);
        List<StopFactorDto> factors = change.apply(new ArrayList<>(current.factors()));
        return update(groupId, new StopFactorRequest(current.metricId(), current.description(),
                current.orderNumber(), current.active(), current.productIds(), current.segment(), factors, changeReason), user);
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

    /** Stop factors and products of a request, normalized; problems are added to errors. */
    private record Definition(List<FactorDefinition> factors, Set<Long> productIds) {
    }

    /** A stop factor of a request, normalized; name is null when the request left it out. */
    private record FactorDefinition(String name, Map<String, Object> params, List<ConditionDto> conditions, boolean active) {
    }

    private Definition validateDefinition(StopFactorMetric metric, StopFactorRequest request, List<String> errors) {
        List<StopFactorDto> requestFactors = request.factors() != null ? request.factors() : List.of();
        if (requestFactors.isEmpty()) {
            errors.add("Добавьте хотя бы один стоп-фактор.");
        }
        List<FactorDefinition> factors = new ArrayList<>();
        Map<Map<String, Object>, Integer> factorByParams = new HashMap<>();
        for (int i = 0; i < requestFactors.size(); i++) {
            StopFactorDto factor = requestFactors.get(i);
            String prefix = "Стоп-фактор " + (i + 1) + ": ";
            List<String> factorErrors = new ArrayList<>();
            Map<String, Object> params = validator.validateParams(metric, factor.params(), factorErrors);
            List<ConditionDto> conditions = validator.validateConditions(metric,
                    factor.conditions() != null ? factor.conditions() : List.of(), factorErrors);
            factorErrors.forEach(error -> errors.add(prefix + Sentences.lowerFirst(error)));
            if (factorErrors.isEmpty()) {
                Integer same = factorByParams.putIfAbsent(params, i);
                if (same != null) {
                    errors.add("Стоп-фактор " + (i + 1) + " повторяет стоп-фактор " + (same + 1) + ": у них одинаковые параметры."
                            + " Измените параметры одного из стоп-факторов или удалите лишний.");
                }
            }
            factors.add(new FactorDefinition(blankToNull(factor.name()), params, conditions, factor.active() == null || factor.active()));
        }
        Set<Long> productIds = request.productIds() != null ? request.productIds() : Set.of();
        validator.validateProducts(productIds, errors);
        return new Definition(factors, productIds);
    }

    /**
     * A stop factor name left out is the name of the current stop factor with the same params, so a change of one
     * stop factor does not rename the others, or the metric's name template rendered with the params.
     */
    private static void addFactors(StopFactorGroup group, StopFactorMetric metric, List<FactorDefinition> factors,
                                   Map<String, String> currentNames) {
        for (FactorDefinition definition : factors) {
            StopFactor factor = group.addFactor();
            factor.setMetric(metric);
            factor.setParams(definition.params());
            factor.setActive(definition.active());
            String name = definition.name() != null ? definition.name() : currentNames.get(paramsKey(definition.params()));
            factor.setName(name != null
                    ? name
                    : NameTemplateRenderer.render(metric.getNameTemplate(), definition.params()));
            definition.conditions().forEach(c ->
                    factor.addCondition(c.result(), c.valueType(), c.value(), c.operator(), c.valueTo()));
        }
    }

    // The current version is closed and flushed before the next one is inserted: a group has only one
    // current version (ux_stop_factor_group_current_version), and Hibernate would run the INSERT before the UPDATE
    private StopFactorGroup closeAndCreateNextVersion(StopFactorGroup current, String changeReason, String user) {
        LocalDateTime now = LocalDateTime.now();
        current.setValidTo(now);
        groupRepository.saveAndFlush(current);

        StopFactorGroup next = current.newVersion(now, changeReason.trim());
        next.setCreatedBy(user);
        return next;
    }

    private StopFactorGroup lockAndFindCurrent(long groupId) {
        groupRepository.lockGroup(groupId)
                .orElseThrow(() -> notFound(groupId));
        return findCurrent(groupId);
    }

    private StopFactorGroup findCurrent(long groupId) {
        return groupRepository.findCurrent(groupId)
                .orElseThrow(() -> notFound(groupId));
    }

    private static NotFoundException notFound(long groupId) {
        return new NotFoundException("Группа стоп-факторов №" + groupId + " не найдена.");
    }

    private static String metricOff(StopFactorMetric metric) {
        return "Группу нельзя включить: её метрика «" + metric.getTitle() + "» отключена. Сначала включите метрику.";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** What makes two stop factor lists the same: order, params and conditions (names follow from the params). */
    private static List<String> factorKeys(List<FactorDefinition> factors) {
        return factors.stream()
                .map(factor -> factor.active() + "|" + new TreeMap<>(factor.params()) + "|" + conditionKeys(factor.conditions().stream()
                        .map(c -> c.result() + "|" + c.valueType() + "|" + c.value() + "|" + c.operator() + "|" + c.valueTo())
                        .toList()))
                .toList();
    }

    private static String paramsKey(Map<String, Object> params) {
        return new TreeMap<>(params).toString();
    }

    private static List<String> factorKeysOf(List<StopFactor> factors) {
        return factors.stream()
                .map(factor -> factor.isActive() + "|" + new TreeMap<>(factor.getParams()) + "|" + conditionKeys(factor.getConditions().stream()
                        .map(c -> c.getResult() + "|" + c.getValueType() + "|" + c.getValue() + "|" + c.getOperator() + "|" + c.getValueTo())
                        .toList()))
                .toList();
    }

    private static Set<String> conditionKeys(List<String> keys) {
        return new TreeSet<>(keys);
    }

    private List<StopFactorResponse> toResponses(List<StopFactorGroup> groups) {
        return groups.stream().map(StopFactorConsoleServiceImpl::toResponse).toList();
    }

    private static StopFactorResponse toResponse(StopFactorGroup g) {
        StopFactorMetric metric = g.getFactors().getFirst().getMetric();
        return new StopFactorResponse(
                g.getGroupId(),
                g.getId(),
                g.getVersionNumber(),
                g.isCurrent(),
                g.getName(),
                g.getDescription(),
                g.isActive(),
                g.getOrderNumber(),
                metric.getId(),
                metric.getTitle(),
                metric.getCalculator(),
                new TreeSet<>(g.getProductIds()),
                g.getSegment(),
                g.getFactors().stream().map(StopFactorConsoleServiceImpl::toDto).toList(),
                g.getValidFrom(),
                g.getValidTo(),
                g.getChangeReason(),
                g.getCreatedBy(),
                g.getCreatedDate()
        );
    }

    private static StopFactorDto toDto(StopFactor factor) {
        return new StopFactorDto(factor.getId(), factor.getName(), new TreeMap<>(factor.getParams()),
                factor.getConditions().stream()
                        .sorted(Comparator.comparing(StopFactorCondition::getResult))
                        .map(c -> new ConditionDto(c.getId(), c.getResult(), c.getValueType(), c.getValue(),
                                c.getOperator(), c.getValueTo()))
                        .toList(),
                factor.isActive());
    }
}
