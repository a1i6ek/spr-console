import { COOKIES } from '#lib/brand.js';
import { rememberChoice } from '#lib/utils/cookies.js';

export type SidebarState = 'wide' | 'mini';

export function isSidebarState(value: unknown): value is SidebarState {
	return value === 'wide' || value === 'mini';
}

export function rememberSidebar(state: SidebarState) {
	rememberChoice(COOKIES.sidebar, state);
}

export function parseClosedGroups(raw: string | undefined): string[] {
	if (!raw) return [];

	return raw
		.split(',')
		.map((id) => id.trim())
		.filter((id) => /^[a-z-]+$/.test(id));
}

export function rememberClosedGroups(closed: string[]) {
	rememberChoice(COOKIES.branches, closed.join(','));
}
