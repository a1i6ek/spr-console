import { error } from '@sveltejs/kit';
import { API_URL } from '$app/env/private';
import { basicAuthorization, problemOf } from '#lib/server/api.js';
import { reportRequest } from '#lib/server/reports.js';
import { clearSession } from '#lib/server/session.js';
import { isReportSlug, validate, type ReportInput } from '#lib/reports.js';
import type { RequestHandler } from './$types';

const fail = (status: number, message: string) => Response.json({ message }, { status });

/**
 * Builds a report: checks the form, asks the console API's report endpoint with the user's session and passes the
 * file on as it comes (streamed, not held here). Any other answer comes back as `{ message }`; a 401 ends the
 * session.
 */
export const POST: RequestHandler = async ({ params, request, locals, cookies }) => {
	if (!isReportSlug(params.report)) error(404, 'Отчет не найден');
	if (!locals.user) return fail(401, 'Сессия истекла. Войдите снова.');

	const input = (await request.json().catch(() => ({}))) as ReportInput;
	const problem = validate(params.report, input);
	if (problem || params.report === 'hard-collection')
		return fail(400, problem ?? 'Отчет недоступен');

	const { path, timeoutMs } = reportRequest(params.report, input);

	let answer: Response;
	try {
		answer = await fetch(new URL(path, API_URL), {
			headers: { authorization: basicAuthorization(locals.user), accept: '*/*' },
			signal: AbortSignal.any([request.signal, AbortSignal.timeout(timeoutMs)])
		});
	} catch (cause) {
		const timedOut = cause instanceof DOMException && cause.name === 'TimeoutError';
		return timedOut
			? fail(504, 'Сервис не ответил вовремя. Попробуйте позже.')
			: fail(502, 'Нет связи с сервисом отчетов.');
	}

	if (answer.status === 401) {
		clearSession(cookies);
		return fail(401, 'Сессия истекла. Войдите снова.');
	}
	if (!answer.ok) {
		return fail(answer.status, await problemOf(answer, `Ошибка ${answer.status}`));
	}

	return new Response(answer.body, {
		headers: {
			'content-type': answer.headers.get('content-type') ?? 'application/octet-stream',
			'content-disposition': answer.headers.get('content-disposition') ?? 'attachment',
			'cache-control': 'no-store'
		}
	});
};
