package kg.bakai.stopfactor.service.impl;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.calculator.ParamSpec;
import kg.bakai.stopfactor.dto.CalculatorDto;
import kg.bakai.stopfactor.dto.MetricDto;
import kg.bakai.stopfactor.dto.MetricParam;
import kg.bakai.stopfactor.entity.StopFactor;
import kg.bakai.stopfactor.entity.StopFactorMetric;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.repository.StopFactorMetricRepository;
import kg.bakai.stopfactor.service.MetricConsoleService;
import kg.bakai.stopfactor.validation.StopFactorValidation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricConsoleServiceImpl implements MetricConsoleService {

    private final StopFactorMetricRepository metricRepository;
    private final CalculatorRegistry calculatorRegistry;
    private final StopFactorValidation validator;

    @Override
    @Transactional(readOnly = true)
    public List<MetricDto> getAll() {
        return metricRepository.findAllOrdered().stream().map(MetricConsoleServiceImpl::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MetricDto get(long metricId) {
        return toDto(find(metricId));
    }

    @Override
    @Transactional
    public MetricDto create(MetricDto request) {
        StopFactorMetric metric = new StopFactorMetric();
        metric.setCalculator(request.calculator());
        apply(metric, request, null);
        StopFactorMetric saved = metricRepository.saveAndFlush(metric);
        log.info("Метрика {} '{}' создана", saved.getId(), saved.getTitle());
        return toDto(saved);
    }

    @Override
    @Transactional
    public MetricDto update(long metricId, MetricDto request) {
        StopFactorMetric metric = find(metricId);
        if (request.calculator() != null && request.calculator() != metric.getCalculator()) {
            throw new BusinessValidationException("Калькулятор метрики нельзя изменить. Создайте новую метрику.");
        }
        apply(metric, request, metricId);
        StopFactorMetric saved = metricRepository.saveAndFlush(metric);
        log.info("Метрика {} '{}' изменена", saved.getId(), saved.getTitle());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(long metricId) {
        StopFactorMetric metric = find(metricId);
        if (metricRepository.isUsed(metricId)) {
            throw new BusinessValidationException("Метрику «" + metric.getTitle() + "» используют стоп-факторы."
                    + " Удалить её нельзя, отключите её.");
        }
        metricRepository.delete(metric);
        log.info("Метрика {} '{}' удалена", metricId, metric.getTitle());
    }

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

    /** Sets title, params and active of a valid request; metricId of an existing metric, null for a new one. */
    private void apply(StopFactorMetric metric, MetricDto request, Long metricId) {
        List<String> errors = new ArrayList<>();
        String title = request.title() != null ? request.title().trim() : "";
        if (title.isEmpty()) {
            errors.add("Укажите название метрики.");
        } else {
            metricRepository.findAllOrdered().stream()
                    .filter(m -> m.getTitle().equals(title) && !m.getId().equals(metricId))
                    .findFirst()
                    .ifPresent(m -> errors.add("Метрика «" + title + "» уже есть (№" + m.getId() + ")."));
        }
        if (metric.getCalculator() == null) {
            errors.add("Укажите калькулятор.");
            throw new BusinessValidationException(errors);
        }
        // left out: every calculator param for a new metric, the current params for a changed one
        List<MetricParam> params;
        if (request.params() == null || request.params().isEmpty() && metricId == null) {
            params = metricId == null ? calculatorParams(metric.getCalculator()) : metric.getParams();
        } else {
            params = validateParams(metric.getCalculator(), request.params(), errors);
        }
        if (metricId != null && errors.isEmpty()) {
            errors.addAll(brokenStopFactors(metric, params));
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException(errors);
        }
        metric.setTitle(title);
        metric.setParams(new ArrayList<>(params));
        metric.setActive(request.active() == null || request.active());
    }

    /** Every param of the calculator, as a metric created without params gets them. */
    private List<MetricParam> calculatorParams(CalculatorType calculator) {
        return calculatorRegistry.get(calculator).paramSpecs().stream()
                .map(spec -> new MetricParam(spec.key(), spec.label(), spec.required()))
                .toList();
    }

    /** Params of the calculator, each once; a param the calculator requires must be there and required. */
    private List<MetricParam> validateParams(CalculatorType calculator, List<MetricParam> params, List<String> errors) {
        Map<String, ParamSpec> specs = calculatorRegistry.get(calculator).paramSpecs().stream()
                .collect(Collectors.toMap(ParamSpec::key, s -> s, (a, b) -> a, LinkedHashMap::new));
        Set<String> seen = new HashSet<>();
        for (MetricParam param : params) {
            if (!specs.containsKey(param.key())) {
                errors.add("Параметр «" + param.key() + "» не используется калькулятором. Допустимые: "
                        + String.join(", ", specs.keySet()) + ".");
            } else if (!seen.add(param.key())) {
                errors.add("Параметр «" + param.key() + "» указан несколько раз.");
            }
        }
        specs.values().stream()
                .filter(ParamSpec::required)
                .filter(spec -> params.stream().noneMatch(p -> p.key().equals(spec.key()) && p.required()))
                .forEach(spec -> errors.add("Параметр «" + spec.key() + "» обязателен для калькулятора: добавьте его как обязательный."));
        return params.stream().map(p -> new MetricParam(p.key().trim(), p.label().trim(), p.required())).toList();
    }

    /** Stop factors of current group versions whose param values would not fit the new params. */
    private List<String> brokenStopFactors(StopFactorMetric metric, List<MetricParam> params) {
        StopFactorMetric changed = new StopFactorMetric();
        changed.setId(metric.getId());
        changed.setTitle(metric.getTitle());
        changed.setCalculator(metric.getCalculator());
        changed.setParams(params);
        List<String> broken = new ArrayList<>();
        for (StopFactor factor : metricRepository.findCurrentStopFactors(metric.getId())) {
            List<String> errors = new ArrayList<>();
            validator.validateParams(changed, factor.getParams(), errors);
            errors.forEach(error -> broken.add("Стоп-фактор «" + factor.getName() + "» группы «" + factor.getGroup().getName()
                    + "» не подходит под новые параметры: " + error));
        }
        return broken;
    }

    private StopFactorMetric find(long metricId) {
        return metricRepository.findById(metricId)
                .orElseThrow(() -> new NotFoundException("Метрика №" + metricId + " не найдена."));
    }

    private static MetricDto toDto(StopFactorMetric m) {
        return new MetricDto(m.getId(), m.getTitle(), m.getCalculator(), m.getCalculator().getName(),
                List.copyOf(m.getParams()), m.isActive(), m.getCreatedDate(), m.getUpdatedDate());
    }
}
