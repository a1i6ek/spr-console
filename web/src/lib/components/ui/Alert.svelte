<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { ColorPalette } from './control';

	type Props = {
		tone?: Exclude<ColorPalette, 'neutral'>;
		children: Snippet;
	};

	let { tone = 'danger', children }: Props = $props();

	const icons = {
		danger: 'ri-error-warning-line',
		warning: 'ri-error-warning-line',
		info: 'ri-information-line',
		success: 'ri-checkbox-circle-line'
	};

	const toneIcon = $derived(icons[tone]);
</script>

<!-- An error is announced as soon as it appears; a notice is just read in its
     place. -->
<p class="alert {tone}" role={tone === 'danger' ? 'alert' : 'status'}>
	<i class={toneIcon} style:font-size="17px" aria-hidden="true"></i>
	<span>{@render children()}</span>
</p>

<style>
	.alert {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
		margin: 0;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-surface);
		font-size: var(--text-base);
		line-height: 1.5;
	}

	.alert :global(i) {
		flex-shrink: 0;
		margin-top: 2px;
	}

	.danger {
		background: var(--surface-danger);
		color: var(--color-danger);
	}

	.warning {
		background: var(--surface-warning);
		color: var(--color-text);
	}

	.warning :global(i) {
		color: var(--color-warning);
	}

	.info {
		background: var(--surface-info);
		color: var(--color-text);
	}

	.info :global(i) {
		color: var(--color-info);
	}

	.success {
		background: var(--surface-success);
		color: var(--color-text);
	}

	.success :global(i) {
		color: var(--color-success);
	}
</style>
