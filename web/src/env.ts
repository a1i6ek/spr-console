import { defineEnvVars } from '@sveltejs/kit/env';

export const variables = defineEnvVars({
	API_URL: {
		description: 'Base URL of the stop factor console API (Spring)',
		schema: (value) => value || 'http://localhost:9090'
	},
	SPR_API_URL: {
		description:
			'Base URL of the SPR API that serves the check files (server.base.url of WebController)',
		schema: (value) => value || 'http://localhost:8060'
	},
	REPORT_URL: {
		description:
			'Base URL of the report service that builds the general report (report.url of WebController)',
		schema: (value) => value || 'http://localhost:8064'
	}
});
