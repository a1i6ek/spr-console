<script lang="ts">
	import type { Snippet } from 'svelte';
	import Button from './Button.svelte';
	import Note from './Note.svelte';

	type Props = {
		title: string;
		description: string;
		onclick?: () => void;
		label?: string;
		children?: Snippet;
	};

	let { title, description, onclick, label = 'Удалить', children }: Props = $props();
</script>

<section class="danger-zone">
	<div class="text">
		<strong>{title}</strong>
		<Note>{description}</Note>
	</div>

	<div class="action">
		{#if children}
			{@render children()}
		{:else}
			<Button colorPalette="danger" variant="subtle" size="sm" {onclick}>
				<i class="ri-delete-bin-line" aria-hidden="true"></i>
				{label}
			</Button>
		{/if}
	</div>
</section>

<style>
	.danger-zone {
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: var(--space-3);
		margin-top: var(--space-6);
		padding: var(--space-3) var(--space-4);
		border: 1px solid color-mix(in srgb, var(--color-danger) 40%, transparent);
		border-radius: var(--radius-surface);
	}

	.text {
		display: flex;
		flex: 1;
		flex-direction: column;
		gap: 2px;
		min-width: 12rem;
	}

	.action {
		display: flex;
		flex-shrink: 0;
		gap: var(--space-2);
	}
</style>
