<script lang="ts">
	import { Tooltip } from '#lib/components/ui/index.js';
	import { currentIcon, type SidebarItem } from './sections';

	type Props = {
		item: SidebarItem;
		current: boolean;
		collapsed: boolean;
	};

	let { item, current, collapsed }: Props = $props();

	const label = $derived(item.label);
	const icon = $derived(item.icon);

	const glyph = $derived(current ? currentIcon(icon) : icon);
</script>

<Tooltip {label} placement="right" disabled={!collapsed}>
	{#snippet children(trigger)}
		<!-- item.href is built with resolve() in sections.ts -->
		<!-- eslint-disable svelte/no-navigation-without-resolve -->
		<a
			{...trigger()}
			href={item.href}
			class="row"
			class:current
			class:collapsed
			aria-current={current ? 'page' : undefined}
		>
			<span class="icon"><i class={glyph} style:font-size="18px" aria-hidden="true"></i></span>
			<span class="label">{label}</span>
		</a>
		<!-- eslint-enable svelte/no-navigation-without-resolve -->
	{/snippet}
</Tooltip>

<style>
	.row {
		position: relative;
		display: flex;
		align-items: center;
		gap: 10px;
		height: var(--nav-item-height);
		padding: 0 10px;
		overflow: hidden;
		border-radius: var(--radius-control);
		color: var(--nav-text);
		font-size: var(--text-base);
		font-weight: 500;
		white-space: nowrap;
		text-decoration: none;
		transition:
			background-color var(--speed-fast),
			color var(--speed-fast);
	}

	.icon {
		display: inline-flex;
		flex: none;
		align-items: center;
		justify-content: center;
		width: 20px;
		height: 20px;
		color: var(--nav-icon);
		transition: color var(--speed-fast);
	}

	.label {
		flex: 1 1 0;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		transition: opacity var(--speed);
	}

	.row:hover {
		background: var(--nav-hover);
	}

	.row:hover .icon {
		color: var(--nav-text);
	}

	.row:focus-visible {
		outline: 2px solid var(--color-info);
		outline-offset: -2px;
	}

	/* The page you are on is told by a solid background and a filled icon, not
	   by a heavier weight, so its label keeps the width of every other. */
	.row.current {
		background: var(--nav-current);
		color: var(--nav-current-text);
	}

	.row.current .icon {
		color: var(--nav-current-text);
	}

	.row.collapsed {
		gap: 0;
		width: var(--nav-item-height);
		margin-inline: auto;
		padding: 0;
		justify-content: center;
	}

	.collapsed .label {
		flex: 0 0 0;
		opacity: 0;
	}

	@media (prefers-reduced-motion: reduce) {
		.row,
		.icon,
		.label {
			transition: none;
		}
	}
</style>
