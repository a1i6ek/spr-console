import { error, redirect } from '@sveltejs/kit';
import { resolve } from '$app/paths';
import { ApiError } from './client';

/**
 * Awaits an API call made by a load, and turns its failure into what SvelteKit shows instead of
 * the page: the sign-in page when the session is gone, an error page with the API's own sentence
 * otherwise.
 */
export async function fromApi<T>(call: Promise<T>): Promise<T> {
	try {
		return await call;
	} catch (err) {
		if (!(err instanceof ApiError)) throw err;

		if (err.status === 401) redirect(307, resolve('/login'));
		if (err.status === 0) error(503, 'Сервер консоли недоступен.');
		error(err.status, err.message);
	}
}
