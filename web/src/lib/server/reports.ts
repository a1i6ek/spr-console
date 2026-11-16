/**
 * The SPR endpoints behind the reports, called as the SPR web controller calls them: the same URLs, the user's
 * `Authorization: Basic base64(username:session)`, the same file names when the service sends none, and the same
 * messages for its errors.
 */
import { REPORT_URL, SPR_API_URL } from '$app/env/private';
import { monthRange, type ReportInput, type ReportSlug } from '#lib/reports.js';

export interface Upstream {
	url: URL;
	method: 'GET' | 'POST';
	timeoutMs: number;
	/** Content type of the file; null: the one the service sends. */
	contentType: string | null;
	/** File name when the service sends none (or, for INN, always: as the controller does). */
	fileName: string;
	alwaysFileName?: boolean;
	/** The message for an answer that is not the file. */
	error: (status: number, statusText: string, body: string) => string;
	/** The message when the service cannot be reached. */
	unreachable: (reason: string) => { status: number; message: string };
}

const MINUTE = 60_000;
const ZIP = 'application/zip';
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

const httpError = (status: number, statusText: string) => `Ошибка ${status}: ${statusText}`;

export function upstreamOf(
	slug: Exclude<ReportSlug, 'hard-collection'>,
	input: ReportInput
): Upstream {
	switch (slug) {
		case 'file': {
			const id = input.scoringId!;
			return {
				url: new URL(`/spr/api/v1/file/${id}`, SPR_API_URL),
				method: 'GET',
				timeoutMs: 2 * MINUTE,
				contentType: null,
				fileName: `scoring_${id}`,
				error: (status, statusText) =>
					status === 404
						? `Файл со scoringId ${id} не найден`
						: status === 403
							? 'Недостаточно прав'
							: status < 500
								? httpError(status, statusText)
								: `Не удалось получить файл: ${httpError(status, statusText)}`,
				unreachable: (reason) => ({ status: 502, message: `Не удалось получить файл: ${reason}` })
			};
		}
		case 'archive': {
			const url = new URL('/spr/api/v1/file/all', SPR_API_URL);
			url.searchParams.set('from', input.from!);
			url.searchParams.set('to', input.to!);
			return {
				url,
				method: 'GET',
				timeoutMs: 5 * MINUTE,
				contentType: ZIP,
				fileName: `files--${input.from}--${input.to}.zip`,
				error: (status, statusText, body) =>
					status === 400 && body
						? body
						: status < 500
							? httpError(status, statusText)
							: `Не удалось получить файлы: ${httpError(status, statusText)}`,
				unreachable: (reason) => ({ status: 502, message: `Не удалось получить файлы: ${reason}` })
			};
		}
		case 'general': {
			const { from, to } = monthRange(input.month!);
			const url = new URL('/api/export/excel', REPORT_URL);
			url.searchParams.set('from', from);
			url.searchParams.set('to', to);
			return {
				url,
				method: 'GET',
				// the report service builds a month for minutes: the controller waits up to 10
				timeoutMs: 10 * MINUTE,
				contentType: XLSX,
				fileName: `scoring-report-${input.month}.xlsx`,
				error: (status, statusText) =>
					status < 500
						? httpError(status, statusText)
						: `Не удалось получить отчёт: ${httpError(status, statusText)}`,
				unreachable: (reason) => ({ status: 502, message: `Не удалось получить отчёт: ${reason}` })
			};
		}
		case 'inn': {
			const url = new URL('/spr/api/v1/file/all-by-inn', SPR_API_URL);
			url.searchParams.set('inn', input.inn!);
			return {
				url,
				method: 'POST',
				timeoutMs: 5 * MINUTE,
				contentType: ZIP,
				fileName: `SPR-${input.inn}.zip`,
				alwaysFileName: true,
				// the controller passes the service's own answer on
				error: (status, statusText, body) => body || httpError(status, statusText),
				unreachable: (reason) => ({ status: 503, message: `Сервис недоступен: ${reason}` })
			};
		}
	}
}

/** The header the SPR services take: Basic, with the session as the password. */
export function authorization(username: string, session: string): string {
	return `Basic ${Buffer.from(`${username}:${session}`, 'utf8').toString('base64')}`;
}
