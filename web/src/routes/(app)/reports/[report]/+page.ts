import { error } from '@sveltejs/kit';
import { productsApi } from '#lib/api/index.js';
import { fromApi } from '#lib/api/load.js';
import { isReportSlug, reports } from '#lib/reports.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
	if (!isReportSlug(params.report)) error(404, 'Отчет не найден');

	const report = reports[params.report];
	const fields: readonly string[] = report.fields;
	const products = fields.includes('products') ? await fromApi(productsApi.list(fetch)) : [];
	return { report, products };
};
