<script lang="ts">
	import type { Metric, Product, StopFactor } from '#lib/api/index.js';
	import { Badge, Tag } from '#lib/components/ui/index.js';
	import { changes } from '#lib/history.js';
	import { formatDate } from '#lib/labels.js';
	import ThresholdTags from './ThresholdTags.svelte';

	type Props = {
		/** The versions of one stop factor, newest first. */
		versions: StopFactor[];
		metrics: Metric[];
		products: Product[];
	};

	let { versions, metrics, products }: Props = $props();

	// each version with what changed from the one before it (the oldest one: its rule as created)
	const items = $derived(
		versions.map((version, i) => ({
			version,
			changes: versions[i + 1] ? changes(versions[i + 1], version, metrics, products) : null
		}))
	);
</script>

<ol>
	{#each items as { version, changes: diff } (version.versionId)}
		<li class:current={version.current}>
			<span class="number">v{version.versionNumber}</span>
			<div class="body">
				<div class="head">
					<span class="reason">{version.changeReason ?? '—'}</span>
					{#if version.current}<Badge tone="success">Действует</Badge>{/if}
					{#if version.endReason}<Badge tone="danger">Удалён</Badge>{/if}
				</div>

				<span class="period">
					{formatDate(version.validFrom)} — {version.validTo
						? formatDate(version.validTo)
						: 'сейчас'}
				</span>

				{#if diff === null}
					<span class="tags">
						<Tag small tone={version.active ? 'success' : 'neutral'}
							>{version.active ? 'Активен' : 'Отключён'}</Tag
						>
						<ThresholdTags conditions={version.conditions} />
					</span>
				{:else if diff.length}
					<ul class="changes">
						{#each diff as change (change.label)}
							<li>
								<span class="label">{change.label}:</span>
								<s>{change.from}</s>
								<i class="ri-arrow-right-line" aria-hidden="true"></i>
								<b>{change.to}</b>
							</li>
						{/each}
					</ul>
				{/if}

				{#if version.endReason}
					<span class="ended">Причина удаления: {version.endReason}</span>
				{/if}

				{#if version.checks}
					<span class="checks">
						{#if version.checks.total}
							Проверено заявок: <b>{version.checks.total}</b>
							{#if version.checks.rejected}· отказ: <b>{version.checks.rejected}</b>{/if}
							{#if version.checks.underwriter}· андеррайтер: <b>{version.checks.underwriter}</b
								>{/if}
						{:else}
							Заявок с этой версией не проверялось
						{/if}
					</span>
				{/if}
			</div>
		</li>
	{/each}
</ol>

<style>
	ol {
		display: flex;
		flex-direction: column;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	ol > li {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: start;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	ol > li:first-child {
		border-top: none;
	}

	.number {
		display: grid;
		place-items: center;
		width: 34px;
		height: 34px;
		border-radius: var(--radius-pill);
		background: var(--color-secondary);
		font-size: var(--text-sm);
		font-weight: 600;
		font-variant-numeric: tabular-nums;
	}

	.current .number {
		background: var(--color-primary);
		color: var(--color-primary-text);
	}

	.body {
		display: flex;
		flex-direction: column;
		gap: 6px;
		min-width: 0;
	}

	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.reason {
		font-weight: 600;
	}

	.period,
	.checks {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		font-variant-numeric: tabular-nums;
	}

	.checks b {
		color: var(--color-text);
		font-weight: 600;
	}

	.tags {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 4px;
	}

	.changes {
		display: flex;
		flex-direction: column;
		gap: 2px;
		margin: 0;
		padding: 0;
		list-style: none;
		font-size: var(--text-sm);
	}

	.changes li {
		overflow-wrap: anywhere;
	}

	.changes .label {
		color: var(--color-text-hint);
	}

	.changes s {
		color: var(--color-text-hint);
	}

	.changes i {
		margin: 0 4px;
		color: var(--color-text-hint);
		vertical-align: -1px;
	}

	.ended {
		color: var(--color-danger);
		font-size: var(--text-sm);
	}
</style>
