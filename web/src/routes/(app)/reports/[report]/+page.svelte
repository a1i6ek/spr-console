<script lang="ts">
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import {
		Button,
		DatePicker,
		EmptyState,
		FieldGrid,
		Input,
		PageHeader,
		Panel,
		Select,
		optionsOf
	} from '#lib/components/ui/index.js';
	import { segments } from '#lib/labels.js';
	import type { ReportField } from '#lib/reports.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const has = (field: ReportField) =>
		(data.report.fields as readonly ReportField[]).includes(field);

	const fileTypes = { xlsx: 'Excel (.xlsx)', csv: 'CSV', pdf: 'PDF' };

	// a report opened from the overview comes with its period filled in
	const param = (name: string) => page.url.searchParams.get(name) ?? '';
	const day = (name: string) => (/^\d{4}-\d{2}-\d{2}$/.test(param(name)) ? param(name) : '');

	let from = $state(day('from'));
	let to = $state(day('to'));
	let date = $state(day('date'));
	let segment = $state(param('segment'));
	let productId = $state(param('productId'));
	let inn = $state(param('inn'));
	let fileType = $state(param('fileType'));
	let requested = $state(false);

	const innValid = $derived(/^\d{14}$/.test(inn));
	const ready = $derived(
		(!has('period') || (from !== '' && to !== '' && from <= to)) &&
			(!has('date') || date !== '') &&
			(!has('inn') || innValid) &&
			(!has('fileType') || fileType !== '')
	);
</script>

<svelte:head>
	<title>{pageTitle(data.report.title, 'Отчеты')}</title>
</svelte:head>

<PageHeader crumbs={['Отчеты', data.report.title]} description={data.report.description} />

<Panel title="Параметры" icon="ri-equalizer-line">
	<form
		onsubmit={(event) => {
			event.preventDefault();
			requested = true;
		}}
	>
		<FieldGrid columns={3}>
			{#if has('inn')}
				<Input
					label="ИНН"
					bind:value={inn}
					inputmode="numeric"
					maxlength={14}
					required
					error={inn && !innValid ? 'ИНН — 14 цифр' : undefined}
				/>
			{/if}
			{#if has('period')}
				<DatePicker label="Период с" bind:value={from} max={to || undefined} required />
				<DatePicker label="Период по" bind:value={to} min={from || undefined} required />
			{/if}
			{#if has('date')}
				<DatePicker label="Дата" bind:value={date} required />
			{/if}
			{#if has('segment')}
				<Select
					label="Сегмент"
					bind:value={segment}
					options={optionsOf(segments)}
					placeholder="Все сегменты"
					clearable
				/>
			{/if}
			{#if has('products')}
				<Select
					label="Продукт"
					bind:value={productId}
					options={data.products.map((p) => ({
						value: String(p.id),
						label: p.name,
						description: segments[p.segment]
					}))}
					placeholder="Все продукты"
					clearable
				/>
			{/if}
			{#if has('fileType')}
				<Select
					label="Формат файла"
					bind:value={fileType}
					options={optionsOf(fileTypes)}
					placeholder="Выберите формат"
					required
				/>
			{/if}
		</FieldGrid>
		<div class="actions">
			<Button type="submit" icon="ri-play-line" disabled={!ready}>Сформировать</Button>
		</div>
	</form>
</Panel>

{#if requested}
	<EmptyState icon="ri-tools-line" title="Раздел в разработке">
		Формирование отчета появится в следующей версии консоли.
	</EmptyState>
{:else}
	<EmptyState icon="ri-file-chart-line" title="Отчет не сформирован">
		Заполните параметры и нажмите «Сформировать».
	</EmptyState>
{/if}

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.actions {
		display: flex;
		justify-content: flex-end;
	}
</style>
