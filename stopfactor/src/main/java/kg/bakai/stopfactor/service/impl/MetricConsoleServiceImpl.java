package kg.bakai.stopfactor.service.impl;

import kg.bakai.stopfactor.validator.StopFactorDefinitionValidator;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.ConflictException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.ParamSpec;
import kg.bakai.stopfactor.calculator.StopFactorCalculator;
import kg.bakai.stopfactor.service.MetricConsoleService;
import kg.bakai.stopfactor.util.NameTemplateRenderer;
import kg.bakai.stopfactor.util.Sentences;
import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.entity.MetricParamSchema;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorGroup;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.repository.StopFactorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricConsoleServiceImpl implements MetricConsoleService {

    private final StopFactorMetricRepository metricRepository;
    private final StopFactorGroupRepository groupRepository;
    private final StopFactorRepository stopFactorRepository;
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
    public List<MetricDto> getAll() {
        return metricRepository.findAll(Sort.by("id")).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MetricDto get(long id) {
        return toDto(find(id));
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
        StopFactorMetric saved = metricRepository.save(metric);
        log.info("Метрика {} '{}' ({}) создана", saved.getId(), saved.getTitle(), saved.getCalculator());
        return toDto(saved);
    }

    @Override
    @Transactional
    public MetricDto update(long id, MetricDto request) {
        StopFactorMetric metric = find(id);
        if (request.calculator() != metric.getCalculator()) {
            throw new BusinessValidationException("Калькулятор метрики изменить нельзя. Для другого калькулятора создайте новую метрику.");
        }
        String title = request.title().trim();
        requireUniqueTitle(title, id);
        StopFactorCalculator<?> calculator = calculatorRegistry.get(metric.getCalculator());
        List<MetricParamSchema> params = request.params() != null ? normalizeSchema(request.params()) : metric.getParams();
        validateDefinition(calculator, request.nameTemplate(), params);

        List<String> errors = new ArrayList<>();
        for (StopFactorGroup group : groupRepository.findCurrentByMetricId(id)) {
            for (StopFactor factor : group.getFactors()) {
                List<String> factorErrors = new ArrayList<>();
                validator.validateParams(metric.getCalculator(), title, params, factor.getParams(), factorErrors);
                factorErrors.forEach(e -> errors.add("Группа №" + group.getGroupId() + ", стоп-фактор " + factor.getOrderNumber()
                        + ": " + Sentences.lowerFirst(e)));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }

        metric.setTitle(title);
        metric.setNameTemplate(request.nameTemplate().trim());
        requireRemovedParamsUnused(metric, params);
        metric.setParams(new ArrayList<>(params));
        if (request.active() != null) {
            metric.setActive(request.active());
        }
        log.info("Метрика {} обновлена (active={})", id, metric.isActive());
        return toDto(metricRepository.saveAndFlush(metric));
    }

    @Override
    @Transactional
    public void delete(long id) {
        StopFactorMetric metric = find(id);
        if (groupRepository.existsByMetricId(id)) {
            throw new ConflictException("Метрику «" + metric.getTitle() + "» нельзя удалить: на неё ссылаются стоп-факторы, в том числе"
                    + " прежние версии. Отключите метрику вместо удаления.");
        }
        metricRepository.delete(metric);
        log.info("Метрика {} '{}' удалена", id, metric.getTitle());
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

        NameTemplateRenderer.placeholders(nameTemplate).stream()
                .filter(key -> !keys.contains(key))
                .forEach(key -> errors.add("Шаблон названия содержит {" + key + "}, но у метрики нет такого параметра."));

        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
    }

    /** A param a stop factor of any version of any group of this metric has a value of cannot be removed from the metric. */
    private void requireRemovedParamsUnused(StopFactorMetric metric, List<MetricParamSchema> params) {
        Set<String> keys = params.stream().map(MetricParamSchema::key).collect(Collectors.toSet());
        List<String> used = metric.getParams().stream()
                .map(MetricParamSchema::key)
                .filter(key -> !keys.contains(key))
                .filter(key -> stopFactorRepository.isParamUsed(metric.getId(), key))
                .toList();
        if (!used.isEmpty()) {
            throw new BusinessValidationException("Параметры " + String.join(", ", used) + " нельзя убрать: они заданы в стоп-факторах"
                    + " этой метрики, в том числе в прежних версиях.");
        }
    }

    private List<MetricParamSchema> normalizeSchema(List<MetricParamSchema> params) {
        return params.stream()
                .map(p -> new MetricParamSchema(p.key().trim(), p.label().trim(), p.required()))
                .toList();
    }

    // Compared in Java: with the C locale of the database, PostgreSQL lower() does not change Cyrillic letters
    private void requireUniqueTitle(String title, Long exceptId) {
        boolean taken = metricRepository.findAll().stream()
                .anyMatch(m -> !m.getId().equals(exceptId) && m.getTitle().equalsIgnoreCase(title));
        if (taken) {
            throw new ConflictException("Метрика с названием «" + title + "» уже существует.");
        }
    }

    private StopFactorMetric find(long id) {
        return metricRepository.findById(id).orElseThrow(() -> new NotFoundException("Метрика с id " + id + " не найдена."));
    }

    private MetricDto toDto(StopFactorMetric metric) {
        return new MetricDto(metric.getId(), metric.getTitle(), metric.getCalculator(),
                metric.getCalculator().getName(), metric.getNameTemplate(), List.copyOf(metric.getParams()),
                metric.isActive(), metric.getCreatedDate(), metric.getUpdatedDate());
    }
}
