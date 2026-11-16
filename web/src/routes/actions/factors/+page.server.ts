import { factorsApi } from '#lib/api/index.js';
import { action } from '#lib/server/actions.js';
import type { Actions } from './$types';

// Every change of a stop factor, as vard's /actions routes: a form posts its input as JSON, the
// action calls the typed client with it in order, and answers with what the API answered.
export const actions = {
	create: action(factorsApi.create),
	update: action(factorsApi.update),
	setStatus: action(factorsApi.setStatus),
	remove: action(factorsApi.remove)
} satisfies Actions;
