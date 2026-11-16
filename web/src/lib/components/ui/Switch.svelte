<script lang="ts">
	import { Switch } from '@ark-ui/svelte/switch';
	import { onlyTheSwitch } from './switch';

	type Props = {
		label: string;
		checked: boolean;
		onChange?: (checked: boolean) => void;
		disabled?: boolean;
		id?: string;
		name?: string;
	};

	let { label, checked = $bindable(false), onChange, disabled, id, name }: Props = $props();
</script>

<Switch.Root
	{id}
	{name}
	{checked}
	{disabled}
	onclick={onlyTheSwitch}
	onCheckedChange={(details) => {
		checked = details.checked;
		onChange?.(details.checked);
	}}
>
	<Switch.Control><Switch.Thumb /></Switch.Control>
	<Switch.Label>{label}</Switch.Label>
	<Switch.HiddenInput />
</Switch.Root>
