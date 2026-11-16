import type { RequestEvent } from '@sveltejs/kit';
import { API_URL } from '$app/env/private';
import { clearSession, type User } from './session';

/**
 * The paths the console API answers (vard's server/api.ts). The browser calls them on this app's
 * own origin, so nothing is cross-origin; this server passes them on to Spring with the session.
 */
const API_PATHS = ['/api/v1/console'];

// Headers about one connection rather than the request, which a proxy does not pass on.
const HOP_BY_HOP = [
	'connection',
	'keep-alive',
	'host',
	'te',
	'trailer',
	'transfer-encoding',
	'upgrade'
];

const API_TIMEOUT_MS = 2 * 60 * 1000;

/** Whole segments only: `/api/v1/console` takes `/api/v1/console/metrics`, not `/api/v1/consoles`. */
export function isApiPath(pathname: string): boolean {
	return API_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

/**
 * Passes a request to the API and its answer back: the browser's own requests, and the ones a load
 * or an action makes while answering, which SvelteKit sends through the same hook. Without a
 * session the API is not asked; a 401 from it ends the session here too (its cookies are cleared).
 */
export async function toApi(event: RequestEvent): Promise<Response> {
	const { request, url, locals } = event;

	if (!locals.user) return unauthorized();

	const headers = new Headers(request.headers);
	for (const name of HOP_BY_HOP) headers.delete(name);
	headers.delete('cookie');
	headers.set('authorization', basicAuthorization(locals.user));
	headers.set('x-session', locals.user.session);
	headers.set('x-forwarded-for', event.getClientAddress());

	const body =
		request.method === 'GET' || request.method === 'HEAD' ? undefined : await request.arrayBuffer();

	let answer: Response;
	try {
		answer = await fetch(new URL(url.pathname + url.search, API_URL), {
			method: request.method,
			headers,
			body,
			redirect: 'manual',
			signal: AbortSignal.any([request.signal, AbortSignal.timeout(API_TIMEOUT_MS)])
		});
	} catch (error) {
		const timedOut = error instanceof DOMException && error.name === 'TimeoutError';
		return new Response(null, { status: timedOut ? 504 : 502 });
	}

	if (answer.status === 401) {
		clearSession(event.cookies);
		return unauthorized();
	}

	// fetch has already decoded the body, so the encoding and length it came with no longer describe it
	const out = new Headers(answer.headers);
	out.delete('content-encoding');
	out.delete('content-length');

	return new Response(answer.body, {
		status: answer.status,
		statusText: answer.statusText,
		headers: out
	});
}

function unauthorized(): Response {
	return Response.json(
		{ title: 'Unauthorized', detail: 'Сессия закончилась. Войдите снова.' },
		{ status: 401 }
	);
}

/** The header the console API and SPR take: Basic, with the session as the password. */
export function basicAuthorization(user: Pick<User, 'username' | 'session'>): string {
	return `Basic ${Buffer.from(`${user.username}:${user.session}`, 'utf8').toString('base64')}`;
}

/** The message of a ProblemDetail (or `{ message }`) answer, the fallback otherwise. */
export async function problemOf(answer: Response, fallback: string): Promise<string> {
	const data = (await answer.json().catch(() => null)) as {
		detail?: string;
		message?: string;
	} | null;
	return data?.detail || data?.message || fallback;
}

type LoginResult =
	{ ok: true; user: User } | { ok: false; status: 400 | 401 | 502; message: string };

/** POST /api/v1/console/auth/login: the username and the session for these credentials. */
export async function login(username: string, password: string): Promise<LoginResult> {
	let answer: Response;
	try {
		answer = await fetch(new URL('/api/v1/console/auth/login', API_URL), {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ username, password }),
			signal: AbortSignal.timeout(30_000)
		});
	} catch {
		return { ok: false, status: 502, message: 'Сервис входа недоступен. Попробуйте позже.' };
	}
	if (!answer.ok) {
		return {
			ok: false,
			status: answer.status === 400 ? 400 : 401,
			message: await problemOf(answer, 'Неверный логин или пароль')
		};
	}
	const { username: name, session } = (await answer.json()) as {
		username: string;
		session: string;
	};
	return { ok: true, user: { username: name, session, role: '' } };
}
