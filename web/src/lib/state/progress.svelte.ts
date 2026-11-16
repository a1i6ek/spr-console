export const TRICKLE = 200;
export const CEILING = 0.94;

export const START = 0.3;

export const HOLD = 80;

export class NavigationProgress {
	value = $state(0);
	visible = $state(false);
	instant = $state(false);

	#running = false;
	#navigation = 0;
	#ready: Promise<void> = Promise.resolve();

	#trickle: ReturnType<typeof setTimeout> | undefined;
	#hide: ReturnType<typeof setTimeout> | undefined;

	readonly #settle: () => Promise<void>;

	constructor(settle: () => Promise<void> = async () => {}) {
		this.#settle = settle;
	}

	start() {
		clearTimeout(this.#hide);

		// A navigation that starts while another is still under way — a
		// redirect, or a second click — carries on with the same bar.
		if (this.#running) return;

		this.#running = true;
		const navigation = ++this.#navigation;
		clearTimeout(this.#trickle);

		this.instant = true;
		this.value = 0;
		this.visible = true;

		let ready: Promise<void>;
		try {
			ready = this.#settle();
		} catch {
			ready = Promise.reject(new Error('navigation progress could not settle'));
		}
		this.#ready = ready.then(
			() => {
				// A newer navigation may have taken over while this one settled.
				if (!this.#running || navigation !== this.#navigation) return;

				this.instant = false;
				this.value = START;
				this.#creep();
			},
			() => {
				// A failed settle means there is no bar to move. This is also
				// a teardown path, so do not leave a rejected promise behind.
				if (navigation !== this.#navigation) return;

				this.#navigation += 1;
				this.instant = false;
				this.#running = false;
				this.value = 0;
				this.visible = false;
			}
		);
	}

	async finish() {
		if (!this.#running) return;

		const navigation = this.#navigation;
		this.#running = false;
		clearTimeout(this.#trickle);

		await this.#ready;

		// Another navigation began while this one was being put in place.
		if (this.#running || navigation !== this.#navigation) return;

		this.value = 1;
		this.#hide = setTimeout(() => (this.visible = false), HOLD);
	}

	dispose() {
		this.#running = false;
		this.#navigation += 1;
		clearTimeout(this.#trickle);
		clearTimeout(this.#hide);
	}

	#creep() {
		this.#trickle = setTimeout(() => {
			this.value += (CEILING - this.value) * (0.1 + Math.random() * 0.1);
			this.#creep();
		}, TRICKLE);
	}
}
