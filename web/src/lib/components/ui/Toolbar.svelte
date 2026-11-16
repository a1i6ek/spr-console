<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		children: Snippet;
		end?: Snippet;
	};

	let { children, end }: Props = $props();
</script>

<div class="toolbar">
	<div class="start">{@render children()}</div>
	{#if end}<div class="end">{@render end()}</div>{/if}
</div>

<style>
	.toolbar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.start {
		display: flex;
		flex: 1 1 auto;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.start > :global(.search) {
		flex: 1 1 18rem;
		max-width: 28rem;
	}

	.end {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		margin-left: auto;
	}

	@media (max-width: 40rem) {
		.start > :global(.search) {
			max-width: none;
		}
	}
</style>
