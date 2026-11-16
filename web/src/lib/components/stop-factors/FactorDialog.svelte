<script lang="ts">
	import { untrack } from 'svelte';
	import type { Metric, Product, StopFactor } from '#lib/api/index.js';
	import {
		Button,
		FormSection,
		FullscreenDialog,
		Input,
		notify
	} from '#lib/components/ui/index.js';
	import { submit } from '#lib/forms.js';
	import { toDraft, toPayload } from '#lib/stop-factors.js';
	import FactorFields from './FactorFields.svelte';

	type Props = {
		open: boolean;
		/** The stop factor to change; none: a new one. */
		factor?: StopFactor;
		metrics: Metric[];
		products: Product[];
		/** Told what was saved. */
		onSaved?: (factor: StopFactor) => unknown;
	};

	let { open = $bindable(false), factor, metrics, products, onSaved }: Props = $props();

	let draft = $state(toDraft());
	let changeReason = $state('');
	let saving = $state(false);

	// a new stop factor needs no reason; a change does, it is kept in the history
	const ready = $derived(
		draft.metricId !== '' && draft.name.trim() !== '' && (!factor || changeReason.trim() !== '')
	);

	$effect(() => {
		if (!open) return;
		untrack(() => {
			draft = toDraft(factor);
			changeReason = '';
		});
	});

	const save = submit<StopFactor>({
		input: () => (factor ? [factor.factorId, toPayload(draft, changeReason)] : [toPayload(draft)]),
		when: () => ready,
		pending: (busy) => (saving = busy),
		done: async (saved) => {
			open = false;
			if (factor && saved.versionId === factor.versionId) {
				notify.info('Изменений нет', 'Новая версия не создана');
			} else {
				notify.success(
					factor ? 'Стоп-фактор сохранен' : 'Стоп-фактор добавлен',
					`Версия ${saved.versionNumber}`
				);
			}
			await onSaved?.(saved);
		},
		failed: (err) => notify.error(err, 'Не удалось сохранить стоп-фактор')
	});
</script>

<FullscreenDialog
	bind:open
	title={factor ? factor.name : 'Новый стоп-фактор'}
	description={factor
		? 'Сохранение создаст следующую версию этого стоп-фактора; прошлые проверки сохранят свои правила.'
		: 'Новый стоп-фактор проверяется последним.'}
	action={factor ? '/actions/factors?/update' : '/actions/factors?/create'}
	onsubmit={save}
>
	<FactorFields bind:draft {metrics} {products} />

	{#if factor}
		<FormSection title="Причина изменения" description="Сохраняется в истории стоп-фактора.">
			<Input label="Причина" bind:value={changeReason} maxlength={255} required />
		</FormSection>
	{/if}

	{#snippet actions()}
		<Button variant="ghost" onclick={() => (open = false)}>Отмена</Button>
		<Button type="submit" loading={saving} disabled={!ready}>
			{factor ? 'Сохранить' : 'Добавить'}
		</Button>
	{/snippet}
</FullscreenDialog>
