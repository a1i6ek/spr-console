<script lang="ts">
	import { pageTitle } from '#lib/brand.js';
	import {
		Badge,
		CellText,
		DataTable,
		PageHeader,
		Tag,
		type Column
	} from '#lib/components/ui/index.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const columns: Column[] = [
		{ key: 'id', label: '№', min: '3rem' },
		{ key: 'title', label: 'Метрика', min: '18rem', grow: true },
		{ key: 'params', label: 'Параметры', min: '20rem' },
		{ key: 'status', label: 'Статус', min: '8rem' }
	];
	const rows = $derived(data.metrics.map((metric) => ({ ...metric, id: String(metric.id) })));
</script>

<svelte:head>
	<title>{pageTitle('Метрики')}</title>
</svelte:head>

<PageHeader
	crumbs={['Метрики']}
	count={data.metrics.length}
	description="Что измеряют стоп-факторы и какие параметры они задают."
/>

<DataTable {columns} {rows} empty="Метрик нет.">
	{#snippet row(metric)}
		<td><span class="number">{metric.id}</span></td>
		<td><CellText title={metric.title} detail={metric.calculatorName} /></td>
		<td>
			<span class="tags">
				{#each metric.params as param (param.key)}
					<Tag small title={param.key}>{param.label}{param.required ? '' : ' · необяз.'}</Tag>
				{:else}
					<span class="muted">—</span>
				{/each}
			</span>
		</td>
		<td
			><Badge tone={metric.active ? 'success' : 'neutral'}
				>{metric.active ? 'Активна' : 'Отключена'}</Badge
			></td
		>
	{/snippet}
</DataTable>

<style>
	.number {
		color: var(--color-text-hint);
		font-variant-numeric: tabular-nums;
	}

	.tags {
		display: inline-flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.muted {
		color: var(--color-text-hint);
	}
</style>
