import type { RequestEvent } from '@sveltejs/kit';
import { API_URL } from '$app/env/private';
import { clearSession } from './session';

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
