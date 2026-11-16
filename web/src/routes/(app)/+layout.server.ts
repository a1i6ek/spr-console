import { redirect } from '@sveltejs/kit';
import { COOKIES } from '#lib/brand.js';
import { isSidebarState, parseClosedGroups, type SidebarState } from '#lib/state/sidebar.js';
import { readChoice } from '#lib/utils/cookies.js';
import type { LayoutServerLoad } from './$types';

// The hook already sends guests to /login; this keeps every page of the group private on its own too.
// The sidebar's state is read here, so the first paint already has it the way it was left.
export const load: LayoutServerLoad = ({ locals, cookies, url }) => {
	if (!locals.user) redirect(303, '/login');

	const saved = readChoice(cookies, COOKIES.sidebar, url);
	const sidebar: SidebarState = isSidebarState(saved) ? saved : 'wide';
	const closedGroups = parseClosedGroups(readChoice(cookies, COOKIES.branches, url));

	return { username: locals.user.username, sidebar, closedGroups };
};
