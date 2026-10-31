import Constants from 'expo-constants';
import { FlexWidget, ImageWidget, OverlapWidget, SvgWidget, TextWidget } from 'react-native-android-widget';

import { COLORS } from '@/lib/theme';

import { UserStats } from '@/types/stats';

export type WidgetTheme = 'light' | 'dark';

type HexColor = `#${string}`;

const configuredScheme = Constants.expoConfig?.scheme;
const DEEP_LINK_SCHEME = (Array.isArray(configuredScheme) ? configuredScheme[0] : configuredScheme) ?? 'letstable';

export const DEEP_LINKS = {
	home: `${DEEP_LINK_SCHEME}://home`,
	matches: `${DEEP_LINK_SCHEME}://matches`,
	collection: `${DEEP_LINK_SCHEME}://collection`,
	friends: `${DEEP_LINK_SCHEME}://friends`,
}

const APP_ICONS: Record<WidgetTheme, number> = {
	light: require('../assets/images/widget-icon-light.png'),
	dark: require('../assets/images/widget-icon-dark.png'),
}

const DISPLAY_FONT = 'PlayfairDisplay_700Bold';
const LABEL_FONT = 'PlusJakartaSans_700Bold';
const BODY_FONT = 'PlusJakartaSans_500Medium';

const ICON_PATHS = {
	trophy: '<path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6"/><path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18"/><path d="M4 22h16"/><path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22"/><path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22"/><path d="M18 2H6v7a6 6 0 0 0 12 0V2Z"/>',
	dices: '<rect width="12" height="12" x="2" y="10" rx="2" ry="2"/><path d="m17.92 14 3.5-3.5a2.24 2.24 0 0 0 0-3l-5-4.92a2.24 2.24 0 0 0-3 0L10 6"/><path d="M6 18h.01"/><path d="M10 14h.01"/><path d="M15 6h.01"/><path d="M18 9h.01"/>',
	users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
}

type IconName = keyof typeof ICON_PATHS;

const buildIconSvg = (icon: IconName, color: string) =>
	`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICON_PATHS[icon]}</svg>`;

const buildRingSvg = (percent: number, size: number, strokeWidth: number, trackColor: string, color: string) => {
	const center = size / 2;
	const radius = (size - strokeWidth) / 2;
	const circumference = 2 * Math.PI * radius;
	const progress = (circumference * Math.min(100, Math.max(0, percent))) / 100;

	const track = `<circle cx="${center}" cy="${center}" r="${radius}" fill="none" stroke="${trackColor}" stroke-width="${strokeWidth}"/>`;
	const arc = percent > 0
		? `<circle cx="${center}" cy="${center}" r="${radius}" fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-dasharray="${progress} ${circumference}" transform="rotate(-90 ${center} ${center})"/>`
		: '';

	return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">${track}${arc}</svg>`;
}

const mixColors = (foreground: string, background: string, amount: number): HexColor => {
	const parse = (hex: string) => [1, 3, 5].map(index => Number.parseInt(hex.slice(index, index + 2), 16));
	const [fr, fg, fb] = parse(foreground);
	const [br, bg, bb] = parse(background);
	const channel = (front: number, back: number) => Math.round(front * amount + back * (1 - amount)).toString(16).padStart(2, '0');

	return `#${channel(fr, br)}${channel(fg, bg)}${channel(fb, bb)}`;
}

const getPalette = (theme: WidgetTheme) => {
	const colors = COLORS[theme];

	return {
		background: colors.card as HexColor,
		foreground: colors.foreground as HexColor,
		muted: colors.mutedForeground as HexColor,
		primary: colors.primary as HexColor,
		track: colors.secondary as HexColor,
		border: colors.border as HexColor,
		chip: mixColors(colors.primary, colors.card, 0.15),
		appBackground: colors.background as HexColor,
	}
}

const getWinRate = (stats: UserStats) => stats.totalMatches > 0 ? Math.round((stats.totalWins / stats.totalMatches) * 100) : 0;

const plural = (value: number, singular: string, pluralForm: string) => (value === 1 ? singular : pluralForm);

interface ThemedProps {
	theme: WidgetTheme;
}

interface StatsProps extends ThemedProps {
	stats: UserStats;
}

const AppHeader = ({ theme }: ThemedProps) => {
	const palette = getPalette(theme);

	return (
		<FlexWidget style = {{ flexDirection: 'row', alignItems: 'center', width: 'match_parent' }}>
			<FlexWidget style = {{ width: 18, height: 18, borderRadius: 5, backgroundColor: palette.appBackground, justifyContent: 'center', alignItems: 'center' }}>
				<ImageWidget image = { APP_ICONS[theme] } imageWidth = { 14 } imageHeight = { 14 }/>
			</FlexWidget>
			<TextWidget text = "Let's Table" style = {{ fontSize: 12, fontFamily: LABEL_FONT, color: palette.muted, marginLeft: 6 }}/>
		</FlexWidget>
	)
}

const WidgetContainer = ({ theme, uri, children }: ThemedProps & { uri: string; children: any }) => (
	<FlexWidget
		clickAction = 'OPEN_URI'
		clickActionData = {{ uri }}
		style = {{
			height: 'match_parent',
			width: 'match_parent',
			flexDirection: 'column',
			backgroundColor: getPalette(theme).background,
			borderRadius: 20,
			paddingHorizontal: 14,
			paddingVertical: 10,
		}}
	>
		<AppHeader theme = { theme }/>
		<FlexWidget style = {{ flex: 1, width: 'match_parent', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }}>
			{children}
		</FlexWidget>
	</FlexWidget>
)

const IconChip = ({ icon, theme, size }: ThemedProps & { icon: IconName; size: number }) => {
	const palette = getPalette(theme);
	const iconSize = Math.round(size * 0.55);

	return (
		<FlexWidget style = {{ width: size, height: size, borderRadius: Math.round(size * 0.3), backgroundColor: palette.chip, justifyContent: 'center', alignItems: 'center' }}>
			<SvgWidget svg = { buildIconSvg(icon, palette.primary) } style = {{ width: iconSize, height: iconSize }}/>
		</FlexWidget>
	)
}

const WinRing = ({ percent, theme, size }: ThemedProps & { percent: number; size: number }) => {
	const palette = getPalette(theme);
	const strokeWidth = Math.max(4, Math.round(size * 0.11));

	return (
		<OverlapWidget style = {{ width: size, height: size }}>
			<SvgWidget svg = { buildRingSvg(percent, size, strokeWidth, palette.track, palette.primary) } style = {{ width: size, height: size }}/>
			<FlexWidget style = {{ width: size, height: size, justifyContent: 'center', alignItems: 'center' }}>
				<TextWidget text = { `${percent}%` } style = {{ fontSize: Math.round(size * 0.24), fontFamily: DISPLAY_FONT, color: palette.foreground }}/>
			</FlexWidget>
		</OverlapWidget>
	)
}

const ValueLabel = ({ value, label, theme, size }: ThemedProps & { value: string; label: string; size: number }) => {
	const palette = getPalette(theme);

	return (
		<FlexWidget style = {{ flexDirection: 'column', marginLeft: 10 }}>
			<TextWidget text = { value } style = {{ fontSize: size, fontFamily: DISPLAY_FONT, color: palette.foreground }}/>
			<TextWidget text = { label.toUpperCase() } style = {{ fontSize: 11, fontFamily: LABEL_FONT, color: palette.muted }}/>
		</FlexWidget>
	)
}

const Section = ({ uri, children }: { uri: string; children: any }) => (
	<FlexWidget clickAction = 'OPEN_URI' clickActionData = {{ uri }} style = {{ flex: 1, height: 'match_parent', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }}>
		{children}
	</FlexWidget>
)

const Divider = ({ theme }: ThemedProps) => (
	<FlexWidget style = {{ width: 1, height: 36, backgroundColor: getPalette(theme).border }}/>
)

export const StatsOverviewWidget = ({ stats, theme }: StatsProps) => (
	<WidgetContainer theme = { theme } uri = { DEEP_LINKS.home }>
		<Section uri = { DEEP_LINKS.matches }>
			<WinRing percent = { getWinRate(stats) } theme = { theme } size = { 54 }/>
			<ValueLabel value = { `${stats.totalWins}/${stats.totalMatches}` } label = 'Vittorie' theme = { theme } size = { 22 }/>
		</Section>
		<Divider theme = { theme }/>
		<Section uri = { DEEP_LINKS.collection }>
			<IconChip icon = 'dices' theme = { theme } size = { 40 }/>
			<ValueLabel value = { `${stats.totalGames}` } label = { plural(stats.totalGames, 'Gioco', 'Giochi') } theme = { theme } size = { 22 }/>
		</Section>
		<Divider theme = { theme }/>
		<Section uri = { DEEP_LINKS.friends }>
			<IconChip icon = 'users' theme = { theme } size = { 40 }/>
			<ValueLabel value = { `${stats.totalFriends}` } label = { plural(stats.totalFriends, 'Amico', 'Amici') } theme = { theme } size = { 22 }/>
		</Section>
	</WidgetContainer>
)

export const WinRateWidget = ({ stats, theme }: StatsProps) => (
	<WidgetContainer theme = { theme } uri = { DEEP_LINKS.matches }>
		<WinRing percent = { getWinRate(stats) } theme = { theme } size = { 54 }/>
		<ValueLabel value = { `${stats.totalWins}/${stats.totalMatches}` } label = 'Vittorie' theme = { theme } size = { 24 }/>
	</WidgetContainer>
)

const CountWidget = ({ icon, value, label, uri, theme }: ThemedProps & { icon: IconName; value: number; label: string; uri: string }) => (
	<WidgetContainer theme = { theme } uri = { uri }>
		<IconChip icon = { icon } theme = { theme } size = { 44 }/>
		<ValueLabel value = { `${value}` } label = { label } theme = { theme } size = { 28 }/>
	</WidgetContainer>
)

export const CollectionWidget = ({ stats, theme }: StatsProps) => (
	<CountWidget icon = 'dices' value = { stats.totalGames } label = { plural(stats.totalGames, 'Gioco', 'Giochi') } uri = { DEEP_LINKS.collection } theme = { theme }/>
)

export const FriendsWidget = ({ stats, theme }: StatsProps) => (
	<CountWidget icon = 'users' value = { stats.totalFriends } label = { plural(stats.totalFriends, 'Amico', 'Amici') } uri = { DEEP_LINKS.friends } theme = { theme }/>
)

export const LoggedOutWidget = ({ theme }: ThemedProps) => (
	<WidgetContainer theme = { theme } uri = { DEEP_LINKS.home }>
		<TextWidget text = 'Accedi per vedere le tue statistiche' maxLines = { 2 } style = {{ fontSize: 13, fontFamily: BODY_FONT, color: getPalette(theme).muted, textAlign: 'center' }}/>
	</WidgetContainer>
)