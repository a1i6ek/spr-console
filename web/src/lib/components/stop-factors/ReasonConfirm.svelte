<script lang="ts">
	import { untrack } from 'svelte';
	import { ConfirmDialog, Input } from '#lib/components/ui/index.js';

	type Props = {
		open: boolean;
		title: string;
		description?: string;
		confirmLabel: string;
		tone?: 'danger' | 'warning' | 'info' | 'neutral';
		icon?: string;
		/** Asked with the reason typed; the dialog stays open, busy, until it settles. */
		onConfirm: (changeReason: string) => Promise<unknown>;
	};

	let {
		open = $bindable(false),
		title,
		description,
		confirmLabel,
		tone = 'warning',
		icon,
		onConfirm
	}: Props = $props();

	let reason = $state('');
	let busy = $state(false);

	// every opening starts with an empty reason
	$effect(() => {
		if (open) untrack(() => (reason = ''));
	});

	async function confirm() {
		if (!reason.trim() || busy) return;
		busy = true;
		try {
			await onConfirm(reason.trim());
		} finally {
			busy = false;
		}
	}
</script>

<ConfirmDialog
	bind:open
	{title}
	{description}
	{tone}
	{icon}
	{confirmLabel}
	cancelLabel="Отмена"
	{busy}
	onConfirm={confirm}
>
	<Input
		label="Причина изменения"
		bind:value={reason}
		maxlength={255}
		hint="Сохраняется в истории стоп-фактора"
		required
	/>
</ConfirmDialog>
