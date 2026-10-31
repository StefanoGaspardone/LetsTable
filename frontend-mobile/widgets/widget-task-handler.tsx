import { WidgetTaskHandlerProps } from 'react-native-android-widget';

import { isWidgetName, renderWidgetVariants, syncWidgets } from '@/widgets/widget-registry';
import { fetchWidgetStats, loadWidgetStats } from '@/widgets/widget-stats';

export const widgetTaskHandler = async (props: WidgetTaskHandlerProps) => {
	const { widgetName } = props.widgetInfo;

	if(!isWidgetName(widgetName)) return;
	if(props.widgetAction === 'WIDGET_DELETED' || props.widgetAction === 'WIDGET_CLICK') return;

	const cached = await loadWidgetStats();
	props.renderWidget(renderWidgetVariants(widgetName, cached));

	if(props.widgetAction === 'WIDGET_RESIZED') return;

	const fresh = await fetchWidgetStats();
	if(fresh === undefined) return;

	await syncWidgets(fresh);
}