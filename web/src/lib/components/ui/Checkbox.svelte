<script lang="ts">
	import { Checkbox } from '@ark-ui/svelte/checkbox';

	type Props = {
		checked: boolean | 'indeterminate';
		onChange: (checked: boolean) => void;
		label?: string;
		title?: string;
		disabled?: boolean;
		id?: string;
		name?: string;
	};

	let { checked, onChange, label, title, disabled, id, name }: Props = $props();
</script>

<Checkbox.Root
	{id}
	{name}
	{checked}
	{disabled}
	onCheckedChange={(details) => onChange(details.checked === true)}
>
	<Checkbox.Control>
		<Checkbox.Indicator>
			<i class="ri-check-line" style:font-size="14px" aria-hidden="true"></i>
		</Checkbox.Indicator>
		<Checkbox.Indicator indeterminate>
			<i class="ri-subtract-line" style:font-size="14px" aria-hidden="true"></i>
		</Checkbox.Indicator>
	</Checkbox.Control>

	{#if label}
		<Checkbox.Label>{label}</Checkbox.Label>
	{/if}

	<Checkbox.HiddenInput aria-label={label ?? title} />
</Checkbox.Root>
