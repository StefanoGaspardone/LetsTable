import type { JSX } from 'react';
import { Platform } from 'react-native';
import { requestWidgetUpdate } from 'react-native-android-widget';

import { CollectionWidget, FriendsWidget, LoggedOutWidget, StatsOverviewWidget, WidgetTheme, WinRateWidget } from '@/widgets/widget-components';
import { saveWidgetStats } from '@/widgets/widget-stats';

import { UserStats } from '@/types/stats';

export const WIDGET_NAMES = ['StatsOverview', 'WinRate', 'Collection', 'Friends'] as const;

export type WidgetName = typeof WIDGET_NAMES[number];

const RENDERERS: Record<WidgetName, (stats: UserStats, theme: WidgetTheme) => JSX.Element> = {
	StatsOverview: (stats, theme) => <StatsOverviewWidget stats = { stats } theme = { theme }/>,
	WinRate: (stats, theme) => <WinRateWidget stats = { stats } theme = { theme }/>,
	Collection: (stats, theme) => <CollectionWidget stats = { stats } theme = { theme }/>,
	Friends: (stats, theme) => <FriendsWidget stats = { stats } theme = { theme }/>,
}

export const isWidgetName = (name: string): name is WidgetName => (WIDGET_NAMES as readonly string[]).includes(name);

export const renderWidgetVariants = (widgetName: WidgetName, stats: UserStats | null) => {
	const render = (theme: WidgetTheme) => (stats ? RENDERERS[widgetName](stats, theme) : <LoggedOutWidget theme = { theme }/>);

	return {
		light: render('light'),
		dark: render('dark'),
	}
}

let lastSyncedStats: string | null | undefined;

export const syncWidgets = async (stats: UserStats | null): Promise<void> => {
	if(Platform.OS !== 'android') return;

	const serialized = stats ? JSON.stringify(stats) : null;
	if(serialized === lastSyncedStats) return;

	lastSyncedStats = serialized;
	await saveWidgetStats(stats);

	await Promise.all(WIDGET_NAMES.map(widgetName => requestWidgetUpdate({
		widgetName,
		renderWidget: () => renderWidgetVariants(widgetName, stats),
		widgetNotFound: () => undefined,
	})));
}