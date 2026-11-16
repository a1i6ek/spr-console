<script lang="ts">
	import type { IconName } from './icon';

	type Props = { label: string; icon?: IconName; required?: boolean };

	let { label, icon, required = false }: Props = $props();
</script>

{#if icon}<i class={icon} style:font-size="1em" aria-hidden="true"
	></i>{/if}{label}{#if required}<span data-part="required-indicator" aria-hidden="true"
		>&thinsp;*</span
	>{/if}
