<script lang="ts">
	import { page } from '$app/state';
	import { MediaQuery } from 'svelte/reactivity';
	import { useShell } from '#lib/state/shell.svelte.js';
	import SidebarGroup from './sidebar/SidebarGroup.svelte';
	import { isCurrentItem, sidebarGroups, type SidebarItem } from './sidebar/sections';

	const shell = useShell();

	/**
	 * Below this width the sidebar becomes a slide-in panel; it is a script decision because it
	 * changes which component is drawn.
	 */
	const narrow = new MediaQuery('max-width: 55rem');

	const folded = $derived(shell.collapsed && !narrow.current);

	const groups = sidebarGroups();

	const isCurrent = (item: SidebarItem) => isCurrentItem(item, page.url.pathname);

	$effect(() => {
		void page.url.pathname;
		shell.setMenu(false);
	});

	$effect(() => {
		if (!narrow.current) shell.setMenu(false);
	});

	$effect(() => {
		if (!narrow.current || !shell.menuOpen) return;

		const root = document.documentElement;
		const before = root.style.overflow;
		root.style.overflow = 'hidden';

		return () => {
			root.style.overflow = before;
		};
	});

	// Escape closes it — listened for on the way down rather than on the way
	// up, because the control that opens the panel carries a tooltip, and a
	// tooltip takes Escape for itself before it reaches the window.
	function dismiss(event: KeyboardEvent) {
		if (event.key === 'Escape' && shell.menuOpen) shell.setMenu(false);
	}
</script>

<svelte:window onkeydowncapture={dismiss} />

{#if narrow.current && shell.menuOpen}
	<!-- svelte-ignore a11y_click_events_have_key_events -->
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="backdrop" onclick={() => shell.setMenu(false)}></div>
{/if}

<aside class:collapsed={folded} class:open={shell.menuOpen}>
	<nav aria-label="Разделы">
		{#each groups as group (group.id)}
			<SidebarGroup
				{group}
				{isCurrent}
				collapsed={folded}
				open={shell.isOpen(group.id)}
				onToggle={() => shell.toggleGroup(group.id)}
			/>
		{/each}
	</nav>
</aside>

<style>
	aside {
		position: fixed;
		top: var(--header-height);
		bottom: 0;
		left: 0;
		z-index: 9;
		display: flex;
		flex-direction: column;
		width: var(--sidebar-width);
		border-right: 1px solid var(--color-border);
		background: var(--nav-surface);
		overflow: hidden;
	}

	nav {
		display: flex;
		flex: 1;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-2) var(--space-3);
		overflow-x: hidden;
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: thin;
		scrollbar-color: transparent transparent;
	}

	nav:hover {
		scrollbar-color: var(--scrollbar-thumb) transparent;
	}

	.collapsed nav {
		gap: 0;
		padding-inline: 8px;
	}

	@media (max-width: 55rem) {
		aside {
			width: min(17rem, 82vw);
			box-shadow: var(--shadow-md);
			transform: translateX(-100%);
			visibility: hidden;
			transition:
				transform var(--speed-slow) cubic-bezier(0.4, 0, 0.2, 1),
				visibility var(--speed-slow);
		}

		aside.open {
			transform: translateX(0);
			visibility: visible;
		}

		.collapsed nav {
			gap: var(--space-2);
			padding-inline: var(--space-2);
		}
	}

	.backdrop {
		position: fixed;
		top: var(--header-height);
		right: 0;
		bottom: 0;
		left: 0;
		z-index: 8;
		background: var(--color-overlay);
		animation: fade var(--speed) ease-out;
	}

	@keyframes fade {
		from {
			opacity: 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		aside {
			transition: none;
		}

		.backdrop {
			animation: none;
		}
	}
</style>
