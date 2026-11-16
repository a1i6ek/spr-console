<script lang="ts">
	import type { Metric } from '#lib/api/index.js';
	import { Badge, Tag } from '#lib/components/ui/index.js';

	let { metrics }: { metrics: Metric[] } = $props();
</script>

<section class="card" id="metrics" aria-label="Метрики">
	<header>
		<h2 class="title">Метрики</h2>
		<span class="count">{metrics.length}</span>
		<p class="description">Что измеряют стоп-факторы и какие параметры они задают.</p>
	</header>

	<ul class="rows">
		{#each metrics as metric, index (metric.id)}
			<li class="row" class:inactive={!metric.active}>
				<span class="number">{index + 1}</span>
				<span class="name">
					<span class="metric-title" title={metric.title}>{metric.title}</span>
					<span class="calculator">{metric.calculatorName}</span>
				</span>
				<span class="params">
					{#each metric.params as param (param.key)}
						<Tag small title={param.key}>{param.label}{param.required ? '' : ' · необяз.'}</Tag>
					{:else}
						<span class="muted">—</span>
					{/each}
				</span>
				<Badge tone={metric.active ? 'success' : 'neutral'}
					>{metric.active ? 'Активна' : 'Отключена'}</Badge
				>
			</li>
		{/each}
	</ul>
</section>

<style>
	.card {
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
		/* the page scrolls under the fixed header: keep the anchor clear of it */
		scroll-margin-top: calc(var(--header-height) + var(--space-3));
	}

	header {
		padding: var(--space-3) var(--space-3) var(--space-2);
		border-bottom: 1px solid var(--color-border);
	}

	.title {
		font-size: var(--text-lg);
		font-weight: 600;
	}

	.count {
		display: inline-grid;
		place-items: center;
		min-width: 22px;
		height: 20px;
		margin-left: var(--space-1);
		padding: 0 6px;
		border-radius: var(--radius-pill);
		background: var(--color-secondary);
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		font-weight: 600;
		vertical-align: 2px;
	}

	.description {
		margin: 2px 0 0;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.rows {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.row {
		display: grid;
		grid-template-columns: 2.5rem minmax(0, 1fr) minmax(0, 1.4fr) auto;
		align-items: center;
		gap: var(--space-3);
		padding: 8px var(--space-3);
	}

	.row:not(:last-child) {
		box-shadow: 0 1px 0 0 var(--color-border);
	}

	.number {
		color: var(--color-text-disabled);
		font-size: var(--text-sm);
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	.name {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}

	.metric-title {
		overflow: hidden;
		font-weight: 600;
		line-height: 1.35;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.calculator {
		overflow: hidden;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.params {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.muted {
		color: var(--color-text-hint);
	}

	.inactive .metric-title,
	.inactive .calculator,
	.inactive .number {
		color: var(--color-text-disabled);
	}

	@media (max-width: 48rem) {
		.row {
			grid-template-columns: 2.5rem minmax(0, 1fr) auto;
		}

		.params {
			grid-column: 2 / -1;
		}
	}
</style>
