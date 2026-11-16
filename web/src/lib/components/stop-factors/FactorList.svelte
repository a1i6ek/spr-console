<script lang="ts">
	import { resolve } from '$app/paths';
	import type { Product, StopFactor } from '#lib/api/index.js';
	import { Badge } from '#lib/components/ui/index.js';
	import { formatDay } from '#lib/labels.js';
	import { ruleText } from '#lib/stop-factors.js';
	import ThresholdTags from './ThresholdTags.svelte';

	export type Section = {
		key: string;
		title: string;
		/** Which products the section's stop factors check. */
		description: string;
		factors: StopFactor[];
	};

	type Props = {
		sections: Section[];
		products: Product[];
	};

	let { sections, products }: Props = $props();

	const href = (factor: StopFactor) =>
		resolve('/(app)/stop-factors/[factorId]', { factorId: String(factor.factorId) });

	/** A stop factor limited to some products of its section names them; one for the whole section says nothing. */
	function limitedTo(factor: StopFactor): string | null {
		if (!factor.productIds.length) return null;
		const ofSegment = products.filter((p) => p.segment === factor.segment);
		if (ofSegment.length && ofSegment.every((p) => factor.productIds.includes(p.id))) return null;
		return products
			.filter((p) => factor.productIds.includes(p.id))
			.map((p) => p.name)
			.join(', ');
	}
</script>

<div class="card">
	<div class="scroller">
		<table>
			<thead>
				<tr>
					<th scope="col" class="w-number"><span class="sr-only">Номер</span></th>
					<th scope="col">Стоп-фактор</th>
					<th scope="col" class="w-thresholds">Пороги</th>
					<th scope="col" class="w-version version">Изменён</th>
					<th scope="col" class="w-status">Статус</th>
					<th scope="col" class="w-open"><span class="sr-only">Открыть</span></th>
				</tr>
			</thead>
			{#each sections as section (section.key)}
				<tbody>
					<tr class="section">
						<th scope="rowgroup" colspan="6">
							<span class="section-title">{section.title}</span>
							<span class="section-count">{section.factors.length}</span>
							<span class="section-description">{section.description}</span>
						</th>
					</tr>
					{#each section.factors as factor, index (factor.factorId)}
						{@const only = limitedTo(factor)}
						<!-- the whole row opens the stop factor; the link in it is what the keyboard reaches -->
						<tr class="row" class:inactive={!factor.active}>
							<td class="number">{index + 1}</td>
							<td>
								<div class="name">
									<!-- the link is built with resolve() in href() -->
									<!-- eslint-disable svelte/no-navigation-without-resolve -->
									<a href={href(factor)} class="title" title={factor.name}>{factor.name}</a>
									<!-- eslint-enable svelte/no-navigation-without-resolve -->
									<span class="summary" title={factor.summary}>{ruleText(factor)}</span>
									{#if only}<span class="only">Только: {only}</span>{/if}
								</div>
							</td>
							<td><ThresholdTags conditions={factor.conditions} /></td>
							<td class="version">
								<span class="version-number">v{factor.versionNumber}</span>
								<span class="version-date">{formatDay(factor.validFrom)}</span>
							</td>
							<td>
								<Badge tone={factor.active ? 'success' : 'neutral'}
									>{factor.active ? 'Активен' : 'Отключён'}</Badge
								>
							</td>
							<td class="open"><i class="ri-arrow-right-s-line" aria-hidden="true"></i></td>
						</tr>
					{/each}
				</tbody>
			{/each}
		</table>
	</div>
</div>

<style>
	.card {
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
	}

	.scroller {
		overflow-x: auto;
	}

	table {
		width: 100%;
		min-width: 46rem;
		border-collapse: separate;
		border-spacing: 0;
		table-layout: fixed;
	}

	.w-number {
		width: 3rem;
	}

	.w-thresholds {
		width: 15rem;
	}

	.w-version {
		width: 7.5rem;
	}

	.w-status {
		width: 7.5rem;
	}

	.w-open {
		width: 2.5rem;
	}

	thead th {
		position: sticky;
		top: 0;
		z-index: 1;
		padding: 10px var(--space-3);
		border-bottom: 1px solid var(--color-border);
		background: var(--color-surface-alt);
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		font-weight: 600;
		letter-spacing: 0.06em;
		text-align: left;
		text-transform: uppercase;
	}

	/* a section: which products its stop factors check */
	.section th {
		padding: var(--space-3) var(--space-3) var(--space-2);
		border-bottom: 1px solid var(--color-border);
		background: var(--color-surface);
		font-weight: 400;
		text-align: left;
	}

	tbody + tbody .section th {
		border-top: 6px solid var(--color-surface-alt);
	}

	.section-title {
		font-size: var(--text-lg);
		font-weight: 600;
	}

	.section-count {
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

	.section-description {
		display: block;
		margin-top: 2px;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.row td {
		padding: 12px var(--space-3);
		border-bottom: 1px solid var(--color-border);
		vertical-align: middle;
	}

	tbody:last-child .row:last-child td {
		border-bottom: none;
	}

	.row {
		position: relative;
		cursor: pointer;
		transition: background-color var(--speed-fast);
	}

	.row:hover {
		background: var(--color-surface-alt);
	}

	.row:focus-within {
		background: var(--color-brand-subtle);
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
		gap: 3px;
		min-width: 0;
	}

	/* the link covers the whole row, so a click anywhere on it opens the stop factor */
	.title {
		display: -webkit-box;
		overflow: hidden;
		color: var(--color-text);
		font-weight: 600;
		line-height: 1.35;
		text-decoration: none;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 3;
		line-clamp: 3;
	}

	.title::after {
		position: absolute;
		inset: 0;
		content: '';
	}

	.title:focus-visible {
		outline: none;
	}

	.row:has(.title:focus-visible) {
		outline: 2px solid var(--color-info);
		outline-offset: -2px;
	}

	.summary,
	.only {
		overflow: hidden;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.only {
		color: var(--color-brand);
	}

	.version {
		font-variant-numeric: tabular-nums;
	}

	.version-number {
		display: block;
		font-weight: 600;
	}

	.version-date {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.open {
		color: var(--color-text-disabled);
		font-size: 18px;
		text-align: center;
	}

	.row:hover .open {
		color: var(--color-text);
	}

	.inactive .title,
	.inactive .number {
		color: var(--color-text-disabled);
	}

	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		white-space: nowrap;
	}

	/* narrow: the name keeps its room; when it changed is on the stop factor's page */
	@media (max-width: 64rem) {
		/* collapsed, not removed: the section rows span every column */
		.w-version {
			width: 0;
		}

		.version {
			padding: 0 !important;
			overflow: hidden;
			visibility: hidden;
		}

		.w-thresholds {
			width: 11rem;
		}

		table {
			min-width: 34rem;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.row {
			transition: none;
		}
	}
</style>
