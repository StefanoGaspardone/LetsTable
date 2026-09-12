import { LocaleConfig } from 'react-native-calendars';
import '@/lib/calendar';

describe('calendar locale configuration', () => {
	it('registers the Italian locale with the correct month names', () => {
		expect(LocaleConfig.locales['it'].monthNames).toEqual([
			'Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno',
			'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre',
		]);
	});

	it('registers the Italian locale with the correct short month names', () => {
		expect(LocaleConfig.locales['it'].monthNamesShort).toEqual([
			'Gen', 'Feb', 'Mar', 'Apr', 'Mag', 'Giu', 'Lug', 'Ago', 'Set', 'Ott', 'Nov', 'Dic',
		]);
	});

	it('registers the Italian locale with the correct day names', () => {
		expect(LocaleConfig.locales['it'].dayNames).toEqual([
			'Domenica', 'Lunedì', 'Martedì', 'Mercoledì', 'Giovedì', 'Venerdì', 'Sabato',
		]);
	});

	it('registers the Italian locale with the correct short day names', () => {
		expect(LocaleConfig.locales['it'].dayNamesShort).toEqual([
			'Dom', 'Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab',
		]);
	});

	it('sets the "today" label to "Oggi"', () => {
		expect(LocaleConfig.locales['it'].today).toBe('Oggi');
	});

	it('sets Italian as the default locale', () => {
		expect(LocaleConfig.defaultLocale).toBe('it');
	});
});