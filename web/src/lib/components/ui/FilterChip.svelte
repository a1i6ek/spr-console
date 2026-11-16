<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		title: string;
		onclear: () => void;
		children: Snippet;
	};

	let { title, onclear, children }: Props = $props();
</script>

<button type="button" class="chip" {title} onclick={onclear}>
	<span>{@render children()}</span>
	<i class="ri-close-line" aria-hidden="true"></i>
</button>

<style>
	.chip {
		display: inline-flex;
		flex: none;
		align-items: center;
		gap: var(--space-1);
		height: var(--control-height);
		padding: 0 var(--space-3);
		border: none;
		border-radius: var(--radius-control);
		background: var(--surface-info);
		color: var(--color-text);
		font: inherit;
		font-size: var(--text-sm);
		white-space: nowrap;
		cursor: pointer;
		transition: filter var(--speed-fast);
	}

	.chip:hover {
		filter: brightness(0.96);
	}

	.chip :global(strong) {
		font-family: var(--font-mono);
		font-size: var(--text-sm);
	}
</style>
