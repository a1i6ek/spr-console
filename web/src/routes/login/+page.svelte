<script lang="ts">
	import { enhance } from '$app/forms';
	import { pageTitle } from '#lib/brand.js';
	import GateShell from '#lib/components/layout/GateShell.svelte';
	import { Alert, Button, Input, PasswordInput } from '#lib/components/ui/index.js';
	import type { PageProps } from './$types';

	let { form }: PageProps = $props();

	// keeps what was typed after a refused sign-in
	// svelte-ignore state_referenced_locally
	let username = $state(form?.username ?? '');
	let password = $state('');
	let submitting = $state(false);

	const canSubmit = $derived(username.trim() !== '' && password !== '' && !submitting);
</script>

<svelte:head>
	<title>{pageTitle('Вход')}</title>
</svelte:head>

<GateShell title="Вход в консоль" description="Стоп-факторы, отчеты и проверки заявок SPR.">
	<form
		method="POST"
		use:enhance={({ cancel }) => {
			if (!canSubmit) return cancel();
			submitting = true;
			return async ({ update }) => {
				await update({ reset: false });
				password = '';
				submitting = false;
			};
		}}
	>
		{#if form?.message}<Alert>{form.message}</Alert>{/if}
		<Input label="Логин" name="username" bind:value={username} autocomplete="username" required />
		<PasswordInput label="Пароль" name="password" bind:value={password} required />
		<Button type="submit" size="lg" loading={submitting} disabled={!canSubmit}>Войти</Button>
	</form>
</GateShell>

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}
</style>
