import type { IconName } from './icon';

export type Size = 'sm' | 'md' | 'lg';

export type Variant = 'solid' | 'subtle' | 'outline' | 'ghost' | 'plain';

export type ColorPalette = 'neutral' | 'danger' | 'success' | 'info' | 'warning';

export type ControlProps = {
	size?: Size;
	variant?: Variant;
	colorPalette?: ColorPalette;
	loading?: boolean;
	disabled?: boolean;
	icon?: IconName;
};
