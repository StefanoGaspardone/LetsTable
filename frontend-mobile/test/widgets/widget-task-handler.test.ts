import { widgetTaskHandler } from '@/widgets/widget-task-handler';
import { renderWidgetVariants, syncWidgets } from '@/widgets/widget-registry';
import { fetchWidgetStats, loadWidgetStats } from '@/widgets/widget-stats';

jest.mock('@/widgets/widget-registry', () => ({
	...jest.requireActual('@/widgets/widget-registry'),
	renderWidgetVariants: jest.fn(() => ({ light: 'light-variant', dark: 'dark-variant' })),
	syncWidgets: jest.fn(() => Promise.resolve()),
}));

jest.mock('@/widgets/widget-stats', () => ({
	loadWidgetStats: jest.fn(),
	fetchWidgetStats: jest.fn(),
	saveWidgetStats: jest.fn(),
}));

const mockedRenderWidgetVariants = jest.mocked(renderWidgetVariants);
const mockedSyncWidgets = jest.mocked(syncWidgets);
const mockedLoadWidgetStats = jest.mocked(loadWidgetStats);
const mockedFetchWidgetStats = jest.mocked(fetchWidgetStats);

const cached = { totalMatches: 10, totalWins: 4, totalGames: 7, totalFriends: 2 } as any;
const fresh = { totalMatches: 11, totalWins: 5, totalGames: 7, totalFriends: 2 } as any;

const buildProps = (widgetAction: string, widgetName = 'WinRate') => ({
	widgetInfo: { widgetName, widgetId: 1, height: 100, width: 200, screenInfo: {} },
	widgetAction,
	renderWidget: jest.fn(),
}) as any;

describe('widgetTaskHandler', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockedLoadWidgetStats.mockResolvedValue(cached);
		mockedFetchWidgetStats.mockResolvedValue(fresh);
	});

	it('ignores unknown widgets', async () => {
		const props = buildProps('WIDGET_UPDATE', 'Unknown');

		await widgetTaskHandler(props);

		expect(props.renderWidget).not.toHaveBeenCalled();
		expect(mockedLoadWidgetStats).not.toHaveBeenCalled();
	});

	it.each(['WIDGET_DELETED', 'WIDGET_CLICK'])('does nothing on %s', async (action) => {
		const props = buildProps(action);

		await widgetTaskHandler(props);

		expect(props.renderWidget).not.toHaveBeenCalled();
		expect(mockedFetchWidgetStats).not.toHaveBeenCalled();
	});

	it.each(['WIDGET_ADDED', 'WIDGET_UPDATE'])('renders the cached stats immediately and then syncs fresh ones on %s', async (action) => {
		const props = buildProps(action);

		await widgetTaskHandler(props);

		expect(mockedRenderWidgetVariants).toHaveBeenCalledWith('WinRate', cached);
		expect(props.renderWidget).toHaveBeenCalledWith({ light: 'light-variant', dark: 'dark-variant' });
		expect(mockedFetchWidgetStats).toHaveBeenCalled();
		expect(mockedSyncWidgets).toHaveBeenCalledWith(fresh);
	});

	it('only re-renders the cached stats when the widget is resized', async () => {
		const props = buildProps('WIDGET_RESIZED');

		await widgetTaskHandler(props);

		expect(props.renderWidget).toHaveBeenCalledTimes(1);
		expect(mockedFetchWidgetStats).not.toHaveBeenCalled();
		expect(mockedSyncWidgets).not.toHaveBeenCalled();
	});

	it('keeps the cached stats when fetching fresh ones fails', async () => {
		mockedFetchWidgetStats.mockResolvedValueOnce(undefined);
		const props = buildProps('WIDGET_UPDATE');

		await widgetTaskHandler(props);

		expect(props.renderWidget).toHaveBeenCalledTimes(1);
		expect(mockedSyncWidgets).not.toHaveBeenCalled();
	});

	it('switches the widgets to the logged out state when there is no session', async () => {
		mockedFetchWidgetStats.mockResolvedValueOnce(null);
		const props = buildProps('WIDGET_UPDATE');

		await widgetTaskHandler(props);

		expect(mockedSyncWidgets).toHaveBeenCalledWith(null);
	});

	it('renders the logged out variant when nothing is cached yet', async () => {
		mockedLoadWidgetStats.mockResolvedValueOnce(null);
		const props = buildProps('WIDGET_ADDED');

		await widgetTaskHandler(props);

		expect(mockedRenderWidgetVariants).toHaveBeenCalledWith('WinRate', null);
	});
});