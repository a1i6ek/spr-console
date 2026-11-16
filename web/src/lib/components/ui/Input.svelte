<script lang="ts">
	import type { IconName } from './icon';
	import type { HTMLInputAttributes } from 'svelte/elements';
	import { Field as ArkField } from '@ark-ui/svelte/field';
	import CopyButton from './CopyButton.svelte';
	import Field from './Field.svelte';

	type Props = Omit<HTMLInputAttributes, 'value' | 'size'> & {
		label: string;
		value: string;
		icon?: IconName;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
		readOnly?: boolean;
		copyable?: boolean;
		suffix?: string;
		compact?: boolean;
	};

	let {
		label,
		value = $bindable(''),
		icon,
		hint,
		error,
		required,
		disabled,
		readOnly,
		copyable = false,
		suffix,
		compact = false,
		...input
	}: Props = $props();

	/** Kinds of field the browser draws a placeholder into — "dd/mm/yyyy",
	    "--:--" — so the label must sit above it even while it is empty. */
	const alwaysFilled = new Set(['date', 'time', 'datetime-local', 'month', 'week']);
</script>

<Field
	{label}
	{icon}
	{hint}
	{error}
	{required}
	{disabled}
	{readOnly}
	{compact}
	filled={(value !== '' && value !== null && value !== undefined) ||
		alwaysFilled.has(String(input.type))}
>
	{#if copyable || suffix}
		<div class="adorned" class:copyable class:suffixed={suffix !== undefined}>
			<ArkField.Input bind:value readonly={readOnly} {...input} />
			{#if suffix}
				<span class="suffix">{suffix}</span>
			{/if}
			{#if copyable}
				<span class="action"><CopyButton {value} {label} /></span>
			{/if}
		</div>
	{:else}
		<ArkField.Input bind:value readonly={readOnly} {...input} />
	{/if}
</Field>

<style>
	.adorned {
		position: relative;
	}

	.copyable :global([data-part='input']) {
		padding-right: 46px;
	}

	.suffixed :global([data-part='input']) {
		padding-right: 5.5rem;
	}

	.action {
		position: absolute;
		top: 50%;
		right: 6px;
		z-index: 2;
		transform: translateY(-50%);
	}

	.suffix {
		position: absolute;
		top: var(--field-value-top);
		right: 14px;
		z-index: 2;
		line-height: var(--field-line);
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		pointer-events: none;
	}

	.copyable.suffixed .suffix {
		right: 46px;
	}
</style>
