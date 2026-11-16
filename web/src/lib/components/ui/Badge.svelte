<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		tone?: 'neutral' | 'success' | 'warning' | 'danger';
		children: Snippet;
	};

	let { tone = 'neutral', children }: Props = $props();
</script>

<span class="badge {tone}">
	{@render children()}
</span>

<style>
	.badge {
		display: inline-flex;
		align-items: center;
		justify-self: start;
		height: 25px;
		padding: 0 7px;
		border-radius: var(--radius-control);
		font-size: var(--text-sm);
		white-space: nowrap;
	}

	.neutral {
		background: var(--color-secondary);
		color: var(--color-text-hint);
	}

	.success {
		background: var(--surface-success);
		color: var(--color-success);
	}

	.warning {
		background: var(--surface-warning);
		color: var(--color-warning);
	}

	.danger {
		background: var(--surface-danger);
		color: var(--color-danger);
	}
</style>
