<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		children: Snippet;
		columns?: 2 | 3;
		spacing?: 'compact' | 'comfortable';
	};

	let { children, columns = 2, spacing = 'compact' }: Props = $props();
</script>

<div class="frame">
	<div class="field-grid {spacing}" style:--columns={columns}>
		{@render children()}
	</div>
</div>

<style>
	.frame {
		container-type: inline-size;
	}

	.field-grid {
		display: grid;
		grid-template-columns: repeat(var(--columns), minmax(0, 1fr));
		align-items: start;
		gap: var(--space-3);
	}

	.comfortable {
		column-gap: var(--space-4);
	}

	.field-grid > :global(.full) {
		grid-column: 1 / -1;
	}

	@container (max-width: 30rem) {
		.field-grid {
			grid-template-columns: minmax(0, 1fr);
		}
	}
</style>
