/**
 * What the overview page recommends, from the rules as they are now. Each recommendation names a
 * problem a person can act on and where to act on it.
 */
import type { Metric, Product, StopFactor } from './api/index.js';

export type Recommendation = {
	id: string;
	tone: 'danger' | 'warning' | 'info';
	icon: string;
	title: string;
	detail: string;
	/** Where to act on it, already resolved, and what the link says. */
	href: string;
	action: string;
};

type Links = {
	factors: (query: string) => string;
	metrics: string;
};

export function recommendations(
	factors: StopFactor[],
	metrics: Metric[],
	products: Product[],
	links: Links
): Recommendation[] {
	const found: Recommendation[] = [];
	const metricsById = new Map(metrics.map((metric) => [metric.id, metric]));

	const off = factors.filter((f) => !f.active);
	if (off.length) {
		found.push({
			id: 'factors-off',
			tone: 'warning',
			icon: 'ri-toggle-line',
			title: `${off.length} стоп-факторов отключены`,
			detail: 'Они не проверяются. Включите нужные, а ненужные удалите — история сохранится.',
			href: links.factors('active=false'),
			action: 'Посмотреть'
		});
	}

	const live = factors.filter((f) => f.active);

	const noThresholds = live.filter((f) => f.conditions.length === 0);
	if (noThresholds.length) {
		found.push({
			id: 'no-thresholds',
			tone: 'info',
			icon: 'ri-scales-3-line',
			title: `${noThresholds.length} активных стоп-факторов без порогов`,
			detail:
				'Они измеряются и сохраняются в проверках, но не отклоняют заявки и не отправляют их андеррайтеру.',
			href: links.factors('active=true'),
			action: 'Посмотреть'
		});
	}

	const onInactiveMetric = live.filter((f) => metricsById.get(f.metric.id)?.active === false);
	if (onInactiveMetric.length) {
		found.push({
			id: 'inactive-metric',
			tone: 'danger',
			icon: 'ri-error-warning-line',
			title: `${onInactiveMetric.length} стоп-факторов на отключённых метриках`,
			detail: 'Изменить их можно, только выбрав активную метрику. Проверьте, нужны ли они.',
			href: links.metrics,
			action: 'К метрикам'
		});
	}

	const used = new Set(factors.map((f) => f.metric.id));
	const unused = metrics.filter((m) => m.active && !used.has(m.id));
	if (unused.length) {
		found.push({
			id: 'unused-metrics',
			tone: 'info',
			icon: 'ri-ruler-line',
			title: `${unused.length} метрик не используются`,
			detail: `Ни один стоп-фактор не измеряет: ${unused.map((m) => m.title).join(', ')}.`,
			href: links.metrics,
			action: 'К метрикам'
		});
	}

	// a product no product-limited rule names gets only the rules of its segment and of every product
	const named = new Set(live.flatMap((f) => f.productIds));
	const uncovered = products.filter((p) => !named.has(p.id));
	if (uncovered.length && named.size) {
		found.push({
			id: 'uncovered-products',
			tone: 'warning',
			icon: 'ri-shopping-bag-3-line',
			title: `${uncovered.length} продуктов без собственных правил`,
			detail: `${uncovered.map((p) => p.name).join(', ')}: проверяются только общими стоп-факторами своего сегмента.`,
			href: links.factors(''),
			action: 'К стоп-факторам'
		});
	}

	return found;
}
