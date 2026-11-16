<script lang="ts">
	import type { IconName } from './icon';
	import type { HTMLTextareaAttributes } from 'svelte/elements';
	import { Field as ArkField } from '@ark-ui/svelte/field';
	import Field from './Field.svelte';

	type Props = Omit<HTMLTextareaAttributes, 'value'> & {
		label: string;
		value: string;
		icon?: IconName;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
		rows?: number;
	};

	let {
		label,
		value = $bindable(''),
		icon,
		hint,
		error,
		required,
		disabled,
		rows = 4,
		...textarea
	}: Props = $props();
</script>

<Field {label} {icon} {hint} {error} {required} {disabled} filled={value !== ''}>
	<ArkField.Textarea bind:value {rows} {...textarea} />
</Field>
