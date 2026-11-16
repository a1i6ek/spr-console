<script lang="ts">
	import { DatePicker as ArkDatePicker } from '@ark-ui/svelte/date-picker';
	import { Portal } from '@ark-ui/svelte/portal';
	import type { IconName } from './icon';
	import { formatMonth, parseMonth, timeZone, toMonthDate, toMonthIso } from './date';
	import FieldText from './FieldText.svelte';

	type Props = {
		label: string;
		value: string;
		min?: string;
		max?: string;
		icon?: IconName;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
		readOnly?: boolean;
		clearable?: boolean;
		compact?: boolean;
		name?: string;
		id?: string;
		onChange?: (value: string) => void;
	};

	let {
		label,
		value = $bindable(''),
		min,
		max,
		icon,
		hint,
		error,
		required,
		disabled,
		readOnly,
		clearable = false,
		compact = false,
		name,
		id,
		onChange
	}: Props = $props();

	const selected = $derived(toMonthDate(value));
	const canClear = $derived(clearable && value !== '' && !disabled && !readOnly);

	let open = $state(false);
</script>

<ArkDatePicker.Root
	class="field-root"
	data-invalid={error !== undefined || undefined}
	{id}
	{name}
	{required}
	{disabled}
	{readOnly}
	invalid={error !== undefined}
	locale="ru-RU"
	{timeZone}
	minView="month"
	maxView="month"
	value={selected ? [selected] : []}
	min={toMonthDate(min)}
	max={toMonthDate(max)}
	format={formatMonth}
	parse={parseMonth}
	placeholder="мм.гггг"
	positioning={{ placement: 'bottom-start', gutter: 4 }}
	onOpenChange={(details) => (open = details.open)}
	onValueChange={(details) => {
		const [day] = details.value;
		const next = day ? toMonthIso(day) : '';
		if (next === value) return;

		value = next;
		onChange?.(next);
	}}
>
	<div
		class="field-box"
		data-float={value !== '' || undefined}
		data-open={open || undefined}
		data-compact={compact || undefined}
	>
		<ArkDatePicker.Label><FieldText {label} {icon} {required} /></ArkDatePicker.Label>

		<ArkDatePicker.Control>
			<ArkDatePicker.Input />

			{#if canClear}
				<ArkDatePicker.ClearTrigger aria-label="Очистить «{label}»">
					<i class="ri-close-line" aria-hidden="true"></i>
				</ArkDatePicker.ClearTrigger>
			{/if}

			<ArkDatePicker.Trigger aria-label="Выбрать месяц: {label}">
				<i class="ri-calendar-line" aria-hidden="true"></i>
			</ArkDatePicker.Trigger>
		</ArkDatePicker.Control>
	</div>

	<Portal>
		<ArkDatePicker.Positioner>
			<ArkDatePicker.Content>
				<ArkDatePicker.View view="month">
					<ArkDatePicker.Context>
						{#snippet render(context)}
							{@const api = context()}
							<ArkDatePicker.ViewControl>
								<ArkDatePicker.PrevTrigger aria-label="Предыдущий год">
									<i class="ri-arrow-left-s-line" aria-hidden="true"></i>
								</ArkDatePicker.PrevTrigger>
								<ArkDatePicker.ViewTrigger>
									<ArkDatePicker.RangeText />
								</ArkDatePicker.ViewTrigger>
								<ArkDatePicker.NextTrigger aria-label="Следующий год">
									<i class="ri-arrow-right-s-line" aria-hidden="true"></i>
								</ArkDatePicker.NextTrigger>
							</ArkDatePicker.ViewControl>
							<ArkDatePicker.Table>
								<ArkDatePicker.TableBody>
									{#each api.getMonthsGrid( { columns: 3, format: 'short' } ) as months, index (index)}
										<ArkDatePicker.TableRow>
											{#each months as month (month.value)}
												<ArkDatePicker.TableCell value={month.value}>
													<ArkDatePicker.TableCellTrigger
														>{month.label}</ArkDatePicker.TableCellTrigger
													>
												</ArkDatePicker.TableCell>
											{/each}
										</ArkDatePicker.TableRow>
									{/each}
								</ArkDatePicker.TableBody>
							</ArkDatePicker.Table>
						{/snippet}
					</ArkDatePicker.Context>
				</ArkDatePicker.View>
			</ArkDatePicker.Content>
		</ArkDatePicker.Positioner>
	</Portal>

	{#if error}
		<span data-part="error-text">{error}</span>
	{:else if hint}
		<span data-part="helper-text">{hint}</span>
	{/if}
</ArkDatePicker.Root>
