/**
 * Ark renders a switch inside a <label>, so a click anywhere on its row toggled it. This
 * onclick handler cancels clicks that did not land on the switch; the switch's own click and
 * keyboard Space still work.
 */
export function onlyTheSwitch(event: MouseEvent) {
	const target = event.target as Element;

	if (target instanceof HTMLInputElement || target.closest("[data-part='control']")) return;

	event.preventDefault();
}
