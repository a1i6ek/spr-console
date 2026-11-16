import { createToaster } from '@ark-ui/svelte/toast';
import { messageOf } from '#lib/api/index.js';

export const toaster = createToaster({
	placement: 'bottom',
	offsets: '32px',
	gap: 10,
	max: 4,
	duration: 5000,
	pauseOnPageIdle: true
});

const ERROR_DURATION = 8000;

export const notify = {
	success(title: string, description?: string) {
		toaster.success({ title, description });
	},

	info(title: string, description?: string) {
		toaster.info({ title, description });
	},

	/** Something failed: the server's sentence when it sent one, the
	    fallback otherwise — never a stack trace or a status code. */
	error(err: unknown, fallback: string) {
		toaster.error({ title: messageOf(err, fallback), duration: ERROR_DURATION });
	},

	problem(message: string) {
		toaster.error({ title: message, duration: ERROR_DURATION });
	}
};
