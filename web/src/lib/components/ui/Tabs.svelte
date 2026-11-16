<script lang="ts">
	import type { Snippet } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import TabList, { type Tab } from './TabList.svelte';

	type Props = {
		tabs: Tab[];
		value: string;
		label: string;
		panel: Snippet<[string]>;
	};

	let { tabs, value = $bindable(), label, panel }: Props = $props();

	const id = $props.id();

	const tabId = (tab: string) => `${id}-tab-${tab}`;
	const panelId = (tab: string) => `${id}-panel-${tab}`;

	// A panel once opened stays mounted, so what was typed in it survives a
	// look at another tab.
	const opened = new SvelteSet<string>();

	$effect(() => {
		opened.add(value);
	});
</script>

<div class="tabs">
	<TabList {tabs} bind:value {label} {tabId} {panelId} />

	{#each tabs as tab (tab.value)}
		{#if opened.has(tab.value) || tab.value === value}
			<div
				class="panel"
				role="tabpanel"
				id={panelId(tab.value)}
				aria-labelledby={tabId(tab.value)}
				hidden={tab.value !== value}
			>
				{@render panel(tab.value)}
			</div>
		{/if}
	{/each}
</div>

<style>
	.panel {
		padding-top: var(--space-5);
	}
</style>
