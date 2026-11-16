<script lang="ts">
	import type { Snippet } from 'svelte';
	import { Portal } from '@ark-ui/svelte/portal';
	import { Tooltip } from '@ark-ui/svelte/tooltip';

	type Props = {
		label: string;
		placement?: 'top' | 'bottom' | 'left' | 'right';
		disabled?: boolean;
		children: Snippet<[() => Record<string, unknown>]>;
	};

	let { label, placement = 'bottom', disabled = false, children }: Props = $props();
</script>

<Tooltip.Root
	{disabled}
	openDelay={250}
	closeDelay={80}
	positioning={{ placement, gutter: 6, strategy: 'fixed' }}
>
	<Tooltip.Trigger>
		{#snippet asChild(trigger)}
			{@render children(trigger as () => Record<string, unknown>)}
		{/snippet}
	</Tooltip.Trigger>

	<Portal>
		<Tooltip.Positioner>
			<Tooltip.Content>{label}</Tooltip.Content>
		</Tooltip.Positioner>
	</Portal>
</Tooltip.Root>
