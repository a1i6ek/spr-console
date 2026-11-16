import { fail, redirect } from '@sveltejs/kit';
import { saveSession, type User } from '#lib/server/session.js';
import type { Actions } from './$types';

export const actions = {
	default: async ({ request, fetch, cookies, url }) => {
		const form = await request.formData();
		const username = String(form.get('username') ?? '').trim();
		const password = String(form.get('password') ?? '');

		if (!username || !password) {
			return fail(400, { username, message: 'Введите логин и пароль' });
		}

		const response = await fetch('/api/auth/login', {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ username, password })
		});
		if (!response.ok) {
			return fail(401, { username, message: 'Неверный логин или пароль' });
		}

		saveSession(cookies, (await response.json()) as User);

		// only a path of this app, never another site
		const redirectTo = url.searchParams.get('redirectTo');
		redirect(303, redirectTo?.startsWith('/') && !redirectTo.startsWith('//') ? redirectTo : '/');
	}
} satisfies Actions;
