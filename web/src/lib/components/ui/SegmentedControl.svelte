<script lang="ts" generics="Value extends string">
	type Props = {
		label: string;
		options: readonly { value: Value; label: string; reset?: boolean; disabled?: boolean }[];
		value: Value;
		onChange: (value: Value) => void;
	};

	let { label, options, value, onChange }: Props = $props();
</script>

<div class="segmented" role="group" aria-label={label}>
	{#each options as option, index (option.value)}
		<button
			type="button"
			class:selected={option.value === value}
			aria-pressed={option.value === value}
			disabled={option.disabled}
			onclick={() => onChange(option.value)}
		>
			{option.label}
		</button>

		{#if option.reset && index < options.length - 1}
			<span class="rule" aria-hidden="true"></span>
		{/if}
	{/each}
</div>

<style>
	.segmented {
		display: flex;
		flex: none;
		align-items: center;
		gap: 2px;
		height: var(--control-height);
		padding: 3px;
		border: 1px solid var(--color-input-border);
		border-radius: var(--radius-control);
		background: var(--color-secondary);
	}

	button {
		display: inline-flex;
		align-items: center;
		height: 100%;
		padding: 0 var(--space-3);
		border: none;
		border-radius: var(--radius-control);
		background: transparent;
		color: var(--color-text-hint);
		font: inherit;
		font-size: var(--text-sm);
		font-weight: 500;
		white-space: nowrap;
		cursor: pointer;
		transition:
			background-color var(--speed-fast),
			color var(--speed-fast);
	}

	button:hover {
		background: color-mix(in srgb, var(--color-text) 14%, var(--color-secondary));
		color: var(--color-text);
	}

	button.selected,
	button.selected:hover {
		background: var(--color-brand);
		color: var(--color-brand-text);
		font-weight: 600;
	}

	button.selected:hover {
		background: var(--color-brand-hover);
	}

	.rule {
		align-self: stretch;
		width: 1px;
		margin: 7px var(--space-1);
		background: var(--color-text-disabled);
	}

	button:focus-visible {
		outline: 2px solid var(--color-text);
		outline-offset: -2px;
	}

	@media (max-width: 40rem) {
		.segmented {
			overflow-x: auto;
			scrollbar-width: none;
		}

		.segmented::-webkit-scrollbar {
			display: none;
		}
	}

	button:disabled {
		cursor: not-allowed;
		opacity: 0.5;
	}
</style>
