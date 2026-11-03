import { act } from 'react';
import { notifyManager } from '@tanstack/react-query';

notifyManager.setNotifyFunction((fn) => {
	if((globalThis as any).IS_REACT_ACT_ENVIRONMENT) {
		act(() => {
			fn();
		});
		return;
	}

	fn();
});

notifyManager.setBatchNotifyFunction((fn) => {
	fn();
});

jest.mock('@react-native-async-storage/async-storage', () =>
	require('@react-native-async-storage/async-storage/jest/async-storage-mock')
);

jest.mock('react-native-android-widget', () => ({
	requestWidgetUpdate: jest.fn(() => Promise.resolve()),
	registerWidgetTaskHandler: jest.fn(),
	FlexWidget: 'FlexWidget',
	TextWidget: 'TextWidget',
	SvgWidget: 'SvgWidget',
	ImageWidget: 'ImageWidget',
	OverlapWidget: 'OverlapWidget',
}));