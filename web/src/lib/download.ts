/** Downloading a report: the browser asks this app's server, which streams the file from the SPR service. */

export class DownloadError extends Error {
	constructor(
		message: string,
		readonly status: number
	) {
		super(message);
	}
}

/** The file name a Content-Disposition gives: `filename*=UTF-8''…` first, then `filename="…"`. */
export function fileNameOf(disposition: string | null): string | null {
	if (!disposition) return null;
	const encoded = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(disposition);
	if (encoded) {
		try {
			return decodeURIComponent(encoded[1].trim());
		} catch {
			/* not encoded after all: the plain name below */
		}
	}
	const plain = /filename\s*=\s*"?([^";]+)"?/.exec(disposition);
	return plain ? plain[1].trim() : null;
}

/** Posts the form, saves the file it answers with and returns its name; a refusal throws its message. */
export async function download(url: string, body: unknown, fallbackName: string): Promise<string> {
	const answer = await fetch(url, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body)
	});
	// no session: the server's guard answers with the login page
	if (answer.status === 401 || answer.redirected) {
		throw new DownloadError('Сессия истекла. Войдите снова.', 401);
	}
	if (!answer.ok) {
		const data = (await answer.json().catch(() => null)) as { message?: string } | null;
		throw new DownloadError(data?.message ?? `Ошибка ${answer.status}`, answer.status);
	}

	const name = fileNameOf(answer.headers.get('content-disposition')) ?? fallbackName;
	const link = document.createElement('a');
	link.href = URL.createObjectURL(await answer.blob());
	link.download = name;
	document.body.append(link);
	link.click();
	link.remove();
	setTimeout(() => URL.revokeObjectURL(link.href), 60_000);
	return name;
}
