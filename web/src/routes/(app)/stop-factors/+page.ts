import { factorsApi, metricsApi, productsApi, type FactorFilter } from '#lib/api/index.js';
import { fromApi } from '#lib/api/load.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
	const filter: Required<FactorFilter> = {
		search: url.searchParams.get('search') ?? '',
		metricId: url.searchParams.get('metricId') ?? '',
		productId: url.searchParams.get('productId') ?? '',
		segment: url.searchParams.get('segment') ?? '',
		active: url.searchParams.get('active') ?? ''
	};
	const [factors, metrics, products] = await Promise.all([
		fromApi(factorsApi.search(filter, fetch)),
		fromApi(metricsApi.list(fetch)),
		fromApi(productsApi.list(fetch))
	]);
	return { factors, metrics, products, filter };
};
