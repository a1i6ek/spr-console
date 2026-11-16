<script lang="ts">
	import { untrack } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import FactorDialog from '#lib/components/stop-factors/FactorDialog.svelte';
	import FactorList, { type Section } from '#lib/components/stop-factors/FactorList.svelte';
	import MetricsDialog from '#lib/components/stop-factors/MetricsDialog.svelte';
	import {
		Button,
		EmptyState,
		PageHeader,
		SearchInput,
		SegmentedControl,
		Select
	} from '#lib/components/ui/index.js';
	import { segments } from '#lib/labels.js';
	import { appliesTo, scopeOf, searchText, type Scope } from '#lib/stop-factors.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	type Status = 'all' | 'active' | 'off';

	// The filters start from the URL, so a filtered list can be shared and reloaded; they apply at once, in the
	// browser, and are written back to the URL without loading anything.
	const params = page.url.searchParams;
	let search = $state(params.get('q') ?? '');
	let status = $state<Status>((params.get('status') as Status | null) ?? 'all');
	let productId = $state(params.get('product') ?? '');
	let metricId = $state(params.get('metric') ?? '');

	// Shallow routing changes the address bar, not page.url: the query is compared with the address bar, and the
	// effect reads only the filters, or each write would run it again.
	$effect(() => {
		const filters: [string, string][] = [
			['q', search.trim()],
			['status', status === 'all' ? '' : status],
			['product', productId],
			['metric', metricId]
		];
		const query = new URLSearchParams(filters.filter(([, value]) => value)).toString();
		const search_ = query ? `?${query}` : '';
		untrack(() => {
			if (search_ === location.search) return;
			goto(resolve(`/(app)/stop-factors${search_}` as `/(app)/stop-factors?${string}`), {
				state: page.state,
				shallow: true,
				replace: true
			});
		});
	});

	let creating = $state(false);
	let showMetrics = $state(false);

	const product = $derived(data.products.find((p) => String(p.id) === productId));
	const needle = $derived(search.trim().toLowerCase());

	// everything but the status: the status tabs count what the other filters leave
	const matching = $derived(
		data.factors.filter(
			(f) =>
				(!needle || searchText(f).includes(needle)) &&
				(!product || appliesTo(f, product)) &&
				(!metricId || String(f.metric.id) === metricId)
		)
	);
	const counts = $derived({
		all: matching.length,
		active: matching.filter((f) => f.active).length,
		off: matching.filter((f) => !f.active).length
	});
	const shown = $derived(
		matching.filter((f) => status === 'all' || f.active === (status === 'active'))
	);

	const filtered = $derived(!!needle || !!product || !!metricId || status !== 'all');

	function reset() {
		search = '';
		status = 'all';
		productId = '';
		metricId = '';
	}

	const productNames = (scope: Scope) =>
		data.products
			.filter((p) => p.segment === scope)
			.map((p) => p.name)
			.join(', ');

	const sections = $derived<Section[]>(
		(
			[
				{
					key: 'ALL',
					title: 'Все продукты',
					description: 'Проверяются в заявках по любому продукту.'
				},
				{ key: 'RETAIL', title: segments.RETAIL, description: productNames('RETAIL') },
				{ key: 'MSB', title: segments.MSB, description: productNames('MSB') }
			] as const
		)
			.map((s) => ({ ...s, factors: shown.filter((f) => scopeOf(f) === s.key) }))
			.filter((s) => s.factors.length > 0)
	);

	const statusOptions = $derived([
		{ value: 'all' as const, label: `Все · ${counts.all}` },
		{ value: 'active' as const, label: `Активные · ${counts.active}` },
		{ value: 'off' as const, label: `Отключённые · ${counts.off}` }
	]);
	const productOptions = $derived(
		data.products.map((p) => ({
			value: String(p.id),
			label: p.name,
			description: segments[p.segment]
		}))
	);
	const metricOptions = $derived(
		data.metrics.map((m) => ({ value: String(m.id), label: m.title }))
	);
</script>

<svelte:head>
	<title>{pageTitle('Стоп-факторы')}</title>
</svelte:head>

<PageHeader
	crumbs={['Стоп-факторы']}
	count={data.factors.length}
	description="Правила, по которым проверяется каждая заявка. Любое изменение сохраняется в истории стоп-фактора."
>
	{#snippet actions()}
		<Button variant="outline" icon="ri-ruler-line" onclick={() => (showMetrics = true)}>
			Метрики
		</Button>
		<Button icon="ri-add-line" onclick={() => (creating = true)}>Добавить стоп-фактор</Button>
	{/snippet}
</PageHeader>

<div class="filters">
	<div class="search">
		<SearchInput
			label="Поиск стоп-факторов"
			placeholder="Поиск по названию или правилу"
			bind:value={search}
		/>
	</div>
	<Select
		label="Продукт"
		bind:value={productId}
		options={productOptions}
		placeholder="Все"
		clearable
		compact
	/>
	<Select
		label="Метрика"
		bind:value={metricId}
		options={metricOptions}
		placeholder="Все"
		clearable
		compact
	/>
</div>

<div class="bar">
	<SegmentedControl
		label="Статус"
		options={statusOptions}
		value={status}
		onChange={(v) => (status = v)}
	/>
	{#if filtered}
		<span class="shown">
			Показано {shown.length} из {data.factors.length}
			<Button variant="ghost" size="sm" icon="ri-close-line" onclick={reset}
				>Сбросить фильтры</Button
			>
		</span>
	{/if}
</div>

{#if sections.length}
	<FactorList {sections} products={data.products} />
{:else}
	<EmptyState icon="ri-search-line" title="Ничего не найдено" size="lg">
		Ни один стоп-фактор не подходит под выбранные фильтры.
		{#snippet actions()}
			<Button variant="outline" icon="ri-close-line" onclick={reset}>Сбросить фильтры</Button>
		{/snippet}
	</EmptyState>
{/if}

<MetricsDialog bind:open={showMetrics} metrics={data.metrics} />

<FactorDialog
	bind:open={creating}
	metrics={data.metrics}
	products={data.products}
	onSaved={(saved) =>
		goto(resolve('/(app)/stop-factors/[factorId]', { factorId: String(saved.factorId) }))}
/>

<style>
	.filters {
		display: grid;
		grid-template-columns: minmax(16rem, 1fr) auto auto;
		gap: var(--space-2);
		align-items: center;
	}

	.bar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
	}

	.shown {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	@media (max-width: 48rem) {
		.filters {
			grid-template-columns: 1fr 1fr;
		}

		.search {
			grid-column: 1 / -1;
		}
	}
</style>
