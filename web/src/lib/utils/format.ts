export function formatDateTime(value: string): string {
	const date = new Date(value);

	return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function formatDate(value: string): string {
	const date = new Date(value);

	return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function formatRelative(value: string, now: Date = new Date()): string {
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return value;

	const elapsed = now.getTime() - date.getTime();

	if (elapsed < MINUTE) return 'just now';
	if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)} min ago`;
	if (elapsed < DAY && sameDay(date, now)) return `${Math.floor(elapsed / HOUR)} h ago`;

	const days = calendarDaysBetween(date, now);
	if (days === 1) return 'yesterday';
	if (days < 7) return `${days} days ago`;

	return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export function formatDayHeading(value: string, now: Date = new Date()): string {
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return value;

	const days = calendarDaysBetween(date, now);
	if (days === 0) return 'Today';
	if (days === 1) return 'Yesterday';

	return date.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' });
}

export function formatTime(value: string): string {
	const date = new Date(value);

	return Number.isNaN(date.getTime())
		? value
		: date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

export function formatShortDay(day: string): string {
	const [year, month, date] = day.split('-').map(Number);
	const parsed = new Date(year, month - 1, date);

	return Number.isNaN(parsed.getTime())
		? day
		: parsed.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export function describeUserAgent(agent: string): string {
	if (!agent) return 'Unknown device';

	const browser =
		[
			['Edge', /Edg\//],
			['Opera', /OPR\//],
			['Firefox', /Firefox\//],
			['Chrome', /Chrome\//],
			['Safari', /Version\/.*Safari\//],
			['curl', /^curl\//]
		].find(([, pattern]) => (pattern as RegExp).test(agent))?.[0] ?? 'Browser';

	const system =
		[
			['iOS', /iPhone|iPad/],
			['Android', /Android/],
			['macOS', /Mac OS X|Macintosh/],
			['Windows', /Windows/],
			['Linux', /Linux/]
		].find(([, pattern]) => (pattern as RegExp).test(agent))?.[0] ?? '';

	return system ? `${browser} on ${system}` : String(browser);
}

function sameDay(a: Date, b: Date): boolean {
	return calendarDaysBetween(a, b) === 0;
}

function calendarDaysBetween(from: Date, to: Date): number {
	const start = new Date(from.getFullYear(), from.getMonth(), from.getDate());
	const end = new Date(to.getFullYear(), to.getMonth(), to.getDate());

	return Math.round((end.getTime() - start.getTime()) / DAY);
}

export function initials(name: string): string {
	const words = name.split(/\s+/).filter(Boolean);
	const letters = words.length > 1 ? words[0][0] + words[1][0] : (words[0] ?? '').slice(0, 2);

	return letters.toUpperCase();
}

export function countOf(n: number, noun: string): string {
	return `${n.toLocaleString('en')} ${noun}${n === 1 ? '' : 's'}`;
}
