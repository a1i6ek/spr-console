export type Column = {
	key: string;
	label?: string;
	min?: string;
	align?: 'start' | 'end';
	/** Takes the width the other columns leave, rather than sharing it out. */
	grow?: boolean;
};
