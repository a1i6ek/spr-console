<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		/** Set apart on a quiet fill: a remark beside fields rather than under them. */
		boxed?: boolean;
		children: Snippet;
	};

	let { boxed = false, children }: Props = $props();
</script>

<p class={['note', { boxed }]}>{@render children()}</p>

<style>
	.note {
		margin: 0;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		line-height: 1.5;
	}

	.boxed {
		padding: var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-secondary-alt);
	}

	.note :global(a),
	.note :global(strong) {
		color: var(--color-text);
	}

	/* An action that belongs to the sentence, such as undoing what it says. */
	.note :global(button) {
		margin-left: var(--space-1);
		padding: 0;
		border: none;
		background: none;
		color: var(--color-text);
		font: inherit;
		text-decoration: underline;
		text-underline-offset: 3px;
		cursor: pointer;
	}
</style>
