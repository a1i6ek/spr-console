/**
 * What changed from one version of a stop factor to the next, in words an admin reads in the history:
 * "Порог отказа: > 5 → > 3".
 */
import type { Condition, ConditionResult, Metric, Product, StopFactor } from './api/index.js';
import { segments, threshold } from './labels.js';

export type Change = { label: string; from: string; to: string };

export function changes(
	before: StopFactor,
	after: StopFactor,
	metrics: Metric[],
	products: Product[]
): Change[] {
	const found: Change[] = [];
	const add = (label: string, from: string, to: string) => {
		if (from !== to) found.push({ label, from, to });
	};

	add('Статус', before.active ? 'активен' : 'отключён', after.active ? 'активен' : 'отключён');
	add('Название', before.name, after.name);
	add('Метрика', before.metric.title, after.metric.title);

	const metric = metrics.find((m) => m.id === after.metric.id);
	const keys = new Set([...Object.keys(before.params), ...Object.keys(after.params)]);
	for (const key of keys) {
		const label = metric?.params.find((p) => p.key === key)?.label ?? key;
		add(label, value(before.params[key]), value(after.params[key]));
	}

	for (const result of ['REJECTED', 'UNDERWRITER'] as ConditionResult[]) {
		add(
			result === 'REJECTED' ? 'Порог отказа' : 'Порог андеррайтера',
			rule(before.conditions, result),
			rule(after.conditions, result)
		);
	}

	add('Сегмент', segment(before), segment(after));
	add('Продукты', names(before.productIds, products), names(after.productIds, products));
	add('Описание', before.description ?? '—', after.description ?? '—');
	return found;
}

function value(v: unknown) {
	return v === undefined || v === null || v === '' ? '—' : String(v);
}

function rule(conditions: Condition[], result: ConditionResult) {
	const condition = conditions.find((c) => c.result === result);
	return condition ? threshold(condition) : 'нет';
}

function segment(factor: StopFactor) {
	return factor.segment ? segments[factor.segment] : 'все';
}

function names(ids: number[], products: Product[]) {
	if (!ids.length) return 'все продукты сегмента';
	return [...ids]
		.sort((a, b) => a - b)
		.map((id) => products.find((p) => p.id === id)?.name ?? `№${id}`)
		.join(', ');
}
