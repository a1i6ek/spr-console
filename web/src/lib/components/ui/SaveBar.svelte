<script lang="ts">
	import Button from './Button.svelte';

	type Props = {
		saving?: boolean;
		ready?: boolean;
		ondiscard: () => void;
	};

	let { saving = false, ready = true, ondiscard }: Props = $props();
</script>

<div class="save-bar" role="region" aria-label="Несохранённые изменения">
	<span class="label"><span class="dot" aria-hidden="true"></span>Несохранённые изменения</span>

	<Button variant="subtle" size="sm" onclick={ondiscard} disabled={saving}>Отменить</Button>
	<Button type="submit" size="sm" loading={saving} disabled={saving || !ready}>
		{saving ? 'Сохранение…' : 'Сохранить'}
	</Button>
</div>

<style>
	.save-bar {
		position: sticky;
		bottom: var(--space-3);
		z-index: 5;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-2) var(--space-2) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
		box-shadow: var(--shadow-md);
		animation: save-bar-in var(--speed) ease-out;
	}

	.label {
		display: inline-flex;
		flex: 1;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}

	.dot {
		flex: none;
		width: 8px;
		height: 8px;
		border-radius: var(--radius-pill);
		background: var(--color-warning);
	}

	@keyframes save-bar-in {
		from {
			opacity: 0;
			transform: translateY(8px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.save-bar {
			animation: none;
		}
	}
</style>
