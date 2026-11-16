<script lang="ts">
	import { beforeNavigate } from '$app/navigation';
	import { updated } from '$app/state';
	import favicon from '#lib/assets/favicon.svg';
	import NavigationProgress from '#lib/components/layout/NavigationProgress.svelte';
	import { Toaster } from '#lib/components/ui/index.js';
	import '@fontsource-variable/inter';
	import 'remixicon/fonts/remixicon.css';
	import '#lib/scss/main.scss';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	// After a deploy, a tab that was already open still runs the old code;
	// the next link it follows loads the new version whole instead.
	beforeNavigate(({ willUnload, to }) => {
		if (updated.current && !willUnload && to?.url) location.href = to.url.href;
	});
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<NavigationProgress />

{@render children()}

<Toaster />
