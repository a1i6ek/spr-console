package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.ConflictException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.ParamSpec;
import kg.bakai.stopfactor.calculator.StopFactorCalculator;
import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.entity.MetricParamSchema;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.service.MetricConsoleService;
import kg.bakai.stopfactor.util.TextUtils;
import kg.bakai.stopfactor.validator.StopFactorDefinitionValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricConsoleServiceImpl implements MetricConsoleService {

    private static final String CREATED_REASON = "Создание метрики";
    private static final String REASON_REQUIRED = "Укажите причину изменения метрики: она сохраняется в истории версий.";

    private final StopFactorMetricRepository metricRepository;
    private final StopFactorGroupRepository groupRepository;
    private final StopFactorGroupVersions groupVersions;
    private final CalculatorRegistry calculatorRegistry;
    private final StopFactorDefinitionValidator validator;

    @Override
    public List<CalculatorDto> getCalculators() {
        return Arrays.stream(CalculatorType.values())
                .map(calculatorRegistry::get)
                .map(calculator -> new CalculatorDto(
                        calculator.type(),
                        calculator.type().getName(),
                        calculator.valueKind(),
                        new TreeSet<>(calculator.valueKind().conditionTypes()),
                        calculator.valueKind().operators().stream().map(CalculatorDto.OperatorOption::of).toList(),
                        calculator.paramSpecs().stream()
                                .map(spec -> new CalculatorDto.CalculatorParam(
                                        spec.key(),
                                        spec.label(),
                                        spec.required(),
                                        spec.isInteger() ? "INTEGER" : "ENUM",
                                        spec.isInteger() ? spec.min() : null,
                                        spec.isInteger() ? null : spec.allowedValues()))
                                .toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetricDto> getAll(LocalDateTime at) {
        List<StopFactorMetric> metrics = at == null ? metricRepository.findAllCurrent() : metricRepository.findAllAt(at);
        return metrics.stream().map(MetricConsoleServiceImpl::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MetricDto get(long metricId) {
        return toDto(findCurrent(metricId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetricDto> getVersions(long metricId) {
        List<StopFactorMetric> versions = metricRepository.findVersions(metricId);
        if (versions.isEmpty()) {
            throw notFound(metricId);
        }
        return versions.stream().map(MetricConsoleServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public MetricDto create(MetricDto request) {
        String title = request.title().trim();
        requireUniqueTitle(title, null);
        StopFactorCalculator<?> calculator = calculatorRegistry.get(request.calculator());
        List<MetricParamSchema> params = request.params() != null
                ? normalizeSchema(request.params())
                : calculator.paramSpecs().stream().map(ParamSpec::toSchema).toList();
        validateDefinition(calculator, request.nameTemplate(), params);

        StopFactorMetric metric = new StopFactorMetric();
        metric.setTitle(title);
        metric.setCalculator(request.calculator());
        metric.setNameTemplate(request.nameTemplate().trim());
        metric.setParams(new ArrayList<>(params));
        metric.setActive(request.active() == null || request.active());
        metric.setValidFrom(LocalDateTime.now());
        metric.setChangeReason(isBlank(request.changeReason()) ? CREATED_REASON : request.changeReason().trim());
        metric.setCreatedBy("");
        StopFactorMetric saved = metricRepository.save(metric);
        log.info("Метрика {} '{}' ({}) создана", saved.getMetricId(), saved.getTitle(), saved.getCalculator());
        return toDto(saved);
    }

    @Override
    @Transactional
    public MetricDto update(long metricId, MetricDto request) {
        metricRepository.lockMetric(metricId).orElseThrow(() -> notFound(metricId));
        StopFactorMetric current = findCurrent(metricId);
        if (request.calculator() != current.getCalculator()) {
            throw new BusinessValidationException("Калькулятор метрики изменить нельзя. Для другого калькулятора создайте новую метрику.");
        }
        String title = request.title().trim();
        String nameTemplate = request.nameTemplate().trim();
        List<MetricParamSchema> params = request.params() != null ? normalizeSchema(request.params()) : current.getParams();
        boolean active = request.active() != null ? request.active() : current.isActive();
        requireUniqueTitle(title, metricId);
        validateDefinition(calculatorRegistry.get(current.getCalculator()), nameTemplate, params);

        boolean definitionChanged = !title.equals(current.getTitle())
                || !nameTemplate.equals(current.getNameTemplate())
                || !params.equals(current.getParams());
        if (!definitionChanged) {
            // the active flag decides only which metrics new stop factors may use, it is not part of the history
            current.setActive(active);
            log.info("Метрика {}: active={}", metricId, active);
            return toDto(metricRepository.saveAndFlush(current));
        }
        if (isBlank(request.changeReason())) {
            throw new BusinessValidationException(REASON_REQUIRED);
        }
        List<StopFactorGroup> groups = groupRepository.findCurrentByMetricId(metricId);
        requireValidStopFactors(groups, current, title, params);

        String reason = request.changeReason().trim();
        LocalDateTime now = LocalDateTime.now();
        current.setValidTo(now);
        metricRepository.saveAndFlush(current);
        StopFactorMetric next = current.newVersion(now, reason);
        next.setTitle(title);
        next.setNameTemplate(nameTemplate);
        next.setParams(new ArrayList<>(params));
        next.setActive(active);
        next.setCreatedBy("");
        StopFactorMetric saved = metricRepository.saveAndFlush(next);

        for (StopFactorGroup group : groups) {
            StopFactorGroup nextGroup = groupVersions.next(groupVersions.lockCurrent(group.getGroupId()),
                    "Метрика «" + title + "» изменена: " + reason);
            nextGroup.useMetric(current, saved);
            groupRepository.save(nextGroup);
        }
        log.info("Метрика {} обновлена до версии {}, новые версии групп: {}: {}", metricId, saved.getVersionNumber(),
                groups.stream().map(StopFactorGroup::getGroupId).toList(), reason);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(long metricId) {
        StopFactorMetric metric = findCurrent(metricId);
        if (groupRepository.existsByMetricId(metricId)) {
            throw new ConflictException("Метрику «" + metric.getTitle() + "» нельзя удалить: на неё ссылаются стоп-факторы, в том числе"
                    + " прежние версии. Отключите метрику вместо удаления.");
        }
        // the later versions refer to the first one
        List<StopFactorMetric> versions = metricRepository.findVersions(metricId);
        metricRepository.deleteAll(versions.stream().filter(v -> v.getRootId() != null).toList());
        metricRepository.flush();
        metricRepository.deleteAll(versions.stream().filter(v -> v.getRootId() == null).toList());
        log.info("Метрика {} '{}' удалена", metricId, metric.getTitle());
    }

    /** The current stop factors of the metric must stay valid with the new params, or the change is refused. */
    private void requireValidStopFactors(List<StopFactorGroup> groups, StopFactorMetric metric, String title,
                                         List<MetricParamSchema> params) {
        List<String> errors = new ArrayList<>();
        for (StopFactorGroup group : groups) {
            for (StopFactor factor : group.getFactors()) {
                if (!factor.getMetric().getId().equals(metric.getId())) {
                    continue;
                }
                List<String> factorErrors = new ArrayList<>();
                validator.validateParams(metric.getCalculator(), title, params, factor.getParams(), factorErrors);
                factorErrors.forEach(e -> errors.add("Группа №" + group.getGroupId() + ", стоп-фактор " + factor.getOrderNumber()
                        + ": " + TextUtils.lowerFirst(e)));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
    }

    private void validateDefinition(StopFactorCalculator<?> calculator, String nameTemplate, List<MetricParamSchema> params) {
        List<String> errors = new ArrayList<>();
        Map<String, ParamSpec> specs = calculator.paramSpecs().stream()
                .collect(Collectors.toMap(ParamSpec::key, s -> s));

        Set<String> keys = new HashSet<>();
        for (MetricParamSchema param : params) {
            if (!keys.add(param.key())) {
                errors.add("Параметр «" + param.key() + "» указан несколько раз.");
            } else if (!specs.containsKey(param.key())) {
                errors.add("Калькулятор «" + calculator.type().getName() + "» не поддерживает параметр «" + param.key()
                        + "». Доступные параметры: " + String.join(", ", new TreeSet<>(specs.keySet())) + ".");
            }
        }
        specs.values().stream()
                .filter(ParamSpec::required)
                .forEach(spec -> params.stream()
                        .filter(p -> p.key().equals(spec.key()))
                        .findFirst()
                        .ifPresentOrElse(
                                p -> {
                                    if (!p.required()) {
                                        errors.add("Параметр «" + spec.label() + "» обязателен для калькулятора «"
                                                + calculator.type().getName() + "» и не может быть необязательным.");
                                    }
                                },
                                () -> errors.add("Калькулятор «" + calculator.type().getName() + "» требует параметр «"
                                        + spec.label() + "»: верните его в список параметров.")));

        TextUtils.placeholders(nameTemplate).stream()
                .filter(key -> !keys.contains(key))
                .forEach(key -> errors.add("Шаблон названия содержит {" + key + "}, но у метрики нет такого параметра."));

        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
    }

    private List<MetricParamSchema> normalizeSchema(List<MetricParamSchema> params) {
        return params.stream()
                .map(p -> new MetricParamSchema(p.key().trim(), p.label().trim(), p.required()))
                .toList();
    }

    // Compared in Java: with the C locale of the database, PostgreSQL lower() does not change Cyrillic letters
    private void requireUniqueTitle(String title, Long exceptMetricId) {
        boolean taken = metricRepository.findAllCurrent().stream()
                .anyMatch(m -> !m.getMetricId().equals(exceptMetricId) && m.getTitle().equalsIgnoreCase(title));
        if (taken) {
            throw new ConflictException("Метрика с названием «" + title + "» уже существует.");
        }
    }

    private StopFactorMetric findCurrent(long metricId) {
        return metricRepository.findCurrent(metricId).orElseThrow(() -> notFound(metricId));
    }

    private static NotFoundException notFound(long metricId) {
        return new NotFoundException("Метрика с id " + metricId + " не найдена.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static MetricDto toDto(StopFactorMetric metric) {
        return new MetricDto(metric.getMetricId(), metric.getId(), metric.getVersionNumber(), metric.getTitle(),
                metric.getCalculator(), metric.getCalculator().getName(), metric.getNameTemplate(), List.copyOf(metric.getParams()),
                metric.isActive(), metric.getChangeReason(), metric.getValidFrom(), metric.getValidTo(),
                metric.getCreatedDate(), metric.getUpdatedDate());
    }
}
