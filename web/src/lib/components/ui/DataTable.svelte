<script lang="ts" generics="Row extends { id: string }">
	import type { Snippet } from 'svelte';
	import Checkbox from './Checkbox.svelte';
	import IconButton from './IconButton.svelte';
	import type { Column } from './table';

	type Props = {
		columns: Column[];
		rows: Row[];
		empty: string;
		row: Snippet<[Row]>;
		onOpen?: (row: Row) => void;
		label?: (row: Row) => string;
		selected?: string[];
		onSelect?: (ids: string[]) => void;
	};

	let { columns, rows, empty, row, onOpen, label, selected, onSelect }: Props = $props();

	const selectable = $derived(selected !== undefined && onSelect !== undefined);
	const ticked = $derived(new Set(selected ?? []));
	const allTicked = $derived(rows.length > 0 && rows.every((item) => ticked.has(item.id)));

	const headerState = $derived<boolean | 'indeterminate'>(
		allTicked ? true : rows.some((item) => ticked.has(item.id)) ? 'indeterminate' : false
	);

	function tickAll(checked: boolean) {
		onSelect?.(checked ? rows.map((item) => item.id) : []);
	}

	function tick(id: string, checked: boolean) {
		const ids = selected ?? [];

		onSelect?.(checked ? [...ids, id] : ids.filter((it) => it !== id));
	}

	const heading = $derived(columns.some((column) => column.label));

	let scrolled = $state(false);

	function track(event: Event) {
		scrolled = (event.currentTarget as HTMLElement).scrollLeft > 0;
	}
</script>

<div class="table-card">
	<div class="scroller" class:scrolled onscroll={track}>
		<table>
			{#if heading}
				<thead>
					<tr>
						{#if selectable}
							<th class="tick">
								<Checkbox checked={headerState} onChange={tickAll} title="Выбрать все строки" />
							</th>
						{/if}

						{#each columns as column (column.key)}
							<th
								scope="col"
								style:min-width={column.min}
								class:end={column.align === 'end'}
								class:grow={column.grow}
							>
								{column.label}
							</th>
						{/each}
						{#if onOpen}
							<th class="pin"></th>
						{/if}
					</tr>
				</thead>
			{/if}

			<tbody>
				{#each rows as item (item.id)}
					<tr
						class:clickable={onOpen !== undefined}
						class:selected={ticked.has(item.id)}
						onclick={() => onOpen?.(item)}
					>
						{#if selectable}
							<td class="tick" onclick={(event) => event.stopPropagation()}>
								<Checkbox
									checked={ticked.has(item.id)}
									onChange={(checked) => tick(item.id, checked)}
									title="Выбрать строку"
								/>
							</td>
						{/if}

						{@render row(item)}

						{#if onOpen}
							<td class="pin">
								<IconButton
									icon="ri-arrow-right-s-line"
									label={label?.(item) ?? 'Открыть'}
									size="sm"
									placement="left"
								/>
							</td>
						{/if}
					</tr>
				{:else}
					<tr>
						<td class="empty" colspan={columns.length + (onOpen ? 1 : 0) + (selectable ? 1 : 0)}>
							<span class="empty-state">
								<span class="empty-icon"
									><i class="ri-inbox-2-line" style:font-size="20px" aria-hidden="true"></i></span
								>
								{empty}
							</span>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
</div>

<style>
	.table-card {
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
	}

	.scroller {
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: separate;
		border-spacing: 0;
	}

	/* The cells of a row come from the caller's snippet, so they carry that
	   component's scope rather than this one's: every rule that has to reach
	   a `td` says so with :global, anchored to the table itself. */
	th,
	table :global(td) {
		padding: 0 var(--space-3);
		text-align: left;
		vertical-align: middle;
		white-space: nowrap;
	}

	table :global(td) {
		height: 56px;
		padding-top: var(--space-2);
		padding-bottom: var(--space-2);
		border-bottom: 1px solid var(--color-border);
		background: var(--color-surface);
		color: var(--color-text);
		font-size: var(--text-base);
		transition: background-color var(--speed-fast);
	}

	table :global(tbody tr:last-child td) {
		border-bottom: none;
	}

	th {
		height: 40px;
		border-bottom: 1px solid var(--color-border);
		background: var(--color-surface-alt);
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		font-weight: 600;
		letter-spacing: 0.06em;
		text-transform: uppercase;
	}

	th.grow {
		width: 100%;
	}

	th.end,
	table :global(td.end) {
		text-align: right;
	}

	/* Figures line up down a column only if every digit is as wide as the next. */
	table :global(td.end) {
		font-variant-numeric: tabular-nums;
	}

	th:first-child,
	table :global(td:first-child) {
		padding-left: var(--space-4);
	}

	th:last-child,
	table :global(td:last-child) {
		padding-right: var(--space-4);
	}

	tr.clickable {
		cursor: pointer;
	}

	tr.clickable:hover :global(td) {
		background: var(--row-hover);
	}

	tr.selected :global(td),
	tr.selected:hover :global(td) {
		background: var(--color-brand-subtle);
	}

	.tick {
		position: sticky;
		left: 0;
		z-index: 1;
		width: 1%;
		padding-right: 0;
		line-height: 0;
	}

	.tick::after {
		position: absolute;
		top: 0;
		right: 0;
		bottom: 0;
		width: 1px;
		background: var(--color-border);
		opacity: 0;
		transition: opacity var(--speed);
		content: '';
	}

	.scrolled .tick::after {
		opacity: 1;
	}

	.scrolled .tick {
		box-shadow: 8px 0 8px -8px rgb(0 0 0 / 25%);
	}

	.pin {
		position: sticky;
		right: 0;
		z-index: 1;
		width: 1%;
		padding-left: var(--space-1);
	}

	.pin::before {
		position: absolute;
		top: 0;
		bottom: 0;
		left: 0;
		width: 1px;
		background: var(--color-border);
		opacity: 0;
		transition: opacity var(--speed);
		content: '';
	}

	.scrolled .pin {
		box-shadow: -8px 0 8px -8px rgb(0 0 0 / 25%);
	}

	.scrolled .pin::before {
		opacity: 1;
	}

	.pin :global(.control) {
		margin-left: auto;
		color: var(--color-text-disabled);
		transition:
			color var(--speed-fast),
			transform var(--speed);
	}

	tr.clickable:hover .pin :global(.control) {
		color: var(--color-text);
		transform: translateX(2px);
	}

	td.empty {
		height: auto;
		padding: var(--space-6) var(--space-4);
		white-space: normal;
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		text-align: center;
	}

	.empty-icon {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		height: 40px;
		border-radius: var(--radius-pill);
		background: var(--color-secondary);
		color: var(--color-text-disabled);
	}
</style>
