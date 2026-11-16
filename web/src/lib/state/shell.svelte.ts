import { createContext } from 'svelte';
import { SvelteSet } from 'svelte/reactivity';
import { rememberClosedGroups, rememberSidebar, type SidebarState } from './sidebar';

export type Shell = {
	readonly collapsed: boolean;
	toggle: () => void;

	isOpen: (id: string) => boolean;
	toggleGroup: (id: string) => void;

	readonly menuOpen: boolean;
	setMenu: (open: boolean) => void;
};

const [getShell, setShell, hasShell] = createContext<Shell>();

export function provideShell(initial: SidebarState, closedGroups: string[]): Shell {
	let collapsed = $state(initial === 'mini');

	// The closed ones are held, not the open ones, so a section a release adds
	// arrives open rather than hidden from whoever had folded the others.
	const closed = new SvelteSet(closedGroups);

	let menuOpen = $state(false);

	const shell: Shell = {
		get collapsed() {
			return collapsed;
		},

		toggle() {
			collapsed = !collapsed;
			rememberSidebar(collapsed ? 'mini' : 'wide');
		},

		isOpen(id: string) {
			return !closed.has(id);
		},

		toggleGroup(id: string) {
			if (closed.has(id)) {
				closed.delete(id);
			} else {
				closed.add(id);
			}

			rememberClosedGroups([...closed]);
		},

		get menuOpen() {
			return menuOpen;
		},

		setMenu(open: boolean) {
			menuOpen = open;
		}
	};

	return setShell(shell);
}

export function useShell(): Shell {
	if (!hasShell()) throw new Error('useShell() needs the panel layout above it');

	return getShell();
}
