<script lang="ts">
	import type { Snippet } from 'svelte';
	import { Menu } from '@ark-ui/svelte/menu';

	type Props = {
		label?: string;
		children: Snippet;
	};

	let { label, children }: Props = $props();
</script>

<Menu.ItemGroup>
	{#if label}<Menu.ItemGroupLabel>{label}</Menu.ItemGroupLabel>{/if}
	{@render children()}
</Menu.ItemGroup>
