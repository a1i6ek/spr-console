import { error } from '@sveltejs/kit';
import { isReportSlug, reports, type Report } from '#lib/reports.js';
import type { PageLoad } from './$types';

export const load: PageLoad = ({ params }) => {
	if (!isReportSlug(params.report)) error(404, 'Отчет не найден');
	const report: Report = reports[params.report];
	return { slug: params.report, report };
};
