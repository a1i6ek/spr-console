/**
 * The console API, one function per endpoint (vard's api/admin.ts). Loads pass their `fetch`;
 * form actions call the same functions through `action()` (#lib/server/actions.ts), with the
 * arguments the form sends as its input.
 */
import { api, type Fetch } from './client';
import type { Metric, Product, StopFactor, StopFactorRequest } from './types';

export type FactorFilter = {
	search?: string;
	metricId?: string;
	productId?: string;
	segment?: string;
	active?: string;
};

export const factorsApi = {
	search: (filter: FactorFilter, fetch?: Fetch) =>
		api.get<StopFactor[]>('/stop-factors', filter, fetch),
	get: (factorId: number, fetch?: Fetch) =>
		api.get<StopFactor>(`/stop-factors/${factorId}`, undefined, fetch),
	versions: (factorId: number, fetch?: Fetch) =>
		api.get<StopFactor[]>(`/stop-factors/${factorId}/versions`, undefined, fetch),

	create: (factor: StopFactorRequest) => api.post<StopFactor>('/stop-factors', factor),
	update: (factorId: number, factor: StopFactorRequest) =>
		api.put<StopFactor>(`/stop-factors/${factorId}`, factor),
	setStatus: (factorId: number, active: boolean, changeReason: string) =>
		api.patch<StopFactor>(`/stop-factors/${factorId}/status`, { active, changeReason }),
	remove: (factorId: number, changeReason: string) =>
		api.delete<void>(`/stop-factors/${factorId}`, { changeReason })
};

export const metricsApi = {
	list: (fetch?: Fetch) => api.get<Metric[]>('/metrics', undefined, fetch)
};

export const productsApi = {
	list: (fetch?: Fetch) => api.get<Product[]>('/products', undefined, fetch)
};
