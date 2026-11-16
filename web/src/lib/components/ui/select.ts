import type { IconName } from './icon';

export type SelectOption<Value extends string = string> = {
	value: Value;
	label?: string;
	description?: string;
	icon?: IconName;
	disabled?: boolean;
};

/** Options from a { value: label } record, in its order. */
export function optionsOf<Value extends string>(
	labels: Record<Value, string>
): SelectOption<Value>[] {
	return (Object.keys(labels) as Value[]).map((value) => ({ value, label: labels[value] }));
}
