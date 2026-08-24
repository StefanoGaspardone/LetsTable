import { View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Users, Clock, Calendar, Trophy } from 'lucide-react-native';
import Animated, { useAnimatedScrollHandler, useAnimatedStyle, useSharedValue, interpolate, interpolateColor, Extrapolation } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import MeepleIllustration from '@/components/common/meeple-illustration';
import BackButton from '@/components/common/back-button';

import { getGameByBggId } from '@/api/game';
import FabMenu from '@/components/common/fab-menu';

const IMAGE_HEIGHT = 280;
const SHEET_RADIUS = 28;
const SHEET_OVERLAP = 32;
const BADGE_SIZE = 48;

const GameDetailScreen = () => {
	const insets = useSafeAreaInsets();
	const { bggId } = useLocalSearchParams<{ bggId: string }>();
	const scrollY = useSharedValue(0);

	const HEADER_HEIGHT = insets.top + 56;
	const COLLAPSE_DISTANCE = IMAGE_HEIGHT - SHEET_OVERLAP - HEADER_HEIGHT;

	const { data: game, isLoading } = useQuery({
		queryKey: ['games', 'detail', bggId],
		queryFn: () => getGameByBggId(Number(bggId)),
	});

	const scrollHandler = useAnimatedScrollHandler({
		onScroll: (event) => {
			scrollY.value = event.contentOffset.y;
		},
	});

	const headerBackgroundStyle = useAnimatedStyle(() => ({
		opacity: interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [0, 1], Extrapolation.CLAMP),
	}));

	const titleColorStyle = useAnimatedStyle(() => ({
		color: interpolateColor(scrollY.value, [0, COLLAPSE_DISTANCE], ['#FFFFFF', '#1E1C1A']),
	}));

	const scrimStyle = useAnimatedStyle(() => ({
		opacity: interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [1, 0], Extrapolation.CLAMP),
	}));

	if(isLoading || !game) {
		return <View className = 'flex-1 bg-background'/>;
	}

	return (
		<View className = 'flex-1 bg-background'>
			<Image source = {{ uri: game.imageUrl ?? game.thumbnailUrl ?? undefined }} style = {{ position: 'absolute', top: 0, left: 0, right: 0, height: IMAGE_HEIGHT }} contentFit = 'cover'/>
			<Animated.View style = { [{ position: 'absolute', top: 0, left: 0, right: 0, height: HEADER_HEIGHT }, scrimStyle] }>
				<LinearGradient colors = { ['rgba(0,0,0,0.45)', 'rgba(0,0,0,0)'] } style = {{ flex: 1 }}/>
			</Animated.View>
			<View style = {{ position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10 }}>
				<ScreenHeader title = 'Dettagli Gioco' titleStyle = { titleColorStyle } renderBackground = { <Animated.View style = { [{ flex: 1 }, headerBackgroundStyle] } className = 'border-b border-border bg-background'/> } leftElement = { <BackButton/> }
				/>
			</View>
			<Animated.ScrollView onScroll = { scrollHandler } scrollEventThrottle = { 16 } contentContainerStyle = {{ paddingBottom: 60 }}>
				<View style = {{ height: IMAGE_HEIGHT - SHEET_OVERLAP }}/>
				<View style = {{ borderTopLeftRadius: SHEET_RADIUS, borderTopRightRadius: SHEET_RADIUS }} className = 'bg-background px-4 pb-6 pt-6'>
					<View style = {{ position: 'absolute', top: -BADGE_SIZE / 2, right: 24, width: BADGE_SIZE, height: BADGE_SIZE }} className = 'items-center justify-center rounded-full border-2 border-background bg-[#C45135] shadow-lg'>
						<MeepleIllustration size = { 28 } color = '#FFFFFF'/>
					</View>
					<Text className = 'font-display text-2xl text-foreground mt-2'>{game.name}</Text>
					{game.description && (
						<Text className = 'mt-2 text-sm leading-5 text-muted-foreground'>{game.description}</Text>
					)}
					<View className = 'mt-4 flex-row items-center justify-around rounded-2xl border border-border bg-white p-3 shadow-sm'>
						{game.minPlayers && game.maxPlayers && (
							<View className = 'flex-1 items-center px-2'>
								<View className = 'flex-row items-center gap-1.5 mb-1'>
									<Users size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
									<Text className = 'font-sans-bold text-[11px] uppercase tracking-wider text-muted-foreground'>
									Giocatori
									</Text>
								</View>
								<Text className = 'font-display text-lg'>
									{game.minPlayers === game.maxPlayers ? game.minPlayers : `${game.minPlayers}-${game.maxPlayers}`}
								</Text>
							</View>
						)}
						{game.minPlayers && game.playingTimeMinutes && (
							<View className = 'h-8 w-[1px] bg-border'/>
						)}
						{game.playingTimeMinutes && (
							<View className = 'flex-1 items-center px-2'>
								<View className = 'flex-row items-center gap-1.5 mb-1'>
									<Clock size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
									<Text className = 'font-sans-bold text-[11px] uppercase tracking-wider text-muted-foreground'>
										Durata
									</Text>
								</View>
								<Text className = 'font-display text-lg'>
									{game.playingTimeMinutes} <Text className = 'font-sans text-xs text-muted-foreground'>min</Text>
								</Text>
							</View>
						)}
						{game.playingTimeMinutes && game.yearPublished && (
							<View className = 'h-8 w-[1px] bg-border'/>
						)}
						{game.yearPublished && (
							<View className = 'flex-1 items-center px-2'>
								<View className = 'flex-row items-center gap-1.5 mb-1'>
									<Calendar size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
									<Text className = 'font-sans-bold text-[11px] uppercase tracking-wider text-muted-foreground'>
										Anno
									</Text>
								</View>
								<Text className = 'font-display text-lg'>
									{game.yearPublished}
								</Text>
							</View>
						)}
					</View>
				</View>
			</Animated.ScrollView>
			<FabMenu actions = { [
				{
					label: 'Registra partita',
					icon: <Trophy size = { 18 } color = '#1c1b1a'/>,
					onPress: () => {},
				},
			] }/>
		</View>
	)
}

export default GameDetailScreen;