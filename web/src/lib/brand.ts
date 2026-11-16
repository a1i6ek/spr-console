/**
 * The console's brand: its name, its mark, and every cookie name built from its slug (vard's brand.ts).
 */
import logo from '#lib/assets/bakai-logo.jpg';

export const BRAND = {
	/** The bank, then the system: the header reads "BAKAI / SPR". */
	name: 'BAKAI',
	product: 'SPR',

	/** The bank's mark, drawn in the header and on the sign-in page. */
	logo,

	/** What cookie names are built from: lower-case letters and digits. */
	slug: 'spr'
} as const;

/** A page's <title>: its parts, most specific first, the empty ones left out, then the product. */
export function pageTitle(...parts: (string | null | undefined)[]): string {
	return [...parts, `${BRAND.name} / ${BRAND.product}`].filter(Boolean).join(' · ');
}

export const COOKIES = {
	/** The dummy login's session and who it is for; HttpOnly, the page never reads them. */
	session: 'session',
	username: 'username',
	role: 'role',

	sidebar: `${BRAND.slug}-sidebar`,
	branches: `${BRAND.slug}-sidebar-closed`
} as const;
