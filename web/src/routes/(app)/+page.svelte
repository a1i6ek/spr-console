<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import type { ReportSlug } from '#lib/reports.js';

	const iso = (date: Date) =>
		`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

	const today = new Date();
	const threeDaysAgo = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 3);
	const monthName = (date: Date) => date.toLocaleString('ru-RU', { month: 'long' });

	/** The reports people run most, with their period already filled in. */
	const actions: {
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
			title: 'Получить файл',
			detail: 'Скачать файл скоринга по его идентификатору.',
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

	const href = (report: ReportSlug, query: string) =>
		`${resolve('/(app)/reports/[report]', { report })}${query ? `?${query}` : ''}`;
</script>

<svelte:head>
	<title>{pageTitle('Обзор')}</title>
</svelte:head>

<div class="welcome">
	<h1>Добро пожаловать, {page.data.username}</h1>
	<p class="subtitle">Выберите действие</p>
</div>

<div class="tiles">
	{#each actions as action (action.report)}
		<!-- the address is resolved in href() -->
		<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -->
		<a class="tile" href={href(action.report, action.query)}>
			<div class="head">
				<span class="icon"><i class={action.icon} aria-hidden="true"></i></span>
				<span class="title">{action.title}</span>
			</div>
			<p class="detail">{action.detail}</p>
			<span class="cta">Открыть →</span>
		</a>
	{/each}
</div>

<style>
	.welcome h1 {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 600;
	}

	.subtitle {
		margin: var(--space-1) 0 0;
		color: var(--color-text-hint);
		font-size: var(--text-base);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
		gap: var(--space-3);
	}

	.tile {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
		color: inherit;
		text-decoration: none;
		transition:
			background-color var(--speed),
			border-color var(--speed);
	}

	.tile:hover {
		border-color: var(--color-border);
		background: var(--color-surface-alt);
	}

	.tile:focus-visible {
		outline: 2px solid var(--color-info);
		outline-offset: 2px;
	}

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.icon {
		display: grid;
		flex: none;
		place-items: center;
		width: 36px;
		height: 36px;
		border-radius: var(--radius-md);
		background: var(--color-brand-subtle);
		color: var(--color-brand);
		font-size: 18px;
	}

	.title {
		overflow: hidden;
		font-weight: 600;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.detail {
		flex: 1;
		margin: 0;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		line-height: 1.5;
	}

	.cta {
		color: var(--color-brand);
		font-size: var(--text-sm);
		font-weight: 600;
	}

	.tile:hover .cta,
	.tile:focus-visible .cta {
		color: var(--color-brand-hover);
	}
</style>
