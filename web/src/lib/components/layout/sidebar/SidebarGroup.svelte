<script lang="ts">
	import SidebarLink from './SidebarLink.svelte';
	import type { SidebarGroup, SidebarItem } from './sections';

	type Props = {
		group: SidebarGroup;
		open: boolean;
		onToggle: () => void;
		isCurrent: (item: SidebarItem) => boolean;
		collapsed: boolean;
	};

	let { group, open, onToggle, isCurrent, collapsed }: Props = $props();

	const holdsCurrent = $derived(group.items.some((item) => isCurrent(item)));

	const shown = $derived(open || collapsed);
</script>

<section
	class="group"
	class:collapsed
	aria-labelledby={collapsed ? undefined : `group-${group.id}`}
>
	{#if collapsed}
		<hr aria-hidden="true" />
	{:else}
		<h2>
			<button
				type="button"
				id="group-{group.id}"
				class="heading"
				aria-expanded={open}
				aria-controls="pages-{group.id}"
				onclick={onToggle}
			>
				<span class="name">{group.label}</span>
				{#if !open && holdsCurrent}
					<span class="dot" aria-label="Здесь открытая страница"></span>
				{/if}
				<span class="chevron" class:closed={!open} aria-hidden="true">
					<i class="ri-arrow-down-s-line" style:font-size="14px" aria-hidden="true"></i>
				</span>
			</button>
		</h2>
	{/if}

	<div id="pages-{group.id}" class="pages" class:open={shown} inert={!shown}>
		<ul>
			{#each group.items as item (item.path)}
				<li>
					<SidebarLink {item} current={isCurrent(item)} {collapsed} />
				</li>
			{/each}
		</ul>
	</div>
</section>

<style>
	.group {
		display: flex;
		flex-direction: column;
	}

	h2 {
		margin: 0;
	}

	.heading {
		display: flex;
		align-items: center;
		gap: 6px;
		width: 100%;
		height: 26px;
		padding: 0 8px 0 12px;
		border: none;
		border-radius: var(--radius-control);
		background: transparent;
		color: var(--color-text-hint);
		font: inherit;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		text-align: left;
		cursor: pointer;
		transition: color var(--speed-fast);
	}

	.heading:hover {
		color: var(--color-text);
	}

	.heading:focus-visible {
		outline: 2px solid var(--color-info);
		outline-offset: -2px;
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.dot {
		flex: none;
		width: 6px;
		height: 6px;
		border-radius: var(--radius-pill);
		background: var(--nav-current);
	}

	.chevron {
		display: inline-flex;
		flex: none;
		opacity: 0;
		transition:
			opacity var(--speed-fast),
			transform var(--speed);
	}

	.heading:hover .chevron,
	.heading:focus-visible .chevron,
	.chevron.closed {
		opacity: 1;
	}

	.chevron.closed {
		transform: rotate(-90deg);
	}

	.pages {
		display: grid;
		grid-template-rows: 0fr;
		visibility: hidden;
		transition:
			grid-template-rows var(--speed) ease,
			visibility 0s linear var(--speed);
	}

	.pages.open {
		grid-template-rows: 1fr;
		visibility: visible;
		transition: grid-template-rows var(--speed) ease;
	}

	ul {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-height: 0;
		margin: 0;
		padding: 0;
		overflow: hidden;
		list-style: none;
	}

	hr {
		width: 20px;
		margin: 6px auto;
		border: none;
		border-top: 1px solid var(--color-border);
	}

	.group:first-child hr {
		display: none;
	}

	@media (prefers-reduced-motion: reduce) {
		.pages,
		.pages.open,
		.chevron {
			transition: none;
		}
	}
</style>
