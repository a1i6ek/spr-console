<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { IconName } from './icon';

	type Props = {
		icon: IconName;
		title: string;
		/** `md` inside a panel or dialog; `lg` when it is all a page has to show. */
		size?: 'md' | 'lg';
		/** What would fill the space, and how to get there. */
		children?: Snippet;
		actions?: Snippet;
	};

	let { icon, title, size = 'md', children, actions }: Props = $props();
</script>

<section class={['empty', size]}>
	<span class="mark" aria-hidden="true"><i class={icon} aria-hidden="true"></i></span>
	<div>
		<strong>{title}</strong>
		{#if children}<p>{@render children()}</p>{/if}
		{#if actions}<div class="actions">{@render actions()}</div>{/if}
	</div>
</section>

<style>
	.empty {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4);
		border: 1px dashed var(--color-border);
		border-radius: var(--radius-surface);
		color: var(--color-text-hint);
	}

	strong {
		display: block;
		color: var(--color-text);
	}

	p {
		margin: 2px 0 0;
		font-size: var(--text-sm);
	}

	p :global(a) {
		color: var(--color-text);
	}

	.mark {
		display: grid;
		flex-shrink: 0;
		place-items: center;
		font-size: 20px;
	}

	.actions {
		display: flex;
		gap: var(--space-2);
		margin-top: var(--space-3);
	}

	.lg {
		align-items: flex-start;
		gap: var(--space-4);
		padding: var(--space-5) var(--space-4);
	}

	.lg strong {
		font-size: var(--text-lg);
	}

	.lg p {
		max-width: 40rem;
		margin-top: var(--space-1);
		font-size: var(--text-base);
		line-height: 1.55;
	}

	.lg .mark {
		width: 56px;
		height: 56px;
		border-radius: 16px;
		background: var(--color-primary);
		color: var(--color-primary-text);
		font-size: 26px;
	}
</style>
