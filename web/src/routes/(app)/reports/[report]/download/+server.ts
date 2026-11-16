import { error } from '@sveltejs/kit';
import { clearSession } from '#lib/server/session.js';
import { authorization, upstreamOf } from '#lib/server/reports.js';
import { isReportSlug, validate, type ReportInput } from '#lib/reports.js';
import type { RequestHandler } from './$types';

const fail = (status: number, message: string) => Response.json({ message }, { status });

/**
 * Builds a report: checks the form, asks the SPR service with the user's session and passes the file on as it comes
 * (streamed, not held here). Any other answer comes back as `{ message }`; a 401 ends the session.
 */
export const POST: RequestHandler = async ({ params, request, locals, cookies }) => {
	if (!isReportSlug(params.report)) error(404, 'Отчет не найден');
	if (!locals.user) return fail(401, 'Сессия истекла. Войдите снова.');

	const input = (await request.json().catch(() => ({}))) as ReportInput;
	const problem = validate(params.report, input);
	if (problem || params.report === 'hard-collection')
		return fail(400, problem ?? 'Отчет недоступен');

	const upstream = upstreamOf(params.report, input);

	let answer: Response;
	try {
		answer = await fetch(upstream.url, {
			method: upstream.method,
			headers: {
				authorization: authorization(locals.user.username, locals.user.session),
				accept: '*/*'
			},
			signal: AbortSignal.any([request.signal, AbortSignal.timeout(upstream.timeoutMs)])
		});
	} catch (cause) {
		const timedOut = cause instanceof DOMException && cause.name === 'TimeoutError';
		const { status, message } = upstream.unreachable(
			timedOut ? 'сервис не ответил вовремя' : 'нет связи с сервисом'
		);
		return fail(timedOut ? 504 : status, message);
	}

	if (answer.status === 401) {
		clearSession(cookies);
		return fail(401, 'Сессия истекла. Войдите снова.');
	}
	if (!answer.ok) {
		const body = (await answer.text().catch(() => '')).trim();
		return fail(answer.status, upstream.error(answer.status, answer.statusText, body));
	}

	const disposition = upstream.alwaysFileName ? null : answer.headers.get('content-disposition');
	return new Response(answer.body, {
		headers: {
			'content-type':
				upstream.contentType ?? answer.headers.get('content-type') ?? 'application/octet-stream',
			'content-disposition': disposition ?? `attachment; filename="${upstream.fileName}"`,
			'cache-control': 'no-store'
		}
	});
};
