import type { Condition, Segment, StopFactor, StopFactorRequest } from './api/index.js';

/** A stop factor being edited in a form: params as typed text, one optional threshold per result. */
export interface FactorDraft {
	metricId: string;
	name: string;
	description: string;
	active: boolean;
	segment: Segment | '';
	params: Record<string, string>;
	rejected: Condition | null;
	underwriter: Condition | null;
	productIds: number[];
}

export function toDraft(factor?: StopFactor): FactorDraft {
	return {
		metricId: factor ? String(factor.metric.id) : '',
		name: factor?.name ?? '',
		description: factor?.description ?? '',
		active: factor?.active ?? true,
		segment: factor?.segment ?? '',
		params: Object.fromEntries(
			Object.entries(factor?.params ?? {}).map(([key, value]) => [key, String(value)])
		),
		rejected: copy(factor?.conditions.find((c) => c.result === 'REJECTED')),
		underwriter: copy(factor?.conditions.find((c) => c.result === 'UNDERWRITER')),
		productIds: [...(factor?.productIds ?? [])]
	};
}

/** The body the API takes; numbers typed as text are sent as numbers. */
export function toPayload(draft: FactorDraft, changeReason?: string): StopFactorRequest {
	const params = Object.fromEntries(
		Object.entries(draft.params)
			.map(([key, value]) => [key, value.trim()] as const)
			.filter(([, value]) => value !== '')
			.map(([key, value]) => [key, /^-?\d+(\.\d+)?$/.test(value) ? Number(value) : value])
	);
	return {
		metricId: Number(draft.metricId),
		name: draft.name.trim(),
		description: draft.description.trim() || null,
		active: draft.active,
		segment: draft.segment || null,
		params,
		conditions: [draft.rejected, draft.underwriter].filter((c): c is Condition => c !== null),
		productIds: draft.productIds,
		changeReason: changeReason?.trim() || undefined
	};
}

function copy(condition?: Condition): Condition | null {
	return condition ? { ...condition } : null;
}

/** Which applications a stop factor checks: every product, or the products of one segment. */
export type Scope = 'ALL' | Segment;

export function scopeOf(factor: StopFactor): Scope {
	return factor.segment ?? 'ALL';
}

/** Whether a stop factor checks applications of this product (as the check does: StopFactor#appliesTo). */
export function appliesTo(factor: StopFactor, product: { id: number; segment: Segment }): boolean {
	if (factor.productIds.length) return factor.productIds.includes(product.id);
	return factor.segment === null || factor.segment === product.segment;
}

/** The text a search looks in: name, description and the rule as measured. */
export function searchText(factor: StopFactor): string {
	return [factor.name, factor.description ?? '', factor.summary, factor.metric.title]
		.join(' ')
		.toLowerCase();
}

/**
 * The rule in a few words, for a list: "Непрерывная просроченная задолженность · мес. 13–24 · просрочка 1–10 дн.".
 * Known params are written short; any other one as "key value".
 */
export function ruleText(factor: StopFactor): string {
	const p = factor.params as Record<string, unknown>;
	const parts: string[] = [factor.metric.title];
	const range = (from: unknown, to: unknown, unit: string) =>
		to === undefined || to === null ? `от ${from} ${unit}` : `${from}–${to} ${unit}`;

	if (p.source !== undefined)
		parts.push(p.source === 'KIB' ? 'КИБ' : p.source === 'OB' ? 'ОБ' : String(p.source));
	if (p.periodFromMonth !== undefined)
		parts.push(`мес. ${range(p.periodFromMonth, p.periodToMonth, '').trim()}`);
	if (p.minOverdueDays !== undefined)
		parts.push(`просрочка ${range(p.minOverdueDays, p.maxOverdueDays, 'дн.')}`);
	if (p.days !== undefined) parts.push(`за ${p.days} дн.`);

	const known = new Set([
		'source',
		'periodFromMonth',
		'periodToMonth',
		'minOverdueDays',
		'maxOverdueDays',
		'days'
	]);
	for (const [key, value] of Object.entries(p)) {
		if (!known.has(key)) parts.push(`${key} ${String(value)}`);
	}
	return parts.join(' · ');
}
