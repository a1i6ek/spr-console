import { ApiError } from './client';

/** What a person reads about a failure: the API's own sentence, else the fallback. */
export function messageOf(err: unknown, fallback = 'Что-то пошло не так.'): string {
	if (err instanceof ApiError) {
		return err.errors.length ? [err.message, ...err.errors].join(' ') : err.message || fallback;
	}
	return fallback;
}
