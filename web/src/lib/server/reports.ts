/**
 * The console API's report endpoints (ReportController): one per report, with the form's fields. The API checks them
 * again, calls SPR and answers the file, or a ProblemDetail with the message to show.
 */
import type { ReportInput, ReportSlug } from '#lib/reports.js';

const MINUTE = 60_000;

export interface ReportRequest {
	path: string;
	/** The general report is built for minutes; the API waits up to 10 of them. */
	timeoutMs: number;
}

export function reportRequest(
	slug: Exclude<ReportSlug, 'hard-collection'>,
	input: ReportInput
): ReportRequest {
	const base = '/api/v1/console/reports';
	switch (slug) {
		case 'file':
			return { path: `${base}/files/${input.scoringId}`, timeoutMs: 3 * MINUTE };
		case 'archive':
			return {
				path: `${base}/files?${new URLSearchParams({ from: input.from!, to: input.to! })}`,
				timeoutMs: 6 * MINUTE
			};
		case 'general':
			return {
				path: `${base}/general?${new URLSearchParams({ month: input.month! })}`,
				timeoutMs: 11 * MINUTE
			};
		case 'inn':
			return { path: `${base}/inn/${input.inn}`, timeoutMs: 6 * MINUTE };
	}
}
