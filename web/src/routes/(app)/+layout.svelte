<script lang="ts">
	import { untrack, type Snippet } from 'svelte';
	import AppHeader from '#lib/components/layout/AppHeader.svelte';
	import Sidebar from '#lib/components/layout/Sidebar.svelte';
	import { provideShell } from '#lib/state/shell.svelte.js';
	import type { LayoutData } from './$types';

	let { data, children }: { data: LayoutData; children: Snippet } = $props();

	const shell = provideShell(
		untrack(() => data.sidebar),
		untrack(() => data.closedGroups)
	);
</script>

<div class="shell" class:mini={shell.collapsed}>
	<AppHeader username={data.username} />

	<main>
		<div class="dashboard">
			<Sidebar />

			<div class="content">
				{@render children()}
			</div>
		</div>
	</main>
</div>

<style>
	@property --sidebar-width {
		syntax: '<length>';
		inherits: true;
		initial-value: 240px;
	}

	.shell {
		--sidebar-width: 240px;

		height: 100dvh;
		overflow: hidden;
		transition: --sidebar-width var(--speed-slow) cubic-bezier(0.4, 0, 0.2, 1);
	}

	.shell.mini {
		--sidebar-width: 56px;
	}

	@media (prefers-reduced-motion: reduce) {
		.shell {
			transition: none;
		}
	}

	main {
		position: relative;
		height: 100%;
		overflow-y: auto;
		overscroll-behavior-y: auto;
		padding: var(--header-height) 0 0;
		scrollbar-gutter: stable;
	}

	.dashboard {
		display: grid;
		grid-template-columns: var(--sidebar-width) 1fr;
		align-items: start;
	}

	.content {
		grid-column: 2;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
		width: 100%;
		min-width: 0;
		max-width: calc(var(--content-max-width) + var(--page-gutter) * 2);
		margin-inline: auto;
		padding: var(--space-5) var(--page-gutter) var(--space-6);
	}

	@media (max-width: 55rem) {
		.dashboard {
			grid-template-columns: 1fr;
		}

		.content {
			grid-column: auto;
		}
	}
</style>
