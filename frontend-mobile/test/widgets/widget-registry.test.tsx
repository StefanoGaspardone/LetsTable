import { Platform } from 'react-native';

import type * as WidgetRegistryModule from '@/widgets/widget-registry';

jest.mock('@/widgets/widget-components', () => ({
	StatsOverviewWidget: jest.fn(() => null),
	WinRateWidget: jest.fn(() => null),
	CollectionWidget: jest.fn(() => null),
	FriendsWidget: jest.fn(() => null),
	LoggedOutWidget: jest.fn(() => null),
}));

jest.mock('@/widgets/widget-stats', () => ({
	saveWidgetStats: jest.fn(() => Promise.resolve()),
}));

const stats = { totalMatches: 12, totalWins: 5, totalGames: 8, totalFriends: 3 } as any;

let registry: typeof WidgetRegistryModule;
let components: Record<string, jest.Mock>;
let saveWidgetStats: jest.Mock;
let requestWidgetUpdate: jest.Mock;

describe('widget-registry', () => {
	beforeEach(() => {
		jest.resetModules();
		Platform.OS = 'android';

		registry = require('@/widgets/widget-registry');
		components = require('@/widgets/widget-components');
		({ saveWidgetStats } = require('@/widgets/widget-stats'));
		({ requestWidgetUpdate } = require('react-native-android-widget'));

		saveWidgetStats.mockClear();
		requestWidgetUpdate.mockClear();
	});

	describe('isWidgetName', () => {
		it.each(['StatsOverview', 'WinRate', 'Collection', 'Friends'])('recognizes %s', (name) => {
			expect(registry.isWidgetName(name)).toBe(true);
		});

		it('rejects unknown widget names', () => {
			expect(registry.isWidgetName('Unknown')).toBe(false);
		});
	});

	describe('renderWidgetVariants', () => {
		it.each([
			['StatsOverview', 'StatsOverviewWidget'],
			['WinRate', 'WinRateWidget'],
			['Collection', 'CollectionWidget'],
			['Friends', 'FriendsWidget'],
		] as const)('renders %s in light and dark variants', (widgetName, componentName) => {
			const variants = registry.renderWidgetVariants(widgetName, stats);

			expect(variants.light.type).toBe(components[componentName]);
			expect(variants.light.props).toEqual({ stats, theme: 'light' });
			expect(variants.dark.type).toBe(components[componentName]);
			expect(variants.dark.props).toEqual({ stats, theme: 'dark' });
		});

		it('renders the logged out widget when there are no stats', () => {
			const variants = registry.renderWidgetVariants('WinRate', null);

			expect(variants.light.type).toBe(components.LoggedOutWidget);
			expect(variants.dark.props).toEqual({ theme: 'dark' });
		});
	});

	describe('syncWidgets', () => {
		it('does nothing outside Android', async () => {
			Platform.OS = 'ios';

			await registry.syncWidgets(stats);

			expect(saveWidgetStats).not.toHaveBeenCalled();
			expect(requestWidgetUpdate).not.toHaveBeenCalled();
		});

		it('saves the snapshot and updates every widget', async () => {
			await registry.syncWidgets(stats);

			expect(saveWidgetStats).toHaveBeenCalledWith(stats);
			expect(requestWidgetUpdate).toHaveBeenCalledTimes(4);
			expect(requestWidgetUpdate.mock.calls.map(([options]) => options.widgetName)).toEqual(['StatsOverview', 'WinRate', 'Collection', 'Friends']);
		});

		it('passes a renderer that draws the widget with the given stats and ignores missing widgets', async () => {
			await registry.syncWidgets(stats);

			const options = requestWidgetUpdate.mock.calls[0][0];
			const variants = options.renderWidget();

			expect(variants.light.type).toBe(components.StatsOverviewWidget);
			expect(variants.light.props.stats).toEqual(stats);
			expect(options.widgetNotFound()).toBeUndefined();
		});

		it('skips the update when the stats did not change', async () => {
			await registry.syncWidgets(stats);
			await registry.syncWidgets({ ...stats });

			expect(saveWidgetStats).toHaveBeenCalledTimes(1);
			expect(requestWidgetUpdate).toHaveBeenCalledTimes(4);
		});

		it('updates again when the stats change', async () => {
			await registry.syncWidgets(stats);
			await registry.syncWidgets({ ...stats, totalWins: 6 });

			expect(saveWidgetStats).toHaveBeenCalledTimes(2);
			expect(requestWidgetUpdate).toHaveBeenCalledTimes(8);
		});

		it('clears the snapshot and shows the logged out widget after logout', async () => {
			await registry.syncWidgets(stats);
			await registry.syncWidgets(null);

			expect(saveWidgetStats).toHaveBeenLastCalledWith(null);

			const lastVariants = requestWidgetUpdate.mock.calls.at(-1)[0].renderWidget();
			expect(lastVariants.light.type).toBe(components.LoggedOutWidget);
		});

		it('syncs the logged out state on the first call', async () => {
			await registry.syncWidgets(null);

			expect(saveWidgetStats).toHaveBeenCalledWith(null);
			expect(requestWidgetUpdate).toHaveBeenCalledTimes(4);
		});
	});
});