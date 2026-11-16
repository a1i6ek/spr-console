import { factorsApi, metricsApi, productsApi } from '#lib/api/index.js';
import { fromApi } from '#lib/api/load.js';
import type { PageLoad } from './$types';

// Every stop factor at once (a few dozen): the page filters them as you type. The load does not read the URL, so
// changing a filter does not load them again.
export const load: PageLoad = async ({ fetch }) => {
	const [factors, metrics, products] = await Promise.all([
		fromApi(factorsApi.search({}, fetch)),
		fromApi(metricsApi.list(fetch)),
		fromApi(productsApi.list(fetch))
	]);
	return { factors, metrics, products };
};
