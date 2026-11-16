/** Report sections of the console. Their pages are placeholders: the filters work, building is not there yet. */

export type ReportField = 'period' | 'date' | 'segment' | 'products' | 'inn' | 'fileType';

export interface Report {
	title: string;
	description: string;
	fields: ReportField[];
}

export const reports = {
	general: {
		title: 'Общий отчет',
		description: 'Сводка проверок стоп-факторов за период.',
		fields: ['period', 'segment', 'products']
	},
	inn: {
		title: 'Отчет по ИНН',
		description: 'Все проверки клиента по его ИНН.',
		fields: ['inn', 'period']
	},
	'hard-collection': {
		title: 'Hard Collection',
		description: 'Заявки с просрочкой, переданные на взыскание.',
		fields: ['date', 'products']
	},
	file: {
		title: 'Получить файл',
		description: 'Выгрузка результатов проверок в файл.',
		fields: ['fileType', 'period']
	},
	archive: {
		title: 'Архив за период',
		description: 'Архив проверок и решений за выбранный период.',
		fields: ['period']
	}
} as const satisfies Record<string, Report>;

export type ReportSlug = keyof typeof reports;

export const isReportSlug = (slug: string): slug is ReportSlug => slug in reports;
