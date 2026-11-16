import { resolve } from '$app/paths';
import type { IconName } from '#lib/components/ui/index.js';
import { reports, type ReportSlug } from '#lib/reports.js';

export type SidebarItem = {
	/** Where the link goes, already resolved. */
	href: string;
	/** The URL path the item is the page for, and every path under it. */
	path: string;
	label: string;
	icon: IconName;
};

export type SidebarGroup = {
	/** A name the cookie can hold, so which groups are folded survives a
	    reload. It is not the label: renaming a group should not unfold it. */
	id: string;
	label: string;
	items: SidebarItem[];
};

const reportIcons: Record<ReportSlug, IconName> = {
	general: 'ri-file-chart-line',
	inn: 'ri-file-user-line',
	'hard-collection': 'ri-hand-coin-line',
	file: 'ri-file-download-line',
	archive: 'ri-archive-line'
};

export function sidebarGroups(): SidebarGroup[] {
	return [
		{
			id: 'overview',
			label: 'Обзор',
			items: [{ href: resolve('/(app)'), path: '/', label: 'Обзор', icon: 'ri-dashboard-line' }]
		},
		{
			id: 'reports',
			label: 'Отчеты',
			items: (Object.keys(reports) as ReportSlug[]).map((report) => ({
				href: resolve('/(app)/reports/[report]', { report }),
				path: `/reports/${report}`,
				label: reports[report].title,
				icon: reportIcons[report]
			}))
		},
		{
			id: 'stop-factors',
			label: 'Стоп-факторы',
			items: [
				{
					href: resolve('/(app)/stop-factors'),
					path: '/stop-factors',
					label: 'Стоп-факторы',
					icon: 'ri-file-list-3-line'
				}
			]
		}
	];
}

/** The current page's icon is drawn filled: the `-fill` twin of its `-line` icon. */
export function currentIcon(icon: IconName): IconName {
	return icon.replace(/-line$/, '-fill');
}

/** Compared by path: resolved hrefs are relative while a page renders and never match the pathname. */
export function isCurrentItem(item: SidebarItem, pathname: string): boolean {
	// the overview is the root: only itself, not every page under it
	if (item.path === '/') return pathname === '/';
	return pathname === item.path || pathname.startsWith(`${item.path}/`);
}
