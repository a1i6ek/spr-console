<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import {
		Alert,
		Button,
		DatePicker,
		FieldGrid,
		Input,
		MonthPicker,
		PageHeader,
		Panel,
		notify,
		todayIso
	} from '#lib/components/ui/index.js';
	import { DownloadError, download } from '#lib/download.js';
	import { validate, type ReportField, type ReportInput } from '#lib/reports.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const report = $derived(data.report);
	const has = (field: ReportField) => (report.fields as readonly ReportField[]).includes(field);

	// a report opened from the overview comes with its fields filled in
	const param = (name: string) => page.url.searchParams.get(name) ?? '';

	let month = $state(param('month'));
	let inn = $state(param('inn'));
	let scoringId = $state(param('scoringId'));
	let from = $state(param('from'));
	let to = $state(param('to'));

	let busy = $state(false);
	let failure = $state<string | null>(null);

	const input = $derived<ReportInput>({ month, inn, scoringId, from, to });
	const problem = $derived(report.available ? validate(data.slug, input) : null);
	const filled = $derived(
		(!has('month') || month !== '') &&
			(!has('inn') || inn !== '') &&
			(!has('scoringId') || scoringId !== '') &&
			(!has('period') || (from !== '' && to !== ''))
	);
	// a field shows its mistake once it is filled; an empty form shows nothing
	const shownProblem = $derived(filled ? problem : null);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (busy || problem) return;
		busy = true;
		failure = null;
		try {
			const name = await download(
				resolve('/(app)/reports/[report]/download', { report: data.slug }),
				input,
				`${data.slug}.bin`
			);
			notify.success('Файл скачан', name);
		} catch (err) {
			if (err instanceof DownloadError && err.status === 401) {
				await goto(resolve('/login'));
				return;
			}
			failure = err instanceof Error ? err.message : 'Не удалось получить файл';
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head>
	<title>{pageTitle(report.title, 'Отчеты')}</title>
</svelte:head>

<PageHeader crumbs={['Отчеты', report.title]} description={report.description} />

{#if !report.available}
	<Alert tone="info">
		Выгрузка «{report.title}» будет доступна после подключения сервиса отчетов.
	</Alert>
{:else}
	<Panel title="Параметры" icon="ri-equalizer-line">
		<form onsubmit={submit} novalidate>
			<FieldGrid columns={has('period') ? 2 : 3}>
				{#if has('month')}
					<!-- no report for a month that has not come yet -->
					<MonthPicker
						label="Месяц"
						bind:value={month}
						max={todayIso().slice(0, 7)}
						required
						disabled={busy}
					/>
				{/if}
				{#if has('inn')}
					<Input
						label="ИНН клиента"
						bind:value={inn}
						inputmode="numeric"
						maxlength={14}
						placeholder="14 цифр"
						required
						disabled={busy}
						error={inn && !/^\d{14}$/.test(inn) ? 'ИНН должен содержать ровно 14 цифр' : undefined}
					/>
				{/if}
				{#if has('scoringId')}
					<Input
						label="scoringId"
						bind:value={scoringId}
						inputmode="numeric"
						placeholder="Например, 1024"
						required
						disabled={busy}
						error={scoringId && !/^\d+$/.test(scoringId) ? 'Только цифры' : undefined}
					/>
				{/if}
				{#if has('period')}
					<DatePicker
						label="Период с"
						bind:value={from}
						max={to || undefined}
						required
						disabled={busy}
					/>
					<DatePicker
						label="Период по"
						bind:value={to}
						min={from || undefined}
						required
						disabled={busy}
					/>
				{/if}
			</FieldGrid>

			{#if shownProblem && !has('inn') && !has('scoringId')}
				<p class="problem">
					<i class="ri-error-warning-line" aria-hidden="true"></i>
					{shownProblem}
				</p>
			{/if}

			<div class="footer">
				<span class="result">
					<i class="ri-file-download-line" aria-hidden="true"></i>
					{report.result}{#if report.note}<span class="note"> · {report.note}</span>{/if}
				</span>
				<Button
					type="submit"
					icon="ri-download-2-line"
					loading={busy}
					disabled={!filled || !!problem}
				>
					{busy ? 'Формируется…' : 'Скачать'}
				</Button>
			</div>
		</form>
	</Panel>

	{#if busy && data.slug === 'general'}
		<Alert tone="info"
			>Отчет формируется. Это может занять до 10 минут — не закрывайте страницу.</Alert
		>
	{/if}
	{#if failure}
		<Alert tone="danger">{failure}</Alert>
	{/if}
{/if}

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.problem {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		margin: 0;
		color: var(--color-danger);
		font-size: var(--text-sm);
	}

	.footer {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	.result {
		display: inline-flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 6px;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.note {
		color: var(--color-text-hint);
	}
</style>
