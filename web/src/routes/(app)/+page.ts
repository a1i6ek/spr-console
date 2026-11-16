import { factorsApi, metricsApi, productsApi } from '#lib/api/index.js';
import { fromApi } from '#lib/api/load.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [factors, metrics, products] = await Promise.all([
		fromApi(factorsApi.search({}, fetch)),
		fromApi(metricsApi.list(fetch)),
		fromApi(productsApi.list(fetch))
	]);
	return { factors, metrics, products };
};
