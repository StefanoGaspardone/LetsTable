import { View } from 'react-native';
import { Award, CalendarCheck, Compass, Dices, Flame, Frown, Library, Trophy, UserPlus, Users, type LucideIcon } from 'lucide-react-native';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { AchievementTier } from '@/types/achievement';

const ICONS: Record<string, LucideIcon> = {
	'dices': Dices,
	'trophy': Trophy,
	'library': Library,
	'compass': Compass,
	'user-plus': UserPlus,
	'users': Users,
	'frown': Frown,
	'calendar-check': CalendarCheck,
	'flame': Flame,
};

const TIER_COLORS: Record<AchievementTier, string> = {
	BRONZE: '#b45309',
	SILVER: '#64748b',
	GOLD: '#ca8a04',
};

interface AchievementIconProps {
	icon: string;
	tier: AchievementTier | null;
	unlocked: boolean;
	size?: number;
}

const AchievementIcon = ({ icon, tier, unlocked, size = 22 }: AchievementIconProps) => {
	const { colors } = useThemeColors();

	const Icon = ICONS[icon] ?? Award;
	const color = !unlocked ? colors.mutedForeground : tier ? TIER_COLORS[tier] : colors.primary;
	const boxSize = size * 2;

	return (
		<View style = {{ width: boxSize, height: boxSize, borderRadius: boxSize / 2, backgroundColor: `${color}22`, opacity: unlocked ? 1 : 0.6 }} className = 'items-center justify-center'>
			<Icon size = { size } color = { color }/>
		</View>
	)
}

export default AchievementIcon;