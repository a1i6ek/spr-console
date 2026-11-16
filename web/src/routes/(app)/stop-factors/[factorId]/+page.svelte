<script lang="ts">
	import { untrack } from 'svelte';
	import { goto, invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { factorsApi } from '#lib/api/index.js';
	import { pageTitle } from '#lib/brand.js';
	import FactorDialog from '#lib/components/stop-factors/FactorDialog.svelte';
	import ReasonConfirm from '#lib/components/stop-factors/ReasonConfirm.svelte';
	import ThresholdTags from '#lib/components/stop-factors/ThresholdTags.svelte';
	import VersionList from '#lib/components/stop-factors/VersionList.svelte';
	import {
		Badge,
		Button,
		DangerZone,
		PageHeader,
		Panel,
		Switch,
		Tag,
		notify
	} from '#lib/components/ui/index.js';
	import { formatDate, segments } from '#lib/labels.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const factor = $derived(data.factor);
	// the current version as the history has it: with the checks made with it
	const current = $derived(data.versions.find((v) => v.current));
	const metric = $derived(data.metrics.find((m) => m.id === factor.metric.id));
	const productNames = $derived(
		data.products.filter((p) => factor.productIds.includes(p.id)).map((p) => p.name)
	);
	const params = $derived(
		Object.entries(factor.params).map(([key, value]) => ({
			label: metric?.params.find((p) => p.key === key)?.label ?? key,
			value: String(value)
		}))
	);

	let editing = $state(false);

	// the confirmations that only need a reason: turning it on or off, deleting it
	type Confirm = { kind: 'status'; active: boolean } | { kind: 'remove' };
	let confirm = $state<Confirm | null>(null);
	let confirmOpen = $state(false);

	// the switch is drawn again with the saved state once the confirmation closes, whatever it
	// answered; not when it opens, which happens inside the switch's own change
	let closed = $state(0);
	$effect(() => {
		if (!confirmOpen) untrack(() => closed++);
	});

	function ask(next: Confirm) {
		confirm = next;
		confirmOpen = true;
	}

	async function run(changeReason: string) {
		if (!confirm) return;
		try {
			if (confirm.kind === 'status') {
				await factorsApi.setStatus(factor.factorId, confirm.active, changeReason);
				notify.success(confirm.active ? 'Стоп-фактор включён' : 'Стоп-фактор отключён');
			} else {
				await factorsApi.remove(factor.factorId, changeReason);
				notify.success(`Стоп-фактор «${factor.name}» удалён`);
				confirmOpen = false;
				await goto(resolve('/(app)/stop-factors'));
				return;
			}
			confirmOpen = false;
			await invalidateAll();
		} catch (err) {
			notify.error(err, 'Изменение не сохранено');
		}
	}
</script>

<svelte:head>
	<title>{pageTitle(factor.name, 'Стоп-факторы')}</title>
</svelte:head>

<PageHeader
	crumbs={[{ label: 'Стоп-факторы', href: resolve('/(app)/stop-factors') }, factor.name]}
	description={factor.description ?? undefined}
>
	{#snippet meta()}
		<Badge tone={factor.active ? 'success' : 'neutral'}
			>{factor.active ? 'Активен' : 'Отключён'}</Badge
		>
		<Badge>v{factor.versionNumber}</Badge>
	{/snippet}
	{#snippet actions()}
		<Button icon="ri-edit-line" onclick={() => (editing = true)}>Изменить</Button>
	{/snippet}
</PageHeader>

<div class="columns">
	<Panel title="Правило" icon="ri-file-list-3-line">
		<dl>
			<dt>Метрика</dt>
			<dd>
				{factor.metric.title}
				{#if metric && !metric.active}<Tag small tone="warning">Метрика отключена</Tag>{/if}
			</dd>

			{#each params as param (param.label)}
				<dt>{param.label}</dt>
				<dd class="number">{param.value}</dd>
			{/each}

			<dt>Пороги</dt>
			<dd><ThresholdTags conditions={factor.conditions} /></dd>

			<dt>Сегмент</dt>
			<dd>{factor.segment ? segments[factor.segment] : 'Все сегменты'}</dd>

			<dt>Продукты</dt>
			<dd>{productNames.length ? productNames.join(', ') : 'Все продукты сегмента'}</dd>
		</dl>
	</Panel>

	<div class="side">
		<Panel title="Статус" icon="ri-toggle-line">
			{#key closed}
				<Switch
					label={factor.active ? 'Проверяет заявки' : 'Отключён, заявки не проверяет'}
					checked={factor.active}
					onChange={(active) => ask({ kind: 'status', active })}
				/>
			{/key}
		</Panel>

		<Panel title="Текущая версия" icon="ri-git-commit-line">
			<dl>
				<dt>Версия</dt>
				<dd>v{factor.versionNumber} из {data.versions.length}</dd>
				<dt>Действует с</dt>
				<dd>{formatDate(factor.validFrom)}</dd>
				<dt>Причина</dt>
				<dd>{factor.changeReason ?? '—'}</dd>
				<dt>Проверено заявок</dt>
				<dd>
					{current?.checks?.total ?? 0}
					{#if current?.checks?.rejected}<Tag small tone="danger"
							>отказ {current.checks.rejected}</Tag
						>{/if}
					{#if current?.checks?.underwriter}<Tag small tone="warning"
							>андеррайтер {current.checks.underwriter}</Tag
						>{/if}
				</dd>
			</dl>
		</Panel>
	</div>
</div>

<Panel title="История изменений" icon="ri-history-line" flush>
	<VersionList versions={data.versions} metrics={data.metrics} products={data.products} />
</Panel>

<DangerZone
	title="Удалить стоп-фактор"
	description="Стоп-фактор перестанет проверять заявки. Его история сохранится для уже сделанных проверок."
	label="Удалить"
	onclick={() => ask({ kind: 'remove' })}
/>

<FactorDialog
	bind:open={editing}
	{factor}
	metrics={data.metrics}
	products={data.products}
	onSaved={() => invalidateAll()}
/>

{#if confirm}
	<ReasonConfirm
		bind:open={confirmOpen}
		title={confirm.kind === 'status'
			? confirm.active
				? 'Включить стоп-фактор?'
				: 'Отключить стоп-фактор?'
			: `Удалить «${factor.name}»?`}
		description={confirm.kind === 'status'
			? 'Изменение сохранится в истории стоп-фактора.'
			: 'Прошлые проверки сохранят правило, с которым они сделаны.'}
		confirmLabel={confirm.kind === 'status'
			? confirm.active
				? 'Включить'
				: 'Отключить'
			: 'Удалить'}
		tone={confirm.kind === 'status' && confirm.active ? 'info' : 'danger'}
		icon={confirm.kind === 'status' ? 'ri-toggle-line' : 'ri-delete-bin-line'}
		onConfirm={run}
	/>
{/if}

<style>
	.columns {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 24rem), 1fr));
		align-items: start;
		gap: var(--space-4);
	}

	.side {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	dl {
		display: grid;
		grid-template-columns: minmax(8rem, auto) minmax(0, 1fr);
		gap: var(--space-2) var(--space-4);
		margin: 0;
	}

	dt {
		color: var(--color-text-hint);
	}

	dd {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
	}

	.number {
		font-variant-numeric: tabular-nums;
	}
</style>
