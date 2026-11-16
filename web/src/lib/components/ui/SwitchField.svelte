<script lang="ts">
	import { Switch } from '@ark-ui/svelte/switch';
	import { onlyTheSwitch } from './switch';

	type Props = {
		label: string;
		description?: string;
		checked: boolean;
		disabled?: boolean;
		id?: string;
		name?: string;
		onChange?: (checked: boolean) => void;
	};

	let {
		label,
		description,
		checked = $bindable(false),
		disabled,
		id,
		name,
		onChange
	}: Props = $props();
</script>

<Switch.Root
	class="switch-field"
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
	<span class="text">
		<Switch.Label>{label}</Switch.Label>
		{#if description}
			<span class="description">{description}</span>
		{/if}
	</span>
	<Switch.Control><Switch.Thumb /></Switch.Control>
	<Switch.HiddenInput />
</Switch.Root>

<style>
	:global([data-scope='switch'][data-part='root'].switch-field) {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-3) 0;
	}

	:global(.switch-field + .switch-field) {
		border-top: 1px solid var(--color-border);
	}

	.text {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}

	.text :global([data-part='label']) {
		font-weight: 500;
	}

	.description {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		line-height: 1.4;
	}
</style>
