<script lang="ts">
	import type { Snippet } from 'svelte';
	import { enhance, type SubmitFunction } from '$app/forms';
	import { Dialog } from '@ark-ui/svelte/dialog';
	import { Portal } from '@ark-ui/svelte/portal';
	import Code from './Code.svelte';
	import IconButton from './IconButton.svelte';

	type Props = {
		open: boolean;
		title: string;
		description?: string;
		meta?: string;
		/** The form action the sheet posts to (`/actions/<subject>?/<name>`); none is no form. */
		action?: string;
		/** What `use:enhance` does with it: `submit()` from `#lib/forms.js`. */
		onsubmit?: SubmitFunction;
		children: Snippet;
		actions?: Snippet;
		status?: Snippet;
	};

	let {
		open = $bindable(false),
		title,
		description,
		meta,
		action,
		onsubmit,
		children,
		actions,
		status
	}: Props = $props();

	let sheet = $state<HTMLElement | null>(null);
</script>

{#snippet sheetContent()}
	<header class="bar">
		<IconButton
			icon="ri-close-line"
			label="Закрыть"
			size="sm"
			placement="bottom"
			onclick={() => (open = false)}
		/>

		<span class="rule" aria-hidden="true"></span>

		<div class="heading">
			<Dialog.Title>{title}</Dialog.Title>
			{#if meta}<Code tone="quiet" truncate title={meta}>{meta}</Code>{/if}
		</div>
	</header>

	<div class="body">
		<div class="column">
			{#if description}
				<Dialog.Description>{description}</Dialog.Description>
			{/if}

			{@render children()}
		</div>
	</div>

	{#if actions || status}
		<div class="actions">
			{#if status}<span class="status">{@render status()}</span>{/if}
			{@render actions?.()}
		</div>
	{/if}
{/snippet}

<!--
	lazyMount with unmountOnExit rebuilds the sheet on every opening, so it always starts fresh.
	Ark only honours unmountOnExit with a leaving animation to wait for (see styles/ark.css).
-->
<Dialog.Root bind:open lazyMount unmountOnExit initialFocusEl={() => sheet}>
	<Portal>
		<Dialog.Backdrop />
		<Dialog.Positioner class="fullscreen">
			<Dialog.Content class="fullscreen">
				{#if action}
					<form
						class="sheet"
						method="POST"
						{action}
						use:enhance={onsubmit}
						bind:this={sheet}
						tabindex="-1"
					>
						{@render sheetContent()}
					</form>
				{:else}
					<div class="sheet" bind:this={sheet} tabindex="-1">
						{@render sheetContent()}
					</div>
				{/if}
			</Dialog.Content>
		</Dialog.Positioner>
	</Portal>
</Dialog.Root>

<style>
	.sheet {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		grid-template-rows: auto minmax(0, 1fr);
		min-height: 0;
		height: 100%;
	}

	.sheet:focus {
		outline: none;
	}

	.bar,
	.actions {
		grid-row: 1;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-height: var(--header-height);
		border-bottom: 1px solid var(--color-border);
	}

	.bar {
		grid-column: 1 / -1;
		min-width: 0;
		padding: var(--space-1) var(--space-3);
	}

	.sheet:has(> .actions) .bar {
		grid-column: 1;
	}

	.actions {
		grid-column: 2;
		padding: var(--space-1) var(--space-3) var(--space-1) 0;
	}

	.rule {
		flex-shrink: 0;
		width: 1px;
		height: 20px;
		background: var(--color-border);
	}

	.heading {
		display: flex;
		flex: 1;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.heading :global([data-scope='dialog'][data-part='title']) {
		flex-shrink: 0;
		max-width: 100%;
		overflow: hidden;
		font-size: var(--text-lg);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.heading > :global(code) {
		min-width: 0;
	}

	.status {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.body {
		grid-row: 2;
		grid-column: 1 / -1;
		overflow-y: auto;
		scrollbar-gutter: stable;
		overscroll-behavior: contain;
	}

	.column {
		width: min(var(--dialog-column-width), 100%);
		margin: 0 auto;
		padding: var(--space-5) var(--space-4) var(--space-6);
	}

	.column > :global([data-scope='dialog'][data-part='description']) {
		max-width: 64ch;
		margin: 0 0 var(--space-4);
		font-size: var(--text-base);
		line-height: 1.5;
	}

	@media (max-width: 40rem) {
		.sheet {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: auto minmax(0, 1fr) auto;
		}

		.actions {
			grid-row: 3;
			grid-column: 1;
			flex-wrap: wrap;
			min-height: 0;
			padding: var(--space-2) var(--space-3);
			border-top: 1px solid var(--color-border);
			border-bottom: none;
			background: var(--color-surface-alt);
		}

		.actions > :global(.control) {
			flex: 1;
		}

		.status {
			flex-basis: 100%;
			text-align: center;
		}

		.column {
			padding: var(--space-4) var(--space-3) var(--space-5);
		}
	}
</style>
