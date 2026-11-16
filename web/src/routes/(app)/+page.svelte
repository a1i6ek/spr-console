<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import {
		EmptyState,
		LinkButton,
		List,
		ListItem,
		PageHeader,
		Panel,
		StatCard
	} from '#lib/components/ui/index.js';
	import { recommendations } from '#lib/overview.js';
	import { reports, type ReportSlug } from '#lib/reports.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const iso = (date: Date) =>
		`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

	const today = new Date();
	const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
	const lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1);
	const lastMonthEnd = new Date(today.getFullYear(), today.getMonth(), 0);
	const weekAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 6);
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
			detail: 'Сводка проверок с начала месяца по сегодня.',
			query: `from=${iso(monthStart)}&to=${iso(today)}`
		},
		{
			report: 'archive',
			icon: 'ri-archive-line',
			title: `Архив за ${monthName(lastMonthStart)}`,
			detail: 'Проверки и решения за прошлый месяц целиком.',
			query: `from=${iso(lastMonthStart)}&to=${iso(lastMonthEnd)}`
		},
		{
			report: 'hard-collection',
			icon: 'ri-hand-coin-line',
			title: 'Hard Collection на сегодня',
			detail: 'Заявки с просрочкой, переданные на взыскание.',
			query: `date=${iso(today)}`
		},
		{
			report: 'file',
			icon: 'ri-file-download-line',
			title: 'Выгрузка за неделю в Excel',
			detail: 'Результаты проверок за последние 7 дней.',
			query: `fileType=xlsx&from=${iso(weekAgo)}&to=${iso(today)}`
		},
		{
			report: 'inn',
			icon: 'ri-file-user-line',
			title: 'Проверки клиента по ИНН',
			detail: 'Все проверки одного клиента: введите его ИНН.',
			query: ''
		}
	];

	const reportHref = (report: ReportSlug, query: string) =>
		`${resolve('/(app)/reports/[report]', { report })}${query ? `?${query}` : ''}`;

	const liveFactors = $derived(data.factors.filter((f) => f.active));
	const offFactors = $derived(data.factors.length - liveFactors.length);
	const deciding = $derived(liveFactors.filter((f) => f.conditions.length > 0).length);

	const advice = $derived(
		recommendations(data.factors, data.metrics, data.products, {
			factors: (query) => `${resolve('/(app)/stop-factors')}${query ? `?${query}` : ''}`,
			metrics: resolve('/(app)/metrics')
		})
	);

	const greeting =
		today.getHours() < 12 ? 'Доброе утро' : today.getHours() < 18 ? 'Добрый день' : 'Добрый вечер';
</script>

<svelte:head>
	<title>{pageTitle('Обзор')}</title>
</svelte:head>

<PageHeader
	crumbs={['Обзор']}
	description="{greeting}, {page.data.username}. Отчеты, состояние правил и что стоит проверить."
/>

<div class="stats">
	<StatCard
		label="Проверяемые стоп-факторы"
		value={liveFactors.length}
		icon="ri-file-list-3-line"
		note="{deciding} с порогами"
		href={resolve('/(app)/stop-factors?active=true')}
	/>
	<StatCard
		label="Отключённые"
		value={offFactors}
		icon="ri-toggle-line"
		note="из {data.factors.length} стоп-факторов"
		href={resolve('/(app)/stop-factors?active=false')}
	/>
	<StatCard
		label="Метрики"
		value={data.metrics.filter((m) => m.active).length}
		icon="ri-ruler-line"
		note="активных из {data.metrics.length}"
		href={resolve('/(app)/metrics')}
	/>
	<StatCard
		label="Рекомендации"
		value={advice.length}
		icon="ri-lightbulb-line"
		tone={advice.length ? 'warning' : 'success'}
		note={advice.length ? 'стоит проверить' : 'всё в порядке'}
	/>
</div>

<div class="columns">
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

	<Panel title="Рекомендации" icon="ri-lightbulb-line">
		{#if advice.length}
			<List label="Рекомендации">
				{#each advice as item (item.id)}
					<ListItem title={item.title} description={item.detail}>
						{#snippet lead()}
							<span class="lead" data-tone={item.tone}
								><i class={item.icon} aria-hidden="true"></i></span
							>
						{/snippet}
						{#snippet end()}
							<LinkButton href={item.href} size="sm" variant="ghost">{item.action}</LinkButton>
						{/snippet}
					</ListItem>
				{/each}
			</List>
		{:else}
			<EmptyState icon="ri-checkbox-circle-line" title="Всё в порядке">
				Правила настроены, проверять нечего.
			</EmptyState>
		{/if}
	</Panel>
</div>

<style>
	.stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
		gap: var(--space-3);
	}

	.columns {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 28rem), 1fr));
		align-items: start;
		gap: var(--space-4);
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

	.lead[data-tone='warning'] {
		background: var(--surface-warning);
		color: var(--color-warning);
	}

	.lead[data-tone='danger'] {
		background: var(--surface-danger);
		color: var(--color-danger);
	}

	.note {
		margin-top: var(--space-3);
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}
</style>
