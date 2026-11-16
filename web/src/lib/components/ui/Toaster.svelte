<script lang="ts">
	import { Toast, Toaster } from '@ark-ui/svelte/toast';
	import { toaster } from './toast';
</script>

<Toaster {toaster}>
	{#snippet children(toast)}
		<Toast.Root class={toast().description ? 'toast detailed' : 'toast'}>
			<span class="glyph">
				{#if toast().type === 'success'}
					<i class="ri-checkbox-circle-fill" style:font-size="18px" aria-hidden="true"></i>
				{:else if toast().type === 'info'}
					<i class="ri-information-fill" style:font-size="18px" aria-hidden="true"></i>
				{:else}
					<i class="ri-error-warning-fill" style:font-size="18px" aria-hidden="true"></i>
				{/if}
			</span>
			<div class="message">
				<Toast.Title class="title">{toast().title}</Toast.Title>
				{#if toast().description}
					<Toast.Description class="description">{toast().description}</Toast.Description>
				{/if}
			</div>
			<Toast.CloseTrigger class="close" aria-label="Закрыть">
				<i class="ri-close-line" style:font-size="14px" aria-hidden="true"></i>
			</Toast.CloseTrigger>
		</Toast.Root>
	{/snippet}
</Toaster>

<style>
	:global(.toast) {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		width: max-content;
		max-width: min(26rem, calc(100vw - 2 * var(--page-gutter)));
		min-height: 44px;
		padding: 6px 6px 6px 12px;
		border-radius: var(--radius-pill);
		background: var(--toast-surface);
		color: var(--toast-text);
		box-shadow:
			0 12px 32px -8px rgb(0 0 0 / 45%),
			0 2px 6px rgb(0 0 0 / 20%);

		translate: var(--x) var(--y);
		scale: var(--scale);
		z-index: var(--z-index);
		height: var(--height);
		opacity: var(--opacity);
		will-change: translate, opacity, scale;
		transition:
			translate 400ms,
			scale 400ms,
			opacity 400ms,
			height 400ms;
		transition-timing-function: cubic-bezier(0.21, 1.02, 0.73, 1);
	}

	:global(.toast.detailed) {
		align-items: flex-start;
		padding: 12px 10px 12px 14px;
		border-radius: var(--radius-surface);
	}

	:global(.toast.detailed .glyph) {
		margin-top: 1px;
	}

	:global(.toast.detailed .close) {
		margin-top: -4px;
	}

	:global(.toast[data-state='closed']) {
		transition:
			translate 200ms,
			scale 200ms,
			opacity 200ms;
		transition-timing-function: cubic-bezier(0.06, 0.71, 0.55, 1);
	}

	.glyph {
		display: inline-flex;
		flex: none;
		color: var(--toast-icon);
	}

	.message {
		display: flex;
		flex: 1;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
		overflow-wrap: anywhere;
	}

	:global(.toast .title) {
		font-size: var(--text-base);
		font-weight: 600;
		line-height: 1.4;
	}

	:global(.toast .description) {
		color: var(--toast-hint);
		font-size: var(--text-sm);
		line-height: 1.45;
	}

	:global(.toast .close) {
		display: inline-flex;
		flex: none;
		align-items: center;
		justify-content: center;
		width: 28px;
		height: 28px;
		border: none;
		border-radius: var(--radius-pill);
		background: color-mix(in srgb, var(--toast-text), transparent 90%);
		color: var(--toast-text);
		cursor: pointer;
		transition: background-color var(--speed-fast);
	}

	:global(.toast .close:hover) {
		background: color-mix(in srgb, var(--toast-text), transparent 80%);
	}

	:global(.toast .close:focus-visible) {
		outline: 2px solid var(--toast-icon);
		outline-offset: 1px;
	}

	@media (prefers-reduced-motion: reduce) {
		:global(.toast),
		:global(.toast[data-state='closed']) {
			transition: opacity 150ms;
		}
	}
</style>
