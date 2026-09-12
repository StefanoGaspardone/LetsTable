import { notifyManager } from '@tanstack/react-query';

notifyManager.setNotifyFunction((fn) => {
	fn();
});

notifyManager.setBatchNotifyFunction((fn) => {
	fn();
});