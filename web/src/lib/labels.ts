import type { Condition, ConditionResult, Operator, Segment, ValueType } from './api/index.js';

export const segments: Record<Segment, string> = { RETAIL: 'Розница', MSB: 'МСБ' };

export const results: Record<ConditionResult, string> = {
	REJECTED: 'Отказ',
	UNDERWRITER: 'Андеррайтер'
};

export const operators: Record<Operator, string> = {
	GT: '>',
	GTE: '≥',
	LT: '<',
	LTE: '≤',
	EQ: '=',
	RANGE: '> и ≤',
	BETWEEN: '≥ и ≤',
	IN: 'входит в'
};

export const valueTypes: Record<ValueType, string> = {
	INTEGER: 'Целое число',
	DOUBLE: 'Число',
	STRING: 'Статус',
	ARRAY: 'Список статусов'
};

export function formatDate(value: string | null) {
	return value
		? new Date(value).toLocaleString('ru-RU', { dateStyle: 'medium', timeStyle: 'short' })
		: '—';
}

/** "16.11.2026" */
export function formatDay(value: string | null) {
	return value ? new Date(value).toLocaleDateString('ru-RU') : '—';
}

/** "> 5", "> 5 и ≤ 10", "статусы" */
export function threshold(condition: Condition) {
	switch (condition.operator) {
		case 'IN':
			return 'статусы';
		case 'RANGE':
			return `> ${condition.value} и ≤ ${condition.valueTo}`;
		case 'BETWEEN':
			return `≥ ${condition.value} и ≤ ${condition.valueTo}`;
		default:
			return `${operators[condition.operator]} ${condition.value}`;
	}
}
