import { metricsApi } from '#lib/api/index.js';
import { fromApi } from '#lib/api/load.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => ({
	metrics: await fromApi(metricsApi.list(fetch))
});
