<script lang="ts">
	import { Dialog } from '@ark-ui/svelte/dialog';
	import { Portal } from '@ark-ui/svelte/portal';
	import type { Metric } from '#lib/api/index.js';
	import { IconButton } from '#lib/components/ui/index.js';
	import MetricRows from './MetricRows.svelte';

	let {
		open = $bindable(false),
		metrics
	}: {
		open: boolean;
		metrics: Metric[];
	} = $props();
</script>

<Dialog.Root bind:open lazyMount unmountOnExit>
	<Portal>
		<Dialog.Backdrop />
		<Dialog.Positioner>
			<Dialog.Content class="metrics-content">
				<header>
					<div class="heading">
						<Dialog.Title>Метрики</Dialog.Title>
						<span class="count">{metrics.length}</span>
					</div>
					<IconButton
						icon="ri-close-line"
						label="Закрыть"
						size="sm"
						onclick={() => (open = false)}
					/>
				</header>

				<Dialog.Description>
					Что измеряют стоп-факторы и какие параметры они задают.
				</Dialog.Description>

				<div class="body">
					<MetricRows {metrics} />
				</div>
			</Dialog.Content>
		</Dialog.Positioner>
	</Portal>
</Dialog.Root>

<style>
	:global(.metrics-content[data-scope='dialog'][data-part='content']) {
		--dialog-width: 46rem;

		padding: 0;
	}

	header {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-3) var(--space-3) var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	.heading {
		display: flex;
		flex: 1;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.heading :global([data-scope='dialog'][data-part='title']) {
		font-size: var(--text-lg);
	}

	.count {
		display: inline-grid;
		place-items: center;
		min-width: 22px;
		height: 20px;
		padding: 0 6px;
		border-radius: var(--radius-pill);
		background: var(--color-secondary);
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		font-weight: 600;
	}

	:global(.metrics-content [data-scope='dialog'][data-part='description']) {
		padding: var(--space-2) var(--space-4) 0;
	}

	.body {
		flex: 1;
		min-height: 0;
		margin-top: var(--space-2);
		padding-bottom: var(--space-2);
		overflow-y: auto;
	}
</style>
