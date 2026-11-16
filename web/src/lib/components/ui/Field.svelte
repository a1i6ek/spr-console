<script lang="ts">
	import type { IconName } from './icon';
	import type { Snippet } from 'svelte';
	import { Field } from '@ark-ui/svelte/field';
	import FieldText from './FieldText.svelte';

	type Props = {
		label: string;
		children: Snippet;
		icon?: IconName;
		hint?: string;
		error?: string;
		required?: boolean;
		disabled?: boolean;
		readOnly?: boolean;
		filled?: boolean;
		compact?: boolean;
	};

	let {
		label,
		children,
		icon,
		hint,
		error,
		required,
		disabled,
		readOnly,
		filled = false,
		compact = false
	}: Props = $props();
</script>

<Field.Root class="field-root" {required} {disabled} {readOnly} invalid={error !== undefined}>
	<div class="field-box" data-float={filled || undefined} data-compact={compact || undefined}>
		<Field.Label><FieldText {label} {icon} {required} /></Field.Label>
		{@render children()}
	</div>

	{#if error}
		<Field.ErrorText>{error}</Field.ErrorText>
	{:else if hint}
		<Field.HelperText>{hint}</Field.HelperText>
	{/if}
</Field.Root>
