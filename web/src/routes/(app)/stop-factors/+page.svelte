<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import type { FactorFilter } from '#lib/api/index.js';
	import { pageTitle } from '#lib/brand.js';
	import FactorDialog from '#lib/components/stop-factors/FactorDialog.svelte';
	import ThresholdTags from '#lib/components/stop-factors/ThresholdTags.svelte';
	import {
		Badge,
		Button,
		CellText,
		DataTable,
		PageHeader,
		SearchInput,
		Select,
		Toolbar,
		optionsOf,
		type Column
	} from '#lib/components/ui/index.js';
	import { formatDay, segments } from '#lib/labels.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	// svelte-ignore state_referenced_locally
	let search = $state(data.filter.search);

	let creating = $state(false);

	const open = (factorId: number) =>
		goto(resolve('/(app)/stop-factors/[factorId]', { factorId: String(factorId) }));

	const metricOptions = $derived(
		data.metrics.map((m) => ({ value: String(m.id), label: m.title }))
	);
	const productOptions = $derived(
		data.products.map((p) => ({
			value: String(p.id),
			label: p.name,
			description: segments[p.segment]
		}))
	);
	const activeOptions = optionsOf({ true: 'Активные', false: 'Отключённые' });

	/** A filter applies at once; it lives in the URL, so a search can be shared and reloaded. */
	function apply(key: keyof FactorFilter, value: string) {
		const url = new URL(page.url.href);
		if (value) url.searchParams.set(key, value);
		else url.searchParams.delete(key);
		goto(resolve(`/(app)/stop-factors${url.search}` as `/(app)/stop-factors?${string}`), {
			replace: true,
			reset: false
		});
	}

	const columns: Column[] = [
		{ key: 'name', label: 'Стоп-фактор', min: '14rem', grow: true },
		{ key: 'segment', label: 'Сегмент', min: '8rem' },
		{ key: 'thresholds', label: 'Пороги', min: '9rem' },
		{ key: 'version', label: 'Версия', min: '7rem' },
		{ key: 'status', label: 'Статус', min: '7rem' }
	];
	const rows = $derived(data.factors.map((factor) => ({ ...factor, id: String(factor.factorId) })));
</script>

<svelte:head>
	<title>{pageTitle('Стоп-факторы')}</title>
</svelte:head>

<PageHeader
	crumbs={['Стоп-факторы']}
	count={data.factors.length}
	description="Правила проверки заявок. Каждое изменение сохраняется в истории стоп-фактора."
>
	{#snippet actions()}
		<Button icon="ri-add-line" onclick={() => (creating = true)}>Стоп-фактор</Button>
	{/snippet}
</PageHeader>

<Toolbar>
	<SearchInput
		label="Поиск стоп-факторов"
		placeholder="Название или описание"
		bind:value={search}
		size="sm"
		onsubmit={() => apply('search', search.trim())}
	/>
	<Select
		label="Метрика"
		value={data.filter.metricId}
		options={metricOptions}
		placeholder="Все"
		clearable
		compact
		onChange={(v) => apply('metricId', v)}
	/>
	<Select
		label="Продукт"
		value={data.filter.productId}
		options={productOptions}
		placeholder="Все"
		clearable
		compact
		onChange={(v) => apply('productId', v)}
	/>
	<Select
		label="Сегмент"
		value={data.filter.segment}
		options={optionsOf(segments)}
		placeholder="Все"
		clearable
		compact
		onChange={(v) => apply('segment', v)}
	/>
	<Select
		label="Статус"
		value={data.filter.active}
		options={activeOptions}
		placeholder="Все"
		clearable
		compact
		onChange={(v) => apply('active', v)}
	/>
</Toolbar>

<DataTable
	{columns}
	{rows}
	empty="Ничего не найдено. Измените поиск или сбросьте фильтры."
	onOpen={(factor) => open(factor.factorId)}
	label={(factor) => `Открыть «${factor.name}»`}
>
	{#snippet row(factor)}
		<td><CellText title={factor.name} detail={factor.summary ?? factor.metric.title} /></td>
		<td>
			{factor.segment ? segments[factor.segment] : 'Все'}
		</td>
		<td><ThresholdTags conditions={factor.conditions} /></td>
		<td>
			<CellText title="v{factor.versionNumber}" detail={formatDay(factor.validFrom)} />
		</td>
		<td>
			<Badge tone={factor.active ? 'success' : 'neutral'}
				>{factor.active ? 'Активен' : 'Отключён'}</Badge
			>
		</td>
	{/snippet}
</DataTable>

<FactorDialog
	bind:open={creating}
	metrics={data.metrics}
	products={data.products}
	onSaved={(saved) => open(saved.factorId)}
/>
