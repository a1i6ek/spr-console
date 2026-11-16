<script lang="ts">
	import type { Snippet } from 'svelte';

	type Crumb = string | { label: string; href: string };

	type Props = {
		crumbs: Crumb[];
		description?: string;
		/** `lg` for a section's page, `md` for one item's page, whose title is its (often long) name. */
		size?: 'lg' | 'md';
		count?: number;
		meta?: Snippet;
		secondary?: Snippet;
		actions?: Snippet;
	};

	let { crumbs, description, size = 'lg', count, meta, secondary, actions }: Props = $props();

	const label = (crumb: Crumb) => (typeof crumb === 'string' ? crumb : crumb.label);

	const trail = $derived(crumbs.slice(0, -1));
</script>

<header class="page-header">
	{#if trail.length > 0}
		<nav class="trail" aria-label="Навигация">
			{#each trail as crumb, index (index)}
				{#if index > 0}<span class="separator" aria-hidden="true">/</span>{/if}
				{#if typeof crumb === 'string'}
					<span class="crumb">{crumb}</span>
				{:else}
					<!-- The page resolved the address: it knows the route. -->
					<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -->
					<a class="crumb link" href={crumb.href}>{crumb.label}</a>
				{/if}
			{/each}
		</nav>
	{/if}

	<div class="title-row">
		<div class="title">
			<!-- The title wraps rather than being cut: on an item's page it is its whole name. -->
			<h1 class={size}>
				{label(crumbs.at(-1) ?? '')}
				{#if count !== undefined}
					<span class="count" title="{count} in total">{count.toLocaleString()}</span>
				{/if}
			</h1>
			{#if meta}<div class="meta">{@render meta()}</div>{/if}
		</div>

		{#if secondary || actions}
			<div class="tools">
				{#if secondary}<div class="secondary">{@render secondary()}</div>{/if}
				{#if actions}<div class="actions">{@render actions()}</div>{/if}
			</div>
		{/if}
	</div>

	{#if description}
		<p class="description">{description}</p>
	{/if}
</header>

<style>
	.page-header {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		min-width: 0;
	}

	.trail {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 6px;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.separator {
		color: var(--color-text-disabled);
	}

	.link {
		color: inherit;
		text-decoration: none;
		transition: color var(--speed-fast);
	}

	.link:hover {
		color: var(--color-text);
	}

	/* the title takes the room the tools leave; the tools stay at the top right whatever its length */
	.title-row {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		align-items: start;
		gap: var(--space-2) var(--space-4);
		min-height: var(--control-height);
	}

	.title {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		min-width: 0;
		padding-top: 4px;
	}

	/* never cut: long names wrap onto as many lines as they need, evenly, breaking long words if they must */
	h1 {
		max-width: 60ch;
		margin: 0;
		color: var(--color-text);
		font-weight: 600;
		letter-spacing: -0.01em;
		overflow-wrap: anywhere;
		text-wrap: balance;
	}

	h1.lg {
		font-size: clamp(var(--text-xl), 1.1rem + 0.5vw, var(--text-2xl));
		line-height: 1.25;
	}

	h1.md {
		font-size: var(--text-xl);
		line-height: 1.35;
	}

	.count {
		display: inline-block;
		margin-left: var(--space-1);
		padding: 2px 8px;
		border-radius: var(--radius-pill);
		background: var(--color-secondary);
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		font-variant-numeric: tabular-nums;
		font-weight: 600;
		line-height: 1.5;
		vertical-align: 0.2em;
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1);
	}

	.tools,
	.secondary,
	.actions {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.secondary + .actions {
		padding-left: var(--space-2);
		border-left: 1px solid var(--color-border);
	}

	.secondary :global(.total) {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		white-space: nowrap;
	}

	.description {
		max-width: 75ch;
		margin: 0;
		color: var(--color-text-hint);
		font-size: var(--text-base);
		line-height: 1.55;
	}

	@media (max-width: 34rem) {
		h1.md {
			font-size: var(--text-lg);
		}

		.title-row {
			display: contents;
		}

		.title {
			min-height: var(--control-height-sm);
		}

		.description {
			order: 1;
		}

		.tools {
			order: 2;
			width: 100%;
			margin-top: var(--space-2);
		}

		.actions {
			margin-left: auto;
		}
	}
</style>
