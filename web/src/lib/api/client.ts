/**
 * The typed client for the console API (vard's api/client.ts). The browser and the server call it
 * on this app's own origin, /api/v1/console: hooks.server.ts passes those paths on to the Spring API
 * with the session, so the same call works in a load, in a form action and in the browser.
 */

export class ApiError extends Error {
	constructor(
		readonly status: number,
		message: string,
		/** Spring's validation errors, one sentence each. */
		readonly errors: string[] = []
	) {
		super(message);
		this.name = 'ApiError';
	}

	get isUnauthorized(): boolean {
		return this.status === 401;
	}
}

/** The fetch SvelteKit hands a load function. Passing it in is what makes `invalidate` work. */
export type Fetch = typeof globalThis.fetch;

export type Query = Record<string, string | number | boolean | null | undefined>;

type RequestOptions = {
	method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
	body?: unknown;
	query?: Query;
	fetch?: Fetch;
};

/** On the server, the fetch of the request being answered; see `useRequestFetch`. */
let requestFetch: (() => Fetch | undefined) | undefined;

/**
 * Has calls made on the server go through `get()`: a form action's call then passes through this
 * app's own hook, with the caller's cookies, as the browser's would. Set once, by hooks.server.ts.
 */
export function useRequestFetch(get: () => Fetch | undefined) {
	requestFetch = get;
}

/** A console API path on this app's own origin. */
export function apiPath(path: string, query: Query = {}): string {
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(query)) {
		if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
	}
	const search = params.toString();
	return `/api/v1/console${path}${search ? `?${search}` : ''}`;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
	const {
		method = 'GET',
		body,
		query,
		fetch: fetcher = requestFetch?.() ?? globalThis.fetch
	} = options;

	let response: Response;
	try {
		response = await fetcher(apiPath(path, query), {
			method,
			credentials: 'same-origin',
			headers: { accept: 'application/json', 'content-type': 'application/json' },
			body: body === undefined ? undefined : JSON.stringify(body)
		});
	} catch {
		throw new ApiError(0, 'Сервер консоли недоступен.');
	}

	const payload: unknown = response.status === 204 ? {} : await response.json().catch(() => ({}));

	if (!response.ok) throw failureOf(response.status, payload);

	return payload as T;
}

/**
 * The API's answer to a failed call: Spring's ProblemDetail (detail, and errors for validation),
 * read only as far as it has that shape, so a proxy's error page gives the generic sentence.
 */
export function failureOf(status: number, payload: unknown): ApiError {
	const body = isRecord(payload) ? payload : {};
	const message =
		typeof body.detail === 'string'
			? body.detail
			: typeof body.title === 'string'
				? body.title
				: 'Что-то пошло не так.';
	const errors = Array.isArray(body.errors)
		? body.errors.filter((e): e is string => typeof e === 'string')
		: [];
	return new ApiError(status, message, errors);
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export const api = {
	get: <T>(path: string, query?: Query, fetcher?: Fetch) =>
		request<T>(path, { query, fetch: fetcher }),
	post: <T>(path: string, body?: unknown, query?: Query, fetcher?: Fetch) =>
		request<T>(path, { method: 'POST', body, query, fetch: fetcher }),
	put: <T>(path: string, body?: unknown, query?: Query, fetcher?: Fetch) =>
		request<T>(path, { method: 'PUT', body, query, fetch: fetcher }),
	patch: <T>(path: string, query?: Query, fetcher?: Fetch) =>
		request<T>(path, { method: 'PATCH', query, fetch: fetcher }),
	delete: <T>(path: string, query?: Query, fetcher?: Fetch) =>
		request<T>(path, { method: 'DELETE', query, fetch: fetcher })
};
