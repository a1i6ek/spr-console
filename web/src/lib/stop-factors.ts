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
