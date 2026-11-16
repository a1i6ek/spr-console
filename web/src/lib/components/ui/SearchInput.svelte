<script lang="ts">
	import type { Size } from './control';

	type Props = {
		value: string;
		label: string;
		placeholder?: string;
		size?: Exclude<Size, 'lg'>;
		onsubmit?: () => void;
		id?: string;
		name?: string;
		oninput?: (event: Event) => void;
		onkeydown?: (event: KeyboardEvent) => void;
	};

	let {
		value = $bindable(''),
		label,
		placeholder,
		size = 'md',
		onsubmit,
		id,
		name,
		oninput,
		onkeydown
	}: Props = $props();

	const uid = $props.id();
</script>

{#snippet field()}
	<i class="ri-search-line" aria-hidden="true"></i>
	<input
		id={id ?? uid}
		{name}
		type="search"
		{placeholder}
		bind:value
		{oninput}
		{onkeydown}
		aria-label={label}
	/>
{/snippet}

{#if onsubmit}
	<form
		class="search field-box"
		data-size={size}
		role="search"
		onsubmit={(event) => {
			event.preventDefault();
			onsubmit();
		}}
	>
		{@render field()}
	</form>
{:else}
	<label class="search field-box" data-size={size}>
		{@render field()}
	</label>
{/if}

<style>
	.search {
		--search-height: var(--control-height);

		flex: 1 1 auto;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
		height: var(--search-height);
		max-height: var(--search-height);
		padding: 0 12px;
		color: var(--color-text-hint);
	}

	.search[data-size='sm'] {
		--search-height: var(--control-height-sm);

		padding: 0 10px;
	}

	input {
		flex: 1;
		min-width: 0;
		border: none;
		background: transparent;
		color: var(--color-text);
		font: inherit;
		font-size: var(--text-base);
		font-weight: 500;
	}

	.search[data-size='sm'] input {
		font-size: var(--text-sm);
	}

	input:focus {
		outline: none;
	}
</style>
