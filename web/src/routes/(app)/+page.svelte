<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import {
		LinkButton,
		List,
		ListItem,
		PageHeader,
		Panel,
		StatCard
	} from '#lib/components/ui/index.js';
	import { reports, type ReportSlug } from '#lib/reports.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const iso = (date: Date) =>
		`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

	const today = new Date();
	const threeDaysAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 3);
	const monthName = (date: Date) => date.toLocaleString('ru-RU', { month: 'long' });

	/** The reports people run most, with their period already filled in. */
	const reportActions: {
		report: ReportSlug;
		icon: string;
		title: string;
		detail: string;
		query: string;
	}[] = [
		{
			report: 'general',
			icon: 'ri-file-chart-line',
			title: `Общий отчет за ${monthName(today)}`,
			detail: 'Excel по всем проверкам месяца.',
			query: `month=${iso(today).slice(0, 7)}`
		},
		{
			report: 'archive',
			icon: 'ri-archive-line',
			title: 'Архив за последние 3 дня',
			detail: 'Файлы всех проверок за период одним архивом.',
			query: `from=${iso(threeDaysAgo)}&to=${iso(today)}`
		},
		{
			report: 'file',
			icon: 'ri-file-download-line',
			title: 'Файл проверки',
			detail: 'Файл одной проверки по ее scoringId.',
			query: ''
		},
		{
			report: 'inn',
			icon: 'ri-file-user-line',
			title: 'Проверки клиента по ИНН',
			detail: 'Файлы всех проверок клиента одним архивом.',
			query: ''
		}
	];

	const reportHref = (report: ReportSlug, query: string) =>
		`${resolve('/(app)/reports/[report]', { report })}${query ? `?${query}` : ''}`;

	const liveFactors = $derived(data.factors.filter((f) => f.active));
	const offFactors = $derived(data.factors.length - liveFactors.length);
	const deciding = $derived(liveFactors.filter((f) => f.conditions.length > 0).length);

	const greeting =
		today.getHours() < 12 ? 'Доброе утро' : today.getHours() < 18 ? 'Добрый день' : 'Добрый вечер';
</script>

<svelte:head>
	<title>{pageTitle('Обзор')}</title>
</svelte:head>

<PageHeader
	crumbs={['Обзор']}
	description="{greeting}, {page.data.username}. Отчеты и состояние правил проверки."
/>

<div class="stats">
	<StatCard
		label="Проверяемые стоп-факторы"
		value={liveFactors.length}
		icon="ri-file-list-3-line"
		note="{deciding} с порогами"
		href={resolve('/(app)/stop-factors?status=active')}
	/>
	<StatCard
		label="Отключённые"
		value={offFactors}
		icon="ri-toggle-line"
		note="из {data.factors.length} стоп-факторов"
		href={resolve('/(app)/stop-factors?status=off')}
	/>
	<StatCard
		label="Метрики"
		value={data.metrics.filter((m) => m.active).length}
		icon="ri-ruler-line"
		note="активных из {data.metrics.length}"
		href={resolve('/(app)/stop-factors')}
	/>
</div>

<Panel title="Отчеты" icon="ri-file-chart-line">
	<List label="Быстрые отчеты">
		{#each reportActions as action (action.report)}
			<ListItem title={action.title} description={action.detail}>
				{#snippet lead()}
					<span class="lead"><i class={action.icon} aria-hidden="true"></i></span>
				{/snippet}
				{#snippet end()}
					<LinkButton href={reportHref(action.report, action.query)} size="sm" variant="outline">
						{action.query ? 'Сформировать' : 'Открыть'}
					</LinkButton>
				{/snippet}
			</ListItem>
		{/each}
	</List>
	<p class="note">
		Все отчеты: {Object.values(reports)
			.map((r) => r.title)
			.join(', ')}.
	</p>
</Panel>

<style>
	.stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
		gap: var(--space-3);
	}

	.lead {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border-radius: var(--radius-md);
		background: var(--color-brand-subtle);
		color: var(--color-brand);
		font-size: 18px;
	}

	.note {
		margin-top: var(--space-3);
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}
</style>
