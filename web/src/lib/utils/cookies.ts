import type { Cookies } from '@sveltejs/kit';

/** A choice is kept for a year. */
const ONE_YEAR = 60 * 60 * 24 * 365;

/**
 * The reader's choices (theme, language, the sidebar) are cookies so the server renders the page
 * with them. Over https each is a __Host- one — Secure, Path=/, this host alone — so a sibling
 * subdomain cannot plant a choice; plain http keeps the bare name, which a browser requires there.
 * Every name is read whole, never matched by a pattern: on localhost every port shares the cookies.
 */
export function choiceName(name: string, secure: boolean): string {
	return secure ? `__Host-${name}` : name;
}

/** Keeps a choice from the browser. */
export function rememberChoice(name: string, value: string): void {
	const secure = location.protocol === 'https:';
	document.cookie = [
		`${choiceName(name, secure)}=${encodeURIComponent(value)}`,
		'path=/',
		`max-age=${ONE_YEAR}`,
		'samesite=lax',
		...(secure ? ['secure'] : [])
	].join('; ');
}

/** Reads a choice while a page is rendered. */
export function readChoice(cookies: Cookies, name: string, url: URL): string | undefined {
	return cookies.get(choiceName(name, url.protocol === 'https:'));
}

/** Keeps a choice from the server; readable by the page's script, which may change it. */
export function setChoice(cookies: Cookies, name: string, value: string, url: URL): void {
	const secure = url.protocol === 'https:';
	cookies.set(choiceName(name, secure), value, {
		path: '/',
		httpOnly: false,
		sameSite: 'lax',
		secure,
		maxAge: ONE_YEAR
	});
}
