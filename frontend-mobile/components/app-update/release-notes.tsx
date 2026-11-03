import { useMemo } from 'react';
import { View } from 'react-native';
import { AlertTriangle, Bug, LucideIcon, Sparkles, TrendingUp, Wrench } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { parseReleaseNotes } from '@/lib/app-version';

const SECTION_ICONS: Record<string, LucideIcon> = {
	'new features': Sparkles,
	'novità': Sparkles,
	'improvements': TrendingUp,
	'miglioramenti': TrendingUp,
	'fixes & improvements': Wrench,
	'correzioni e miglioramenti': Wrench,
	'fixes': Bug,
	'correzioni': Bug,
	'breaking changes': AlertTriangle,
	'importante': AlertTriangle,
}

interface ReleaseNotesProps {
	markdown: string;
}

const ReleaseNotes = ({ markdown }: ReleaseNotesProps) => {
	const { colors } = useThemeColors();
	const sections = useMemo(() => parseReleaseNotes(markdown), [markdown]);

	if(sections.length === 0) return null;

	return (
		<View className = 'gap-5'>
			{sections.map(section => {
				const Icon = SECTION_ICONS[section.title.toLowerCase()] ?? Sparkles;

				return (
					<View key = { section.title } className = 'gap-2'>
						<View className = 'flex-row items-center gap-2'>
							<Icon size = { 16 } color = { colors.primary }/>
							<Text className = 'font-display text-base text-foreground'>{section.title}</Text>
						</View>
						<View className = 'gap-2'>
							{section.items.map((item, index) => (
								<View key = { `${section.title}-${index}` } className = 'rounded-xl border border-border bg-background px-3 py-2.5'>
									{item.title && (
										<Text className = 'text-sm font-semibold text-foreground'>{item.title}</Text>
									)}
									<Text className = 'text-sm leading-5 text-muted-foreground'>{item.description}</Text>
								</View>
							))}
						</View>
					</View>
				)
			})}
		</View>
	)
}

export default ReleaseNotes;