<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAnchorAttributes } from 'svelte/elements';
	import type { ControlProps } from './control';

	type Props = HTMLAnchorAttributes &
		Omit<ControlProps, 'loading'> & {
			href: string;
			children: Snippet;
		};

	let {
		href,
		size = 'md',
		variant = 'solid',
		colorPalette = 'neutral',
		disabled = false,
		icon,
		children,
		...rest
	}: Props = $props();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->
<a
	href={disabled ? undefined : href}
	class="control"
	data-size={size}
	data-variant={variant}
	data-palette={colorPalette}
	aria-disabled={disabled || undefined}
	role={disabled ? 'link' : undefined}
	{...rest}
>
	{#if icon}
		<i class={icon} aria-hidden="true"></i>
	{/if}

	{@render children()}
</a>

<!-- eslint-enable svelte/no-navigation-without-resolve -->
