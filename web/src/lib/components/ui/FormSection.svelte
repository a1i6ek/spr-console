<script lang="ts">
	import type { IconName } from './icon';
	import type { Snippet } from 'svelte';
	import Thumb from './Thumb.svelte';

	type Props = {
		title: string;
		description?: string;
		icon?: IconName;
		meta?: Snippet;
		action?: Snippet;
		children: Snippet;
	};

	let { title, description, icon, meta, action, children }: Props = $props();
</script>

<section class="form-section">
	<div class="layout">
		<header>
			<div class="heading">
				{#if icon}
					<span class="icon" aria-hidden="true"><Thumb {icon} size="sm" /></span>
				{/if}

				<div class="text">
					<h3>{title}</h3>
					{#if description}
						<p>{description}</p>
					{/if}
					{#if meta}
						<div class="meta">{@render meta()}</div>
					{/if}
				</div>
			</div>

			{#if action}
				<div class="action">{@render action()}</div>
			{/if}
		</header>

		<div class="content">
			{@render children()}
		</div>
	</div>
</section>

<style>
	.form-section {
		container-type: inline-size;
	}

	.form-section + :global(.form-section) {
		margin-top: var(--space-5);
	}

	:global(.form-section) + .form-section > .layout {
		padding-top: var(--space-5);
		border-top: 1px solid var(--color-border);
	}

	.layout {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	header {
		--icon-width: 34px;

		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-3);
	}

	.heading {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
		min-width: 0;
	}

	.icon {
		display: flex;
		flex: none;
	}

	.icon + .text {
		padding-top: 7px;
	}

	.text {
		min-width: 0;
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
		margin-top: var(--space-2);
	}

	h3 {
		margin: 0;
		color: var(--color-text);
		font-size: var(--text-base);
		font-weight: 600;
	}

	p {
		margin: 2px 0 0;
		color: var(--color-text-hint);
		font-size: var(--text-sm);
		line-height: 1.45;
	}

	.action {
		display: flex;
		flex-shrink: 0;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.content {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		min-width: 0;
	}

	.content > :global(.switch-field:first-child),
	.content > :global(:first-child:not(.switch-field) > .switch-field:first-child) {
		padding-top: 0;
	}

	.content > :global(.switch-field:last-child),
	.content > :global(:last-child:not(.switch-field) > .switch-field:last-child) {
		padding-bottom: 0;
	}

	@container (min-width: 44rem) {
		.layout {
			display: grid;
			grid-template-columns: 14rem minmax(0, 1fr);
			align-items: start;
			gap: var(--space-5);
		}

		:global(.form-section) + .form-section > .layout {
			padding-top: 0;
			border-top: none;
		}

		header {
			flex-direction: column;
			justify-content: flex-start;
			padding-top: var(--space-2);
		}

		.heading:has(.icon) + .action {
			padding-left: calc(var(--icon-width) + var(--space-3));
		}

		.content {
			padding: var(--space-4);
			border: 1px solid var(--color-border);
			border-radius: var(--radius-surface);
			background: var(--color-surface);
		}
	}

	@container (min-width: 60rem) {
		.layout {
			grid-template-columns: 17rem minmax(0, 1fr);
		}
	}
</style>
