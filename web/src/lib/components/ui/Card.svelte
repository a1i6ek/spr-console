<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		padded?: boolean;
		children: Snippet;
	};

	let { padded = false, children }: Props = $props();
</script>

<div class="card" class:padded>
	{@render children()}
</div>

<style>
	.card {
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
		box-shadow: var(--shadow-sm);
	}

	.padded {
		padding: var(--space-4);
	}
</style>
