<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		children: Snippet;
		tone?: 'default' | 'quiet';
		truncate?: boolean;
		title?: string;
	};

	let { children, tone = 'default', truncate = false, title }: Props = $props();
</script>

<code class={tone} class:truncate {title}>{@render children()}</code>

<style>
	code {
		display: inline-flex;
		align-items: center;
		max-width: 100%;
		min-height: 24px;
		padding: 0 7px;
		border-radius: var(--radius-sm);
		background: var(--color-secondary-alt);
		color: var(--color-text);
		font-family: var(--font-mono);
		font-size: var(--text-sm);
		vertical-align: middle;
		white-space: nowrap;
	}

	.quiet {
		color: var(--color-text-hint);
	}

	.truncate {
		display: inline-block;
		overflow: hidden;
		line-height: 24px;
		text-overflow: ellipsis;
	}
</style>
