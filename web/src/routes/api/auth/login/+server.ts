import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

/** Dummy login: any non-empty username and password get a new session, as an administrator of the console. */
export const POST: RequestHandler = async ({ request }) => {
	const { username, password } = await request.json().catch(() => ({}));

	if (
		typeof username !== 'string' ||
		!username.trim() ||
		typeof password !== 'string' ||
		!password
	) {
		return json({ message: 'Неверный логин или пароль' }, { status: 401 });
	}

	return json({
		username: username.trim(),
		session: crypto.randomUUID(),
		role: 'Администратор консоли'
	});
};
