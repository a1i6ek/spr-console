<script lang="ts">
	import { PasswordInput } from '@ark-ui/svelte/password-input';
	import FieldText from './FieldText.svelte';

	type Props = {
		label: string;
		value: string;
		disabled?: boolean;
		required?: boolean;
		name?: string;
		autocomplete?: 'current-password' | 'new-password';
		hint?: string;
	};

	let {
		label,
		value = $bindable(''),
		disabled,
		required,
		name,
		autocomplete = 'current-password',
		hint
	}: Props = $props();

	const uid = $props.id();
	const hintId = `${uid}-hint`;
</script>

<PasswordInput.Root class="field-root" {disabled} {required}>
	<div class="field-box" data-float={value !== '' || undefined}>
		<PasswordInput.Label><FieldText {label} {required} /></PasswordInput.Label>
		<PasswordInput.Control>
			<PasswordInput.Input
				{name}
				bind:value
				{autocomplete}
				aria-describedby={hint ? hintId : undefined}
			/>
			<PasswordInput.VisibilityTrigger aria-label="Показать пароль">
				<PasswordInput.Indicator>
					{#snippet fallback()}
						<i class="ri-eye-line" style:font-size="17px" aria-hidden="true"></i>
					{/snippet}
					<i class="ri-eye-off-line" style:font-size="17px" aria-hidden="true"></i>
				</PasswordInput.Indicator>
			</PasswordInput.VisibilityTrigger>
		</PasswordInput.Control>
	</div>

	{#if hint}
		<p id={hintId} data-part="helper-text">{hint}</p>
	{/if}
</PasswordInput.Root>
