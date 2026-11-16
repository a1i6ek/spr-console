<script lang="ts">
	import type { Condition } from '#lib/api/index.js';
	import { Tag } from '#lib/components/ui/index.js';
	import { results, threshold } from '#lib/labels.js';

	let { conditions }: { conditions: Condition[] } = $props();
</script>

<span class="tags">
	{#each conditions as condition (condition.result)}
		<Tag tone={condition.result === 'REJECTED' ? 'danger' : 'warning'} small>
			{results[condition.result]}
			{threshold(condition)}
		</Tag>
	{:else}
		<span class="muted">Без порогов</span>
	{/each}
</span>

<style>
	.tags {
		display: inline-flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.muted {
		color: var(--color-text-hint);
		font-size: var(--text-sm);
	}
</style>
