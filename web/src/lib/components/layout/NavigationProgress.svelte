<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { afterNavigate, beforeNavigate } from '$app/navigation';
	import { navigating } from '$app/state';
	import { NavigationProgress } from '#lib/state/progress.svelte.js';

	const FADE = 150;

	let bar: HTMLDivElement;

	// The jump back to the start is put on the page and read back — the read
	// makes the browser apply it — before the transition returns, so the bar
	// starts from nothing rather than sliding back. No animation frame is
	// waited for: a tab in the background never gets one.
	const progress = new NavigationProgress(async () => {
		await tick();
		void bar.offsetWidth;
	});

	// SvelteKit runs no second beforeNavigate while one is pending, so finishing is shared, and
	// an aborted navigation leaves the bar alone if another replaced it. afterNavigate covers
	// very quick preloads.
	const finish = () => void progress.finish();

	beforeNavigate((navigation) => {
		if (navigation.shallow && navigation.type === 'goto') return;
		if (navigation.willUnload) return;

		progress.start();
		void navigation.complete.catch(() => {
			queueMicrotask(() => {
				if (navigating.to === null) finish();
			});
		});
	});

	afterNavigate(finish);

	$effect(() => {
		if (navigating.to !== null) return;
		untrack(finish);
	});

	$effect(() => () => progress.dispose());
</script>

<div
	bind:this={bar}
	class="progress"
	class:visible={progress.visible}
	class:instant={progress.instant}
	style:--progress={progress.value}
	style:--fade="{FADE}ms"
	aria-hidden="true"
></div>

<style>
	.progress {
		position: fixed;
		top: 0;
		left: 0;
		z-index: 100;
		width: 100%;
		height: 2px;
		background: var(--color-brand);
		opacity: 0;
		pointer-events: none;
		transform: translateX(calc((var(--progress) - 1) * 100%));
		transition:
			transform 120ms linear,
			opacity var(--fade) linear;
	}

	.visible {
		opacity: 1;
		will-change: transform;
		transition: transform 120ms linear;
	}

	.instant {
		transition: none;
	}

	@media (prefers-reduced-motion: reduce) {
		.progress,
		.visible {
			transition: opacity var(--fade) linear;
		}
	}
</style>
