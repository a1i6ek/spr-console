<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		bordered?: boolean;
		label?: string;
		children: Snippet;
	};

	let { bordered = false, label, children }: Props = $props();
</script>

<ul class="list" class:bordered aria-label={label}>
	{@render children()}
</ul>

<style>
	.list {
		--list-inset: var(--space-4);

		display: block;
		width: 100%;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.bordered {
		--list-inset: 13px;

		overflow: hidden;
		border: 1px solid var(--color-secondary-alt);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
	}

	@media (max-width: 34rem) {
		.list:not(.bordered) {
			--list-inset: var(--space-3);
		}
	}
</style>
