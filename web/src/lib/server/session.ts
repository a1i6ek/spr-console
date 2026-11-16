import type { Cookies } from '@sveltejs/kit';
import { COOKIES } from '#lib/brand.js';

export interface User {
	username: string;
	session: string;
	/** What the user may do in the console, as the login answered it. */
	role: string;
}

/** A session from before the login answered a role. */
const DEFAULT_ROLE = 'Администратор консоли';

const MAX_AGE = 60 * 60 * 8; // 8 hours

const options = { path: '/', httpOnly: true, sameSite: 'lax', maxAge: MAX_AGE } as const;

export function readSession(cookies: Cookies): User | null {
	const session = cookies.get(COOKIES.session);
	const username = cookies.get(COOKIES.username);
	return session && username
		? { session, username, role: cookies.get(COOKIES.role) || DEFAULT_ROLE }
		: null;
}

export function saveSession(cookies: Cookies, user: User) {
	cookies.set(COOKIES.session, user.session, options);
	cookies.set(COOKIES.username, user.username, options);
	cookies.set(COOKIES.role, user.role || DEFAULT_ROLE, options);
}

export function clearSession(cookies: Cookies) {
	for (const name of [COOKIES.session, COOKIES.username, COOKIES.role])
		cookies.delete(name, { path: '/' });
}
