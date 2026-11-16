<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		size?: 'sm' | 'md' | 'lg';
		children: Snippet;
	};

	let { size = 'md', children }: Props = $props();
</script>

<div class="container {size}">
	{@render children()}
</div>

<style>
	.container {
		--container-width: var(--form-max-width);

		display: flex;
		flex-direction: column;
		gap: var(--space-4);
		width: 100%;
		max-width: var(--container-width);
		min-width: 0;
	}

	.sm {
		--container-width: 430px;
	}

	.lg {
		--container-width: 1080px;
	}
</style>
