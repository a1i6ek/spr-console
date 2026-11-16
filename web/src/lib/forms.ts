import { applyAction, type SubmitFunction } from '$app/forms';
import { ApiError } from '#lib/api/index.js';
import type { ActionProblem } from '#lib/server/actions.js';

type Options<T> = {
	/** What the action is given: the page's state, sent as the form's `input` field. */
	input: () => unknown;
	/** False keeps the form from being sent at all. */
	when?: () => boolean;
	pending?: (busy: boolean) => void;
	done: (data: T) => unknown;
	failed?: (error: ApiError) => unknown;
};

/**
 * The `use:enhance` callback every form uses (vard's submit). A form's fields are its state rather
 * than its inputs (selects, switches and lists are not form controls), so the state goes as one
 * JSON field; the action answers with what the API answered, and the page applies it.
 */
export function submit<T>(options: Options<T>): SubmitFunction {
	return ({ formData, cancel }) => {
		if (options.when && !options.when()) {
			cancel();
			return;
		}

		formData.set('input', JSON.stringify(options.input()));
		options.pending?.(true);

		return async ({ result }) => {
			options.pending?.(false);

			switch (result.type) {
				case 'success':
					await options.done(result.data as T);
					break;
				case 'redirect':
					await applyAction(result);
					break;
				case 'failure': {
					const { error } = result.data as ActionProblem;
					await options.failed?.(new ApiError(error.status, error.message, error.errors));
					break;
				}
				case 'error':
					await options.failed?.(new ApiError(result.status ?? 500, 'Что-то пошло не так.'));
			}
		};
	};
}
