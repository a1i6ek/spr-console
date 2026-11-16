import { redirect } from '@sveltejs/kit';
import { getRequestEvent } from '$app/server';
import type { Handle } from '@sveltejs/kit/hooks';
import { useRequestFetch } from '#lib/api/client.js';
import { isApiPath, toApi } from '#lib/server/api.js';
import { readSession } from '#lib/server/session.js';

// A form action calls the console API with the request's own fetch, so the call carries the
// user's cookies and comes back through toApi below, with the session.
useRequestFetch(() => {
	try {
		return getRequestEvent().fetch;
	} catch {
		return undefined;
	}
});

/** Pages and endpoints open without a session. */
const PUBLIC_PATHS = ['/login', '/api/auth/login'];

export const handle: Handle = async ({ event, resolve }) => {
	event.locals.user = readSession(event.cookies);

	if (isApiPath(event.url.pathname)) return toApi(event);

	const isPublic = PUBLIC_PATHS.includes(event.url.pathname);
	if (!event.locals.user && !isPublic) {
		const target = event.url.pathname + event.url.search;
		redirect(303, target === '/' ? '/login' : `/login?redirectTo=${encodeURIComponent(target)}`);
	}
	if (event.locals.user && event.url.pathname === '/login') redirect(303, '/');

	return resolve(event);
};
