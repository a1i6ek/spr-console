<script lang="ts">
	import type { Snippet } from 'svelte';
	import { BRAND } from '#lib/brand.js';
	import { Card, Thumb } from '../ui/index.js';

	type Props = {
		title: string;
		description?: string;
		/** `sm` for a sign-in form, `md` for one with more to read. */
		width?: 'sm' | 'md';
		children: Snippet;
	};

	let { title, description, width = 'sm', children }: Props = $props();
</script>

<!-- A page outside the panel: signing in, or setting up what signing in needs. -->
<main>
	<div class={['column', width]}>
		<Card padded>
			<div class="content">
				<header>
					<span class="mark" role="img" aria-label="{BRAND.name} / {BRAND.product}">
						<Thumb src={BRAND.logo} text="B" size="md" bare />
					</span>
					<h1>{title}</h1>
					{#if description}<p>{description}</p>{/if}
				</header>

				{@render children()}
			</div>
		</Card>
	</div>
</main>

<style>
	main {
		display: grid;
		place-items: center;
		min-height: 100dvh;
		padding: var(--space-5);
	}

	.column {
		width: 100%;
	}

	.sm {
		max-width: 22rem;
	}

	.md {
		max-width: 28rem;
	}

	.content {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		padding: var(--space-2);
	}

	/* What the page holds — a form, a step — lays itself out the same way. */
	.content > :global(form) {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	header {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.mark {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		margin-bottom: var(--space-2);
	}

	h1 {
		font-size: var(--text-lg);
	}

	header p {
		color: var(--color-text-hint);
		line-height: 1.5;
	}
</style>
