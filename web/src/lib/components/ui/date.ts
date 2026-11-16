/**
 * Dates travel as ISO days ("2026-03-12") and display day first ("12.03.2026"), as the bank reads them.
 * Months travel as ISO months ("2026-03") and display month first ("03.2026").
 */
import { parseDate, type DateValue } from '@ark-ui/svelte/date-picker';

const pad = (n: number, width = 2) => String(n).padStart(width, '0');

export function toDate(iso: string | undefined): DateValue | undefined {
	if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return undefined;

	try {
		return parseDate(iso);
	} catch {
		return undefined;
	}
}

/** The reader's time zone, for the calendar's "today": Ark's is UTC unless
    it is told otherwise. */
export const timeZone =
	typeof Intl !== 'undefined' ? Intl.DateTimeFormat().resolvedOptions().timeZone : 'UTC';

/** Today where the reader is, as an ISO date — not in UTC, which is
    another day for part of every day almost everywhere. */
export function todayIso(): string {
	const now = new Date();
	return `${pad(now.getFullYear(), 4)}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function toIso(date: DateValue): string {
	return `${pad(date.year, 4)}-${pad(date.month)}-${pad(date.day)}`;
}

export function formatDay(date: DateValue): string {
	return `${pad(date.day)}.${pad(date.month)}.${pad(date.year, 4)}`;
}

export function parseDay(text: string): DateValue | undefined {
	const trimmed = text.trim();

	const iso = toDate(trimmed);
	if (iso) return iso;

	const match = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(trimmed);
	if (!match) return undefined;

	return toDate(`${match[3]}-${pad(Number(match[2]))}-${pad(Number(match[1]))}`);
}

/** A month ("2026-03") as the calendar's first day, for the picker's value. */
export function toMonthDate(iso: string | undefined): DateValue | undefined {
	if (!iso || !/^\d{4}-(0[1-9]|1[0-2])$/.test(iso)) return undefined;

	return toDate(`${iso}-01`);
}

export function toMonthIso(date: DateValue): string {
	return `${pad(date.year, 4)}-${pad(date.month)}`;
}

export function formatMonth(date: DateValue): string {
	return `${pad(date.month)}.${pad(date.year, 4)}`;
}

export function parseMonth(text: string): DateValue | undefined {
	const trimmed = text.trim();

	const iso = toMonthDate(trimmed);
	if (iso) return iso;

	const match = /^(\d{1,2})[/.-](\d{4})$/.exec(trimmed);
	if (!match) return undefined;

	return toMonthDate(`${match[2]}-${pad(Number(match[1]))}`);
}
