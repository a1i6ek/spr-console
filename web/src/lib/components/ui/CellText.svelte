<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		title: string;
		detail?: string;
		mono?: boolean;
		lang?: string;
		children?: Snippet;
	};

	let { title, detail, mono = false, lang, children }: Props = $props();
</script>

<span class="cell-text">
	<span class="line">
		<span class="title" class:mono {lang} {title}>{title}</span>
		{@render children?.()}
	</span>
	{#if detail}
		<span class="detail" title={detail}>{detail}</span>
	{/if}
</span>

<style>
	.cell-text {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
		max-width: 28rem;
	}

	.line {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.title {
		overflow: hidden;
		color: var(--color-text);
		font-weight: 600;
		text-overflow: ellipsis;
	}

	.mono {
		font-family: var(--font-mono);
		font-size: var(--text-sm);
	}

	/* A description takes the width the column already has, and is cut there,
	   rather than widening the column and pushing the others off the page. */
	.detail {
		width: 0;
		min-width: 100%;
		overflow: hidden;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		text-overflow: ellipsis;
	}
</style>
