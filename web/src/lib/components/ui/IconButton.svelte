<script lang="ts">
	import type { IconName } from './icon';
	import { Portal } from '@ark-ui/svelte/portal';
	import { Tooltip } from '@ark-ui/svelte/tooltip';
	import type { ControlProps, Size } from './control';

	type Props = Omit<ControlProps, 'icon'> & {
		icon: IconName;
		label: string;
		placement?: 'top' | 'bottom' | 'left' | 'right';
		onclick?: () => void;
	};

	let {
		icon,
		label,
		size = 'md',
		variant = 'ghost',
		colorPalette = 'neutral',
		loading = false,
		disabled = false,
		placement = 'bottom',
		onclick
	}: Props = $props();

	const glyph: Record<Size, number> = { sm: 16, md: 18, lg: 20 };
</script>

<!-- The trigger is the button itself rather than a wrapper around one: a
     button inside a button is not markup a browser will keep. -->
<Tooltip.Root
	openDelay={250}
	closeDelay={80}
	positioning={{ placement, gutter: 6, strategy: 'fixed' }}
>
	<Tooltip.Trigger
		type="button"
		class="control"
		data-icon="true"
		data-size={size}
		data-variant={variant}
		data-palette={colorPalette}
		data-loading={loading || undefined}
		aria-label={label}
		disabled={disabled || loading}
		{onclick}
	>
		{#if loading}
			<span class="spinner" aria-hidden="true"></span>
		{:else}
			<i class={icon} style:font-size="{glyph[size]}px" aria-hidden="true"></i>
		{/if}
	</Tooltip.Trigger>

	<!-- Portalled to the end of the document, so no sticky column, scrolling
	     table or positioned heading it is opened from can cover or clip it. -->
	<Portal>
		<Tooltip.Positioner>
			<Tooltip.Content>{label}</Tooltip.Content>
		</Tooltip.Positioner>
	</Portal>
</Tooltip.Root>
