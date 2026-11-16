<script lang="ts">
	import type { Snippet } from 'svelte';
	import { Dialog } from '@ark-ui/svelte/dialog';
	import { Portal } from '@ark-ui/svelte/portal';
	import type { IconName } from './icon';
	import Button from './Button.svelte';

	type Tone = 'danger' | 'warning' | 'info' | 'neutral';

	type Props = {
		open?: boolean;
		title: string;
		description?: string;
		tone?: Tone;
		icon?: IconName;
		confirmLabel: string;
		cancelLabel?: string;
		busy?: boolean;
		onConfirm: () => void;
		children?: Snippet;
	};

	let {
		open = $bindable(false),
		title,
		description,
		tone = 'danger',
		icon,
		confirmLabel,
		cancelLabel = 'Отмена',
		busy = false,
		onConfirm,
		children
	}: Props = $props();

	const icons: Record<Tone, IconName> = {
		danger: 'ri-error-warning-line',
		warning: 'ri-alert-line',
		info: 'ri-information-line',
		neutral: 'ri-question-line'
	};

	/** Focus starts on the way out, not the way in: Enter on a dialog that
	    has only just appeared should not do the thing it asks about. */
	let actions = $state<HTMLElement | null>(null);

	const badgeIcon = $derived(icon ?? icons[tone]);
</script>

<Dialog.Root
	{open}
	role="alertdialog"
	onOpenChange={(details) => {
		if (!busy || details.open) open = details.open;
	}}
	closeOnInteractOutside={!busy}
	closeOnEscape={!busy}
	lazyMount
	unmountOnExit
	initialFocusEl={() => actions?.querySelector('button') ?? null}
>
	<Portal>
		<Dialog.Backdrop class="confirm-backdrop" />
		<Dialog.Positioner class="confirm-positioner">
			<Dialog.Content class="confirm-content" data-palette={tone}>
				<div class="head">
					<span class="badge" aria-hidden="true">
						<i class={badgeIcon} style:font-size="18px" aria-hidden="true"></i>
					</span>

					<div class="text">
						<Dialog.Title>{title}</Dialog.Title>
						{#if description}<Dialog.Description>{description}</Dialog.Description>{/if}
						{#if children}<div class="body">{@render children()}</div>{/if}
					</div>
				</div>

				<div class="actions" bind:this={actions}>
					<Button variant="subtle" disabled={busy} onclick={() => (open = false)}>
						{cancelLabel}
					</Button>
					<Button colorPalette={tone} loading={busy} disabled={busy} onclick={onConfirm}>
						{confirmLabel}
					</Button>
				</div>
			</Dialog.Content>
		</Dialog.Positioner>
	</Portal>
</Dialog.Root>

<style>
	:global(.confirm-backdrop[data-scope='dialog'][data-part='backdrop']) {
		z-index: 60;
	}

	:global(.confirm-positioner[data-scope='dialog'][data-part='positioner']) {
		z-index: 65;
	}

	:global(.confirm-content[data-scope='dialog'][data-part='content']) {
		--dialog-width: 26rem;

		padding: var(--space-4);
	}

	.head {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
	}

	.badge {
		display: inline-flex;
		flex: none;
		align-items: center;
		justify-content: center;
		width: 34px;
		height: 34px;
		border: 1px solid var(--palette-border);
		border-radius: var(--radius-control);
		background: var(--palette-subtle);
		color: var(--palette-fg);
	}

	.text {
		flex: 1;
		min-width: 0;
		padding-top: 6px;
	}

	.text :global([data-scope='dialog'][data-part='title']) {
		font-size: var(--text-base);
		line-height: 1.4;
		overflow-wrap: anywhere;
	}

	.text :global([data-scope='dialog'][data-part='description']) {
		margin-top: 2px;
		font-size: var(--text-sm);
		line-height: 1.5;
	}

	.body {
		margin-top: var(--space-3);
		font-size: var(--text-sm);
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: var(--space-2);
		margin-top: var(--space-4);
	}

	@media (max-width: 34rem) {
		:global(.confirm-positioner[data-scope='dialog'][data-part='positioner']) {
			padding: var(--space-2);
		}

		.actions > :global(*) {
			flex: 1;
		}
	}
</style>
