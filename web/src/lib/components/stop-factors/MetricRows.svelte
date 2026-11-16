<script lang="ts">
	import type { Metric } from '#lib/api/index.js';
	import { Badge, Tag } from '#lib/components/ui/index.js';

	let { metrics }: { metrics: Metric[] } = $props();
</script>

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

<style>
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
