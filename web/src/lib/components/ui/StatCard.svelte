<script lang="ts">
	import type { IconName } from './icon';
	import type { ColorPalette } from './control';

	type Props = {
		label: string;
		value: number;
		icon: IconName;
		note?: string;
		tone?: ColorPalette;
		href?: string;
	};

	let { label, value, icon, note, tone = 'neutral', href }: Props = $props();
</script>

<svelte:element this={href ? 'a' : 'div'} class="stat" class:link={href} {href} data-tone={tone}>
	<span class="head">
		<i class={icon} style:font-size="15px" aria-hidden="true"></i>
		<span class="label">{label}</span>
		{#if href}
			<span class="go" aria-hidden="true"
				><i class="ri-arrow-right-s-line" style:font-size="13px" aria-hidden="true"></i></span
			>
		{/if}
	</span>

	<span class="figure">
		<strong>{value.toLocaleString()}</strong>
		{#if note}<span class="note">{note}</span>{/if}
	</span>
</svelte:element>

<style>
	.stat {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-secondary-alt);
		border-radius: var(--radius-surface);
		background: var(--color-surface);
		color: var(--color-text);
		text-decoration: none;
		transition:
			background-color var(--speed),
			border-color var(--speed);
	}

	.link:hover,
	.link:focus-visible {
		outline: 0;
		border-color: var(--color-border);
		background: var(--color-surface-alt);
	}

	.head {
		display: flex;
		align-items: center;
		gap: 5px;
		min-width: 0;
		color: var(--color-text-hint);
	}

	.label {
		overflow: hidden;
		font-size: var(--text-sm);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.go {
		margin-left: auto;
		color: var(--color-text-disabled);
		transition: color var(--speed);
	}

	.link:hover .go {
		color: var(--color-text);
	}

	.figure {
		display: flex;
		align-items: baseline;
		gap: var(--space-2);
		min-width: 0;
	}

	strong {
		flex: none;
		font-size: var(--text-xl);
		font-weight: 600;
		line-height: 1.25;
		font-variant-numeric: tabular-nums;
	}

	.note {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		color: var(--color-text-hint);
		font-size: var(--text-xs);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.stat[data-tone='success'] .note {
		color: var(--color-success);
	}

	.stat[data-tone='warning'] .note {
		color: var(--color-warning);
	}

	.stat[data-tone='danger'] .note {
		color: var(--color-danger);
	}

	.stat[data-tone='info'] .note {
		color: var(--color-info);
	}

	@media (max-width: 34rem) {
		.stat {
			padding: var(--space-2);
		}
	}
</style>
