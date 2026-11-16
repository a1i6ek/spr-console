<script lang="ts">
	import { Menu } from '@ark-ui/svelte/menu';
	import type { IconName } from './icon';

	type Props = {
		value: string;
		icon: IconName;
		label: string;
		description?: string;
		href?: string;
		danger?: boolean;
		onSelect?: () => void;
	};

	let { value, icon, label, description, href, danger = false, onSelect }: Props = $props();
</script>

{#snippet content()}
	<i class={icon} aria-hidden="true"></i>
	<span class="text">
		{label}
		{#if description}<small>{description}</small>{/if}
	</span>
	{#if href}<span class="external"
			><i class="ri-external-link-line" style:font-size="14px" aria-hidden="true"></i></span
		>{/if}
{/snippet}

{#if href}
	<Menu.Item {value}>
		{#snippet asChild(item)}
			<!-- Somewhere else entirely, so there is no route for resolve() to
			     make of it. -->
			<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -->
			<a {...item()} {href} target="_blank" rel="noreferrer noopener">{@render content()}</a>
		{/snippet}
	</Menu.Item>
{:else}
	<Menu.Item {value} data-palette={danger ? 'danger' : undefined} {onSelect}>
		{@render content()}
	</Menu.Item>
{/if}

<style>
	.text {
		display: flex;
		flex: 1;
		flex-direction: column;
		min-width: 0;
		line-height: 1.3;
	}

	small {
		overflow: hidden;
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		font-weight: 400;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.external {
		display: inline-flex;
		margin-left: auto;
		color: var(--color-text-hint);
	}
</style>
