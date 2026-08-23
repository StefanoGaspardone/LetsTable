import { formatDistanceToNow, format } from 'date-fns';
import { it } from 'date-fns/locale';

export const formatRelativeDays = (dateString: string): string => {
	const date = new Date(dateString);
	const isToday = new Date().toDateString() === date.toDateString();
	
    if(isToday) return 'Oggi';

	return formatDistanceToNow(date, { addSuffix: true, locale: it });
}

export const formatFullDate = (dateString: string): string => {
	return format(new Date(dateString), 'd MMMM yyyy', { locale: it });
}

export const formatShortDate = (dateString: string): string => {
	return format(new Date(dateString), 'd MMM yyyy', { locale: it });
}