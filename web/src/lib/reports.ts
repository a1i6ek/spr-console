/**
 * The reports of the console and what each one asks for. The checks here are the ones the SPR web controller makes,
 * with its messages, and run twice: in the form, so a mistake shows before anything is sent, and on the server, which
 * is the one that counts.
 */

export type ReportField = 'month' | 'inn' | 'scoringId' | 'period';

export interface Report {
	title: string;
	description: string;
	fields: readonly ReportField[];
	/** What is downloaded. */
	result: string;
	/** Worth knowing before pressing the button. */
	note?: string;
	/** false: the page is there, the service behind it is not connected yet. */
	available: boolean;
}

export const reports = {
	general: {
		title: 'Общий отчет',
		description: 'Excel-отчет по всем проверкам за месяц.',
		fields: ['month'],
		result: 'Excel (.xlsx)',
		note: 'Отчет за месяц формируется до 10 минут. Не закрывайте страницу, пока файл не скачается.',
		available: true
	},
	inn: {
		title: 'Отчет по ИНН',
		description: 'Файлы всех проверок клиента одним архивом.',
		fields: ['inn'],
		result: 'ZIP-архив',
		available: true
	},
	'hard-collection': {
		title: 'Hard Collection',
		description: 'Заявки с просрочкой, переданные на взыскание.',
		fields: [],
		result: 'Excel (.xlsx)',
		available: false
	},
	file: {
		title: 'Получить файл',
		description: 'Файл одной проверки по ее scoringId.',
		fields: ['scoringId'],
		result: 'Файл проверки',
		available: true
	},
	archive: {
		title: 'Архив за период',
		description: 'Файлы всех проверок за период одним архивом.',
		fields: ['period'],
		result: 'ZIP-архив',
		note: 'Период — не больше 3 календарных дней.',
		available: true
	}
} as const satisfies Record<string, Report>;

export type ReportSlug = keyof typeof reports;

export const isReportSlug = (slug: string): slug is ReportSlug => slug in reports;

/** What a report form sends. */
export interface ReportInput {
	month?: string;
	inn?: string;
	scoringId?: string;
	from?: string;
	to?: string;
}

/** The longest archive period, in days between its first and last day. */
export const ARCHIVE_MAX_DAYS = 3;

const DAY = /^\d{4}-\d{2}-\d{2}$/;

const daysBetween = (from: string, to: string) =>
	Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);

/** The first problem with a report's input, null when it can be sent. */
export function validate(slug: ReportSlug, input: ReportInput): string | null {
	switch (slug) {
		case 'general':
			return /^\d{4}-(0[1-9]|1[0-2])$/.test(input.month ?? '') ? null : 'Неверный формат периода';
		case 'inn':
			return /^\d{14}$/.test(input.inn ?? '') ? null : 'ИНН должен содержать ровно 14 цифр';
		case 'file':
			return /^\d{1,18}$/.test(input.scoringId ?? '') ? null : 'Укажите scoringId: целое число';
		case 'archive': {
			const { from = '', to = '' } = input;
			if (!DAY.test(from) || !DAY.test(to)) return 'Укажите начальную и конечную дату';
			if (from > to) return 'Начальная дата не может быть позже конечной';
			if (daysBetween(from, to) > ARCHIVE_MAX_DAYS)
				return 'Период не должен превышать 3 календарных дня';
			return null;
		}
		case 'hard-collection':
			return 'Выгрузка Hard Collection пока не подключена';
	}
}

/** The first and last day of a month ("2026-11" → 2026-11-01, 2026-11-30). */
export function monthRange(month: string): { from: string; to: string } {
	const [year, m] = month.split('-').map(Number);
	const last = new Date(Date.UTC(year, m, 0)).getUTCDate();
	return { from: `${month}-01`, to: `${month}-${String(last).padStart(2, '0')}` };
}
