<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		lead?: Snippet;
		title?: string;
		description?: string;
		end?: Snippet;
		children?: Snippet;
		href?: string;
	};

	let { lead, title, description, end, children, href }: Props = $props();
</script>

{#snippet row()}
	{#if lead}<span class="lead">{@render lead()}</span>{/if}

	<div class="content">
		{#if title}<span class="title">{title}</span>{/if}
		{#if description}<span class="description">{description}</span>{/if}
		{@render children?.()}
	</div>

	{#if end}<span class="end">{@render end()}</span>{/if}
{/snippet}

{#if href}
	<li class="linked">
		<!-- The caller resolved the address. -->
		<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -->
		<a class="item" {href}>{@render row()}</a>
	</li>
{:else}
	<li class="item">{@render row()}</li>
{/if}

<style>
	.item {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 54px;
		padding: 10px var(--list-inset, var(--space-4));
		overflow-wrap: anywhere;
	}

	.item:not(:first-child),
	.linked:not(:first-child) {
		box-shadow: 0 -1px 0 0 var(--color-secondary);
	}

	a.item {
		color: inherit;
		text-decoration: none;
		transition: background-color var(--speed-fast);
	}

	a.item:hover {
		background: var(--row-hover);
	}

	a.item:focus-visible {
		outline: 2px solid var(--color-info);
		outline-offset: -2px;
	}

	.lead,
	.end {
		display: inline-flex;
		flex-shrink: 0;
		align-items: center;
		gap: 5px;
	}

	.content {
		display: flex;
		flex: 1;
		flex-direction: column;
		min-width: 0;
		line-height: 1.5;
	}

	.title {
		font-size: var(--text-base);
	}

	.description {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	@media (max-width: 34rem) {
		.item {
			flex-wrap: wrap;
		}

		.end {
			margin-left: auto;
		}
	}
</style>
