import { error } from '@sveltejs/kit';
import { factorsApi, metricsApi, productsApi } from '#lib/api/index.js';
import { fromApi } from '#lib/api/load.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
	const factorId = Number(params.factorId);
	if (!Number.isInteger(factorId)) error(404, 'Стоп-фактор не найден');

	const [factor, versions, metrics, products] = await Promise.all([
		fromApi(factorsApi.get(factorId, fetch)),
		fromApi(factorsApi.versions(factorId, fetch)),
		fromApi(metricsApi.list(fetch)),
		fromApi(productsApi.list(fetch))
	]);
	return { factor, versions, metrics, products };
};
