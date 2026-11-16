import { error, fail, type RequestEvent } from '@sveltejs/kit';
import { ApiError } from '#lib/api/index.js';

/** What a refused action answers: the API's own problem, for the form to show. */
export type ActionProblem = {
	error: { status: number; message: string; errors: string[] };
};

/**
 * Runs a form action (vard's act): reads the form's `input` (what the page holds, as JSON; see
 * `#lib/forms.ts`) from a form posted on this origin only, passes it to `call`, which asks the API
 * through the typed client, and answers with what the API answered. A refusal is answered as the
 * API gave it, with its status.
 */
export async function act<I, T>(event: RequestEvent, call: (input: I) => Promise<T>) {
	// SvelteKit checks this too, but not under its dev server
	if (event.request.headers.get('origin') !== event.url.origin) {
		error(403, 'Cross-site form submissions are forbidden');
	}

	const form = await event.request.formData();

	let input: I;
	try {
		input = JSON.parse(String(form.get('input') ?? 'null')) as I;
	} catch {
		return fail(400, problem(new ApiError(400, 'Форму не удалось прочитать.')));
	}

	try {
		// undefined is not an action result: a deleted stop factor answers { ok: true }
		return (await call(input)) ?? { ok: true };
	} catch (err) {
		if (err instanceof ApiError) return fail(err.status || 502, problem(err));
		throw err;
	}
}

function problem(err: ApiError): ActionProblem {
	return { error: { status: err.status, message: err.message, errors: err.errors } };
}

/**
 * A form action that calls one method of the typed client, with the arguments the form sends as
 * its input, in order: `setStatus: action(factorsApi.setStatus)` answers a form whose input is
 * `[factorId, active, changeReason]`. The path and the answer stay written once, in the client.
 */
export function action<F extends (...args: never[]) => Promise<unknown>>(call: F) {
	return (event: RequestEvent) => act(event, (args: Parameters<F>) => call(...args));
}
