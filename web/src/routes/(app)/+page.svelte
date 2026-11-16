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
			<span class="cta">Открыть<i class="ri-arrow-right-line" aria-hidden="true"></i></span>
		</a>
	{/each}
</div>

<style>
	.welcome {
		margin-bottom: var(--space-5);
	}

	.welcome h1 {
		margin: 0;
		font-size: var(--text-2xl);
		font-weight: 600;
	}

	.subtitle {
		margin: 6px 0 0;
		color: var(--color-text-hint);
		font-size: var(--text-base);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
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
			transform var(--speed),
			box-shadow var(--speed),
			border-color var(--speed);
	}

	/* the lift: a quiet card answers the pointer by rising a little, taking a
	   shadow, and tinting its border toward the brand — as a button press does */
	.tile:hover {
		transform: translateY(-2px);
		border-color: color-mix(in srgb, var(--color-brand) 35%, var(--color-border));
		box-shadow: var(--shadow-md);
	}

	.tile:focus-visible {
		outline: 2px solid var(--color-info);
		outline-offset: 2px;
	}

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-width: 0;
	}

	.icon {
		display: grid;
		flex: none;
		place-items: center;
		width: 40px;
		height: 40px;
		border-radius: var(--radius-md);
		background: var(--color-brand-subtle);
		color: var(--color-brand);
		font-size: 20px;
		transition:
			background-color var(--speed),
			color var(--speed);
	}

	.tile:hover .icon {
		background: var(--color-brand);
		color: var(--color-brand-text);
	}

	.title {
		overflow: hidden;
		font-weight: 600;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.detail {
		display: -webkit-box;
		flex: 1;
		margin: 0;
		overflow: hidden;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		line-height: 1.5;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		line-clamp: 2;
	}

	.cta {
		display: inline-flex;
		align-items: center;
		gap: 5px;
		color: var(--color-brand);
		font-size: var(--text-sm);
		font-weight: 600;
	}

	.cta i {
		font-size: 14px;
		transition: transform var(--speed);
	}

	.tile:hover .cta,
	.tile:focus-visible .cta {
		color: var(--color-brand-hover);
	}

	.tile:hover .cta i,
	.tile:focus-visible .cta i {
		transform: translateX(3px);
	}

	@media (prefers-reduced-motion: reduce) {
		.tile,
		.icon,
		.cta i {
			transition: none;
		}

		.tile:hover {
			transform: none;
		}

		.tile:hover .cta i {
			transform: none;
		}
	}
</style>
