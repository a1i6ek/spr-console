/** Shapes of the console API (kg.bakai.stopfactor.dto). */

export type Segment = 'RETAIL' | 'MSB';
export type ConditionResult = 'REJECTED' | 'UNDERWRITER';
export type Operator = 'GT' | 'GTE' | 'LT' | 'LTE' | 'EQ' | 'RANGE' | 'BETWEEN' | 'IN';
export type ValueType = 'STRING' | 'INTEGER' | 'DOUBLE' | 'ARRAY';

export interface Condition {
	result: ConditionResult;
	operator: Operator;
	value: string;
	valueTo: string | null;
	valueType: ValueType;
}

/** The rule of a stop factor, as a form edits it and the API takes it. */
export interface StopFactorRule {
	metricId: number;
	name: string;
	description: string | null;
	active: boolean;
	segment: Segment | null;
	params: Record<string, unknown>;
	conditions: Condition[];
	productIds: number[];
}

/**
 * A version of a stop factor. factorId stays the same across its versions; every change makes the
 * next one and closes the one before.
 */
export interface StopFactor {
	factorId: number;
	versionId: number;
	versionNumber: number;
	current: boolean;
	metric: { id: number; title: string };
	name: string;
	description: string | null;
	active: boolean;
	segment: Segment | null;
	params: Record<string, unknown>;
	conditions: Condition[];
	productIds: number[];
	summary: string;
	orderNumber: number;
	validFrom: string;
	validTo: string | null;
	changeReason: string | null;
	endReason: string | null;
	/** Scorings checked with this version; only in the history. */
	checks: Checks | null;
}

/** How many scorings a version checked, and how many it rejected or sent to the underwriter. */
export interface Checks {
	total: number;
	rejected: number;
	underwriter: number;
}

/** A stop factor as POST and PUT /stop-factors take it; a change needs its reason. */
export interface StopFactorRequest extends StopFactorRule {
	changeReason?: string;
}

export interface MetricParam {
	key: string;
	label: string;
	required: boolean;
}

export interface Metric {
	id: number;
	title: string;
	calculator: string;
	calculatorName: string;
	params: MetricParam[];
	active: boolean;
}

export interface Product {
	id: number;
	name: string;
	segment: Segment;
}
