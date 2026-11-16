<script lang="ts">
	import type { Condition, ConditionResult, Operator, ValueType } from '#lib/api/index.js';
	import { FieldGrid, Input, Select, SwitchField, optionsOf } from '#lib/components/ui/index.js';
	import { operators, results, valueTypes } from '#lib/labels.js';

	type Props = {
		result: ConditionResult;
		/** null: the stop factor has no such threshold */
		condition: Condition | null;
	};

	let { result, condition = $bindable() }: Props = $props();

	const descriptions: Record<ConditionResult, string> = {
		REJECTED: 'Заявка отклоняется, когда значение попадает в порог.',
		UNDERWRITER: 'Заявка уходит андеррайтеру, если она не отклонена.'
	};

	const operatorOptions = optionsOf(operators).filter((option) => option.value !== 'IN');
	const isList = $derived(condition?.valueType === 'ARRAY' || condition?.valueType === 'STRING');
	const hasRange = $derived(condition?.operator === 'RANGE' || condition?.operator === 'BETWEEN');

	function toggle(enabled: boolean) {
		condition = enabled
			? { result, operator: 'GT', value: '', valueTo: null, valueType: 'INTEGER' }
			: null;
	}

	// a status list is always compared with IN, numbers never are
	function setValueType(valueType: ValueType) {
		if (!condition) return;
		const list = valueType === 'ARRAY' || valueType === 'STRING';
		condition.valueType = valueType;
		condition.operator = list ? 'IN' : condition.operator === 'IN' ? 'GT' : condition.operator;
	}

	function setOperator(operator: Operator) {
		if (!condition) return;
		condition.operator = operator;
		if (operator !== 'RANGE' && operator !== 'BETWEEN') condition.valueTo = null;
	}
</script>

<SwitchField
	label={results[result]}
	description={descriptions[result]}
	checked={!!condition}
	onChange={toggle}
/>

{#if condition}
	<FieldGrid columns={isList ? 2 : hasRange ? 2 : 3}>
		<Select
			label="Тип значения"
			value={condition.valueType}
			options={optionsOf(valueTypes)}
			onChange={setValueType}
			required
		/>
		{#if !isList}
			<Select
				label="Условие"
				value={condition.operator}
				options={operatorOptions}
				onChange={setOperator}
				required
			/>
		{/if}
		<Input
			label={isList ? 'Статусы через запятую' : hasRange ? 'От' : 'Значение'}
			bind:value={condition.value}
			required
		/>
		{#if hasRange}
			<Input
				label="До"
				bind:value={
					() => condition?.valueTo ?? '', (value) => condition && (condition.valueTo = value)
				}
				required
			/>
		{/if}
	</FieldGrid>
{/if}
