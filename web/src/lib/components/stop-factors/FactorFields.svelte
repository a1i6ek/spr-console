<script lang="ts">
	import type { Metric, Product } from '#lib/api/index.js';
	import {
		Checkbox,
		FieldGrid,
		FormSection,
		Input,
		Select,
		SwitchField,
		Textarea,
		optionsOf
	} from '#lib/components/ui/index.js';
	import { segments } from '#lib/labels.js';
	import type { FactorDraft } from '#lib/stop-factors.js';
	import ConditionFields from './ConditionFields.svelte';

	type Props = {
		draft: FactorDraft;
		metrics: Metric[];
		products: Product[];
	};

	let { draft = $bindable(), metrics, products }: Props = $props();

	const metric = $derived(metrics.find((m) => String(m.id) === draft.metricId));
	// an inactive metric is offered only to the stop factor that already uses it
	const metricOptions = $derived(
		metrics
			.filter((m) => m.active || String(m.id) === draft.metricId)
			.map((m) => ({ value: String(m.id), label: m.title, description: m.calculatorName }))
	);

	function toggleProduct(id: number, checked: boolean) {
		draft.productIds = checked
			? [...draft.productIds, id]
			: draft.productIds.filter((p) => p !== id);
	}
</script>

<FormSection title="Стоп-фактор" description="Что измеряется и как правило называется в отчетах.">
	<Select
		label="Метрика"
		bind:value={draft.metricId}
		options={metricOptions}
		placeholder="Выберите метрику"
		required
	/>
	<Input label="Название" bind:value={draft.name} maxlength={255} required />
	<Textarea label="Описание" bind:value={draft.description} rows={2} />
	<SwitchField
		label="Активен"
		description="Неактивный стоп-фактор не проверяется."
		bind:checked={draft.active}
	/>
</FormSection>

{#if metric?.params.length}
	<FormSection title="Параметры" description="Значения параметров метрики для этого стоп-фактора.">
		<FieldGrid>
			{#each metric.params as param (param.key)}
				<Input
					label={param.label}
					bind:value={
						() => draft.params[param.key] ?? '', (value) => (draft.params[param.key] = value)
					}
					required={param.required}
				/>
			{/each}
		</FieldGrid>
	</FormSection>
{/if}

<FormSection
	title="Пороги"
	description="Отказ проверяется первым; андеррайтер — только если заявка не отклонена."
>
	<ConditionFields result="REJECTED" bind:condition={draft.rejected} />
	<ConditionFields result="UNDERWRITER" bind:condition={draft.underwriter} />
</FormSection>

<FormSection
	title="Применение"
	description="Без отмеченных продуктов стоп-фактор проверяет все продукты сегмента."
>
	<Select
		label="Сегмент"
		bind:value={draft.segment}
		options={optionsOf(segments)}
		placeholder="Все сегменты"
		clearable
	/>
	<div class="products">
		{#each products as product (product.id)}
			<Checkbox
				label={product.name}
				checked={draft.productIds.includes(product.id)}
				onChange={(checked) => toggleProduct(product.id, checked)}
			/>
		{/each}
	</div>
</FormSection>

<style>
	.products {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
		gap: var(--space-2) var(--space-3);
	}
</style>
