<script lang="ts">
	import type { Snippet } from 'svelte';
	import { Menu } from '@ark-ui/svelte/menu';
	import { Portal } from '@ark-ui/svelte/portal';
	import { Tooltip } from '@ark-ui/svelte/tooltip';
	import { mergeProps } from '@ark-ui/svelte/utils';

	type Props = {
		label: string;
		trigger: Snippet;
		children: Snippet;
		shape?: 'icon' | 'avatar' | 'labelled';
		width?: string;
	};

	let { label, trigger, children, shape = 'icon', width = '17rem' }: Props = $props();

	const uid = $props.id();
	const id = `${uid}-trigger`;
</script>

<Menu.Root positioning={{ placement: 'bottom-end', gutter: 6 }} ids={{ trigger: id }}>
	<Tooltip.Root
		disabled={shape === 'labelled'}
		openDelay={250}
		closeDelay={80}
		positioning={{ placement: 'bottom', gutter: 6, strategy: 'fixed' }}
		ids={{ trigger: id }}
	>
		<Tooltip.Trigger>
			{#snippet asChild(tooltip)}
				<Menu.Trigger>
					{#snippet asChild(menu)}
						<!-- The menu's props last, so its open state is the one the
						     button says it is in. -->
						<button
							{...mergeProps(tooltip(), menu())}
							type="button"
							class="control dropdown-trigger"
							data-icon={shape === 'labelled' ? undefined : 'true'}
							data-size="sm"
							data-variant="ghost"
							data-palette="neutral"
							data-shape={shape}
							aria-label={label}
						>
							{@render trigger()}
						</button>
					{/snippet}
				</Menu.Trigger>
			{/snippet}
		</Tooltip.Trigger>

		<Portal>
			<Tooltip.Positioner>
				<Tooltip.Content>{label}</Tooltip.Content>
			</Tooltip.Positioner>
		</Portal>
	</Tooltip.Root>

	<Portal>
		<Menu.Positioner>
			<Menu.Content class="dropdown-menu" style="--menu-width: {width}">
				{@render children()}
			</Menu.Content>
		</Menu.Positioner>
	</Portal>
</Menu.Root>

<style>
	:global(.dropdown-trigger.control[data-state='open']) {
		background: var(--palette-subtle);
	}

	:global(.dropdown-trigger.control[data-shape='labelled']) {
		gap: var(--space-2);
		max-width: 16rem;
		padding: 0 var(--space-2) 0 3px;
		border-radius: var(--radius-pill);
		color: var(--color-text);
		font-weight: 500;
	}

	@media (max-width: 64rem) {
		:global(.dropdown-trigger.control[data-shape='labelled']) {
			width: var(--control-height-sm);
			padding: 0;
			gap: 0;
		}
	}

	:global(.dropdown-menu) {
		width: min(var(--menu-width), calc(100vw - var(--space-4)));
	}
</style>
