import { defineEnvVars } from '@sveltejs/kit/env';

export const variables = defineEnvVars({
	API_URL: {
		description: 'Base URL of the console API (Spring): login, stop factors, reports',
		schema: (value) => value || 'http://localhost:9090'
	}
});
