import { defineEnvVars } from '@sveltejs/kit/env';

export const variables = defineEnvVars({
	API_URL: {
		description: 'Base URL of the stop factor console API (Spring)',
		schema: (value) => value || 'http://localhost:9090'
	}
});
