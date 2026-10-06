import { ActivityIndicator, ScrollView, View } from 'react-native';
import { RefreshControl } from 'react-native-gesture-handler';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import AchievementList from '@/components/achievements/achievement-list';

import { useMyAchievements } from '@/hooks/use-achievement';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

const AchievementsScreen = () => {
	const { data, isLoading, isError } = useMyAchievements();
	const { colors } = useThemeColors();

	useRefetchOnFocus(['achievements', 'me']);

	const { refreshing, onRefresh } = usePullToRefresh([['achievements', 'me']]);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Traguardi' leftElement = { <BackButton/> }/>
			{isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator color = { colors.primary }/>
				</View>
			) : isError || !data ? (
				<View className = 'flex-1 items-center justify-center px-8'>
					<Text className = 'text-center text-sm text-muted-foreground'>Impossibile caricare i traguardi</Text>
				</View>
			) : (
				<ScrollView
					showsVerticalScrollIndicator = { false }
					contentContainerStyle = {{ paddingBottom: 28 }}
					className = 'flex-1 px-4 pt-2'
					refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }
				>
					<AchievementList achievements = { data }/>
				</ScrollView>
			)}
		</View>
	)
}

export default AchievementsScreen;