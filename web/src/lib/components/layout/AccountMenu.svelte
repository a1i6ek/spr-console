<script lang="ts">
	import { goto, refreshAll } from '$app/navigation';
	import { resolve } from '$app/paths';
	import {
		ConfirmDialog,
		DropdownMenu,
		MenuItem,
		MenuSeparator,
		Thumb
	} from '#lib/components/ui/index.js';
	import { initials } from '#lib/utils/format.js';

	type Props = { username: string };

	let { username }: Props = $props();

	let signingOut = $state(false);
	let confirmingSignOut = $state(false);

	const monogram = $derived(initials(username));

	async function signOut() {
		signingOut = true;
		try {
			// clears the session cookies; the answer is a redirect to the sign-in page
			await fetch(resolve('/logout'), { method: 'POST' });
		} finally {
			// what was loaded with the session is not for whoever signs in next
			await refreshAll();
			await goto(resolve('/login'), { replace: true });
		}
	}
</script>

<DropdownMenu label="Аккаунт: {username}" shape="labelled" width="16rem">
	{#snippet trigger()}
		<Thumb text={monogram} size="xs" shape="circle" tone="accent" />
		<span class="name">{username}</span>
		<span class="chevron" aria-hidden="true"
			><i class="ri-arrow-down-s-line" aria-hidden="true"></i></span
		>
	{/snippet}

	<div class="card">
		<Thumb text={monogram} size="sm" shape="circle" tone="accent" />
		<div class="who">
			<span class="full-name" title={username}>{username}</span>
		</div>
	</div>

	<MenuSeparator />

	<MenuItem
		value="sign-out"
		icon="ri-logout-box-r-line"
		label="Выйти"
		danger
		onSelect={() => (confirmingSignOut = true)}
	/>
</DropdownMenu>

<ConfirmDialog
	bind:open={confirmingSignOut}
	title="Выйти из консоли?"
	description="Чтобы продолжить работу, нужно будет войти снова."
	tone="danger"
	icon="ri-logout-box-r-line"
	confirmLabel="Выйти"
	busy={signingOut}
	onConfirm={signOut}
/>

<style>
	/* Two columns: the avatar, then everything about the person, so the
	   name, address and roles start on one line. */
	.card {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: var(--space-2) 10px;
		padding: 10px;
	}

	.who {
		display: flex;
		flex-direction: column;
		min-width: 0;
		line-height: 1.3;
	}

	.full-name,
	.full-name {
		font-weight: 600;
	}

	.name {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.chevron {
		display: inline-flex;
		flex: none;
		color: var(--color-text-hint);
		transition: transform var(--speed);
	}

	:global(.dropdown-trigger[data-state='open']) .chevron {
		transform: rotate(180deg);
	}

	@media (max-width: 64rem) {
		.name,
		.chevron {
			display: none;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.chevron {
			transition: none;
		}
	}
</style>
