import { View, Pressable } from 'react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface MatchesCalendarDayProps {
    date?: { day: number; dateString: string };
    state?: 'disabled' | 'today' | '';
    marking?: { count?: number; selected?: boolean };
    onPress?: (date: any) => void;
}

const MatchesCalendarDay = ({ date, state, marking, onPress }: MatchesCalendarDayProps) => {
    const { colors } = useThemeColors();

    if(!date) return null;

    const count = marking?.count ?? 0;
    const isSelected = marking?.selected ?? false;
    const isToday = state === 'today';
    const isDisabled = state === 'disabled';

    return (
        <Pressable onPress = { () => onPress?.(date) } className = 'items-center justify-center p-0.5'>
            <View className = 'h-7 w-7 items-center justify-center rounded-full'
                style = {[
                    isSelected && { backgroundColor: colors.primary },
                    isToday && !isSelected && { backgroundColor: `${colors.primary}B3` },
                ]}
			>
                <Text className = { `text-sm ${isSelected ? 'font-semibold text-primary-foreground' : isDisabled ? 'text-muted-foreground/40' : isToday ? 'font-semibold text-primary-foreground' : 'text-foreground'}` }>
                    {date.day}
                </Text>
            </View>
			<View className = 'mt-0.5 h-4 min-w-4 items-center justify-center rounded-full px-1' style = {{ backgroundColor: count > 0 ? `${colors.primary}26` : 'transparent' }}>
                {count > 0 && (
                    <Text className='text-[9px] font-bold text-primary'>
                        {count > 9 ? '9+' : count}
                    </Text>
                )}
            </View>
        </Pressable>
    )
}

export default MatchesCalendarDay;