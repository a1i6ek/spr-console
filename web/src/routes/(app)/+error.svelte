<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { pageTitle } from '#lib/brand.js';
	import { EmptyState, LinkButton } from '#lib/components/ui/index.js';
</script>

<svelte:head>
	<title>{pageTitle(page.status === 404 ? 'Не найдено' : 'Ошибка')}</title>
</svelte:head>

<EmptyState
	icon={page.status === 404 ? 'ri-compass-3-line' : 'ri-error-warning-line'}
	title={page.status === 404 ? 'Страница не найдена' : 'Что-то пошло не так'}
	size="lg"
>
	{page.error?.message}
	{#snippet actions()}
		<LinkButton href={resolve('/(app)/stop-factors')} variant="outline" icon="ri-arrow-left-line"
			>К стоп-факторам</LinkButton
		>
	{/snippet}
</EmptyState>
