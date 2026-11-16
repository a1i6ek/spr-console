import { fail, redirect } from '@sveltejs/kit';
import { login } from '#lib/server/api.js';
import { saveSession } from '#lib/server/session.js';
import type { Actions } from './$types';

export const actions = {
	default: async ({ request, cookies, url }) => {
		const form = await request.formData();
		const username = String(form.get('username') ?? '').trim();
		const password = String(form.get('password') ?? '');

		if (!username || !password) {
			return fail(400, { username, message: 'Введите логин и пароль' });
		}

		const result = await login(username, password);
		if (!result.ok) return fail(result.status, { username, message: result.message });

		saveSession(cookies, result.user);

		// only a path of this app, never another site
		const redirectTo = url.searchParams.get('redirectTo');
		redirect(303, redirectTo?.startsWith('/') && !redirectTo.startsWith('//') ? redirectTo : '/');
	}
} satisfies Actions;
