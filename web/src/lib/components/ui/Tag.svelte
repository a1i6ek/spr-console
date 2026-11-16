<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { ColorPalette } from './control';

	type Props = {
		tone?: ColorPalette;
		dot?: boolean;
		small?: boolean;
		strong?: boolean;
		title?: string;
		children: Snippet;
	};

	let {
		tone = 'neutral',
		dot = false,
		small = false,
		strong = false,
		title,
		children
	}: Props = $props();
</script>

<span class="label {tone}" class:dot class:small class:strong {title}>
	{@render children()}
</span>

<style>
	.label {
		display: inline-flex;
		flex-shrink: 0;
		align-items: center;
		justify-content: center;
		gap: 5px;
		max-width: 100%;
		min-height: 25px;
		padding: 5px 7px;
		border-radius: var(--radius-control);
		background: var(--color-secondary-alt);
		color: var(--color-text);
		font-size: var(--text-sm);
		font-weight: normal;
		line-height: 15px;
		white-space: nowrap;
	}

	.small {
		min-height: 20px;
		padding: 3px 5px;
		font-size: 11px;
	}

	.strong {
		font-size: 0.8em;
		font-weight: bold;
	}

	.label:not(.dot).success {
		background: var(--surface-success);
	}

	.label:not(.dot).danger {
		background: var(--surface-danger);
	}

	.label:not(.dot).warning {
		background: var(--surface-warning);
	}

	.label:not(.dot).info {
		background: var(--surface-info);
	}

	.dot::before {
		flex-shrink: 0;
		width: 6px;
		height: 6px;
		border-radius: 6px;
		background: var(--color-text-disabled);
		content: '';
	}

	.dot.success::before {
		background: var(--color-success);
	}

	.dot.danger::before {
		background: var(--color-danger);
	}

	.dot.warning::before {
		background: var(--color-warning);
	}

	.dot.info::before {
		background: var(--color-info);
	}
</style>
