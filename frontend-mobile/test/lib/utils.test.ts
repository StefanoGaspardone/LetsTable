import { cn } from '@/lib/utils';

describe('cn', () => {
	it('joins multiple string class names', () => {
		expect(cn('flex', 'items-center')).toBe('flex items-center');
	});

	it('ignores falsy values', () => {
		expect(cn('flex', false, null, undefined, '', 'items-center')).toBe('flex items-center');
	});

	it('applies conditional classes based on boolean expressions', () => {
		const isActive = true;
		expect(cn('base', isActive && 'active')).toBe('base active');
	});

	it('omits conditional classes when the condition is false', () => {
		const isActive = false;
		expect(cn('base', isActive && 'active')).toBe('base');
	});

	it('supports object syntax for conditional classes', () => {
		expect(cn({ flex: true, hidden: false, 'items-center': true })).toBe('flex items-center');
	});

	it('supports arrays of class names', () => {
		expect(cn(['flex', 'items-center'], 'gap-2')).toBe('flex items-center gap-2');
	});

	it('resolves conflicting Tailwind classes by keeping the last one', () => {
		expect(cn('px-2', 'px-4')).toBe('px-4');
	});

	it('resolves conflicting Tailwind color classes by keeping the last one', () => {
		expect(cn('text-red-500', 'text-blue-500')).toBe('text-blue-500');
	});

	it('does not merge unrelated Tailwind classes', () => {
		expect(cn('px-4', 'py-2')).toBe('px-4 py-2');
	});

	it('merges conflicting classes even when passed conditionally', () => {
		const override = true;
		expect(cn('px-2', override && 'px-4')).toBe('px-4');
	});

	it('returns an empty string when given no arguments', () => {
		expect(cn()).toBe('');
	});

	it('returns an empty string when all inputs are falsy', () => {
		expect(cn(false, null, undefined)).toBe('');
	});
});