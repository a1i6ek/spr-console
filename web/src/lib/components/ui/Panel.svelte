<script lang="ts">
	import type { IconName } from './icon';
	import type { Snippet } from 'svelte';

	type Props = {
		title: string;
		icon?: IconName;
		meta?: Snippet;
		flush?: boolean;
		children: Snippet;
	};

	let { title, icon, meta, flush = false, children }: Props = $props();
</script>

<section class="panel">
	<header>
		{#if icon}<span class="icon"
				><i class={icon} style:font-size="18px" aria-hidden="true"></i></span
			>{/if}
		<h2>{title}</h2>
		{#if meta}<div class="meta">{@render meta()}</div>{/if}
	</header>

	<div class="content" class:flush>
		{@render children()}
	</div>
</section>

<style>
	.panel {
		display: flex;
		flex-direction: column;
		min-width: 0;
		border: 1px solid var(--color-secondary-alt);
		border-radius: var(--radius-surface);
		background: var(--color-surface-alt);
		box-shadow: var(--shadow-panel);
	}

	header {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: var(--control-height-lg);
		padding: 10px var(--space-4);
		border-radius: var(--radius-surface) var(--radius-surface) 0 0;
		background: var(--color-surface-alt);
	}

	.icon {
		display: inline-flex;
		color: var(--color-text-hint);
	}

	h2 {
		flex: 1;
		min-width: 0;
		margin: 0;
		overflow: hidden;
		font-size: var(--text-base);
		font-weight: normal;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta {
		display: inline-flex;
		flex-shrink: 0;
		align-items: center;
		gap: var(--space-1);
	}

	.content {
		flex: 1;
		padding: var(--space-4);
		border-top: 1px solid var(--color-secondary-alt);
		border-radius: 0 0 var(--radius-surface) var(--radius-surface);
		background: var(--color-surface);
	}

	.content.flush {
		padding: 0;
	}

	@media (max-width: 34rem) {
		header {
			padding: 10px var(--space-3);
		}

		.content:not(.flush) {
			padding: var(--space-3);
		}
	}
</style>
