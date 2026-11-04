import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { ChevronRight, Cog, LucideIcon, Shapes, Tag } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { GameTerm } from '@/types/game';

export type GameTermType = 'mechanic' | 'category';

const COLLAPSED_LIMIT = 8;

interface GameTermsCardProps {
	mechanics: GameTerm[];
	categories: GameTerm[];
	onPress: (type: GameTermType, term: GameTerm) => void;
}

interface TermSectionProps {
	label: string;
	icon: LucideIcon;
	terms: GameTerm[];
	onPress: (term: GameTerm) => void;
}

const TermSection = ({ label, icon: Icon, terms, onPress }: TermSectionProps) => {
	const [isExpanded, setIsExpanded] = useState(false);

	const { colors } = useThemeColors();

	const hiddenCount = terms.length - COLLAPSED_LIMIT;
	const visibleTerms = isExpanded || hiddenCount <= 0 ? terms : terms.slice(0, COLLAPSED_LIMIT);

	return (
		<View>
			<View className = 'mb-2 flex-row items-center gap-2'>
				<Icon size = { 14 } color = { colors.mutedForeground }/>
				<Text className = 'text-xs text-muted-foreground'>{label}</Text>
			</View>
			<View className = 'flex-row flex-wrap gap-2'>
				{visibleTerms.map(term => (
					<Pressable key = { term.id } onPress = { () => onPress(term) } className = 'flex-row items-center gap-1 rounded-full border border-border/40 bg-secondary/80 py-1.5 pl-3 pr-2 active:opacity-75 active:scale-[0.97]'>
						<Text className = 'text-xs font-medium text-foreground'>{term.name}</Text>
						<ChevronRight size = { 12 } color = { colors.mutedForeground }/>
					</Pressable>
				))}
				{hiddenCount > 0 && (
					<Pressable onPress = { () => setIsExpanded(previous => !previous) } className = 'rounded-full px-3 py-1.5 active:opacity-75'>
						<Text className = 'text-xs font-medium text-primary'>
							{isExpanded ? 'Mostra meno' : `+${hiddenCount} altre`}
						</Text>
					</Pressable>
				)}
			</View>
		</View>
	)
}

const GameTermsCard = ({ mechanics, categories, onPress }: GameTermsCardProps) => {
	const { colors } = useThemeColors();

	if(mechanics.length === 0 && categories.length === 0) return null;

	return (
		<View className = 'rounded-2xl border border-border bg-card p-4'>
			<View className = 'mb-3 flex-row items-center gap-2'>
				<Shapes size = { 16 } color = { colors.primary }/>
				<Text className = 'font-display text-base text-foreground'>Gameplay</Text>
			</View>
			<View className = 'gap-4'>
				{categories.length > 0 && (
					<TermSection label = 'Categorie' icon = { Tag } terms = { categories } onPress = { term => onPress('category', term) }/>
				)}
				{mechanics.length > 0 && (
					<TermSection label = 'Meccaniche' icon = { Cog } terms = { mechanics } onPress = { term => onPress('mechanic', term) }/>
				)}
			</View>
		</View>
	)
}

export default GameTermsCard;