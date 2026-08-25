import { useState } from 'react';
import { ActivityIndicator, Linking, Pressable, View } from 'react-native';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import * as DocumentPicker from 'expo-document-picker';
import { router, useLocalSearchParams } from 'expo-router';
import * as IntentLauncher from 'expo-intent-launcher';
import * as FileSystem from 'expo-file-system/legacy';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Users, Clock, Calendar, Trophy, Dices, ExternalLink, FileText, Plus, UserCheck } from 'lucide-react-native';
import Animated, { useAnimatedScrollHandler, useAnimatedStyle, useSharedValue, interpolate, interpolateColor, Extrapolation, useDerivedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ScrollView } from 'react-native-gesture-handler';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import MeepleIllustration from '@/components/common/meeple-illustration';
import BackButton from '@/components/common/back-button';
import FabMenu from '@/components/common/fab-menu';
import AccordionItem from '@/components/common/accordion-item';

import { getGameByBggId, getGameExpansions } from '@/api/game';
import { downloadRuleFile, listGameRules, uploadGameRule } from '@/api/game-rules';

import { useToast } from '@/contexts/toast-context';

const IMAGE_HEIGHT = 280;
const SHEET_RADIUS = 28;
const SHEET_OVERLAP = 32;
const BADGE_SIZE = 48;

const GameDetailScreen = () => {
	const insets = useSafeAreaInsets();
	const { bggId } = useLocalSearchParams<{ bggId: string }>();
	const scrollY = useSharedValue(0);

	const queryClient = useQueryClient();
	const { showToast } = useToast();

	const [shouldFetchExpansions, setShouldFetchExpansions] = useState(false);
	const [shouldFetchRules, setShouldFetchRules] = useState(false);

	const HEADER_HEIGHT = insets.top + 56;
	const COLLAPSE_DISTANCE = IMAGE_HEIGHT - SHEET_OVERLAP - HEADER_HEIGHT;

	const headerProgress = useDerivedValue(() =>
		interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [0, 1], Extrapolation.CLAMP)
	);

	const { data: game, isLoading } = useQuery({
		queryKey: ['games', 'detail', bggId],
		queryFn: () => getGameByBggId(Number(bggId)),
	});

	const { data: expansionsData, isLoading: isLoadingExpansions, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
		queryKey: ['games', 'expansions', bggId],
		queryFn: ({ pageParam }) => getGameExpansions(Number(bggId), pageParam),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: shouldFetchExpansions,
	});

	const expansions = expansionsData?.pages.flatMap(page => page.content) ?? [];

	const { data: ruleFiles, isLoading: isLoadingRules } = useQuery({
		queryKey: ['games', 'rules', game?.id],
		queryFn: () => listGameRules(game!.id),
		enabled: shouldFetchRules && !!game?.id,
	});

	const handleVisitBgg = () => {
		Linking.openURL(`https://boardgamegeek.com/boardgame/${bggId}`);
	}

	const handleOpenRuleFile = async (fileId: string, fileName: string) => {
		try {
			const localUri = await downloadRuleFile(game!.id, fileId, fileName);
			const contentUri = await FileSystem.getContentUriAsync(localUri);

			await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
				data: contentUri,
				flags: 1,
				type: 'application/pdf',
			});
		} catch (error) {
			showToast('Impossibile aprire il file', 'error');
		}
	}

	const handleUploadRule = async () => {
		const result = await DocumentPicker.getDocumentAsync({ type: 'application/pdf' });
		if(result.canceled) return;

		const asset = result.assets[0];
		
		try {
			await uploadGameRule(game!.id, asset.uri, asset.name);
			queryClient.invalidateQueries({ queryKey: ['games', 'rules', game!.id] });
			
			showToast('Regolamento caricato con successo', 'success');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante il caricamento';
			showToast(message, 'error');
		}
	}

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
				<ScreenHeader title = 'Dettagli Gioco' titleStyle = { titleColorStyle } renderBackground = { <Animated.View style = { [{ flex: 1 }, headerBackgroundStyle] } className = 'bg-background'/> } leftElement = { <BackButton progress = { headerProgress }/> }/>
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
					<View className='mt-4 rounded-2xl border border-border bg-white shadow-sm overflow-hidden'>
						<View className = 'flex-row items-center justify-around p-3'>
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
									{game.playingTimeMinutes}{' '}
									<Text className = 'font-sans text-xs text-muted-foreground'>min</Text>
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
								<Text className = 'font-display text-lg'>{game.yearPublished}</Text>
							</View>
							)}
						</View>
						<View className = 'flex-row items-center border-t border-border/50 bg-background py-2.5'>
							{game.bestWith && (
								<View className = 'flex-1 flex-row items-center justify-center gap-1.5 px-1'>
									<UserCheck size = { 13 } color = '#736E65' strokeWidth = { 2 }/>
									<Text  className = 'font-sans text-xs text-muted-foreground pb-1 leading-normal' style = {{ includeFontPadding: false }}>
										Ideale: <Text className = 'font-sans-semibold text-foreground'>{game.bestWith}</Text>
									</Text>
								</View>
							)}
							{game.bestWith && game.recommendedWith && game.recommendedWith !== game.bestWith && (
								<View className = 'h-4 w-[1px] bg-border'/>
							)}
							{game.recommendedWith && game.recommendedWith !== game.bestWith && (
								<View className = 'flex-1 flex-row items-center justify-center gap-1.5 px-1'>
									<Users size = { 13 } color = '#736E65' strokeWidth = { 2 }/>
									<Text className = 'font-sans text-xs text-muted-foreground pb-1 leading-normal' style = {{ includeFontPadding: false }}>
										Consigliato: <Text className='font-sans-semibold text-foreground'>{game.recommendedWith}</Text>
									</Text>
								</View>
							)}
						</View>
					</View>
					<View className = 'mt-4'>
						<AccordionItem title = 'Regole' onFirstOpen = { () => setShouldFetchRules(true) }>
							<Pressable onPress = { handleVisitBgg } className = 'flex-row items-center gap-2 rounded-xl border border-border bg-secondary px-3 py-2.5'>
								<ExternalLink size = { 16 } color = '#C45135'/>
								<Text className = 'text-sm font-medium text-foreground'>Visita su BoardGameGeek</Text>
							</Pressable>
							{isLoadingRules ? (
								<View className = 'items-center py-4'>
									<ActivityIndicator color = '#C45135'/>
								</View>
							) : (
								<View className = 'mt-3 gap-2'>
									{ruleFiles?.map(ruleFile => (
										<Pressable key = { ruleFile.id } onPress = { () => handleOpenRuleFile(ruleFile.id, ruleFile.fileName) } className = 'flex-row items-center gap-2 rounded-xl border border-border px-3 py-2.5 active:bg-black/20'>
											<FileText size = { 16 } color = '#736E65'/>
											<View className = 'flex-1'>
												<Text className = 'text-sm text-foreground' numberOfLines = { 1 }>
													{ruleFile.fileName}
												</Text>
												{ruleFile.uploadedByUsername && (
													<Text className = 'text-xs text-muted-foreground'>
														Caricato da {ruleFile.uploadedByUsername}
													</Text>
												)}
											</View>
										</Pressable>
									))}
									<Pressable onPress = { handleUploadRule } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
										{({ pressed }) => (
											<>
												<Plus size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
												<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
													Carica un regolamento
												</Text>
											</>
										)}
									</Pressable>
								</View>
							)}
						</AccordionItem>
						<AccordionItem title = { `Espansioni (${game.expansions ?? 0})` } onFirstOpen = { () => setShouldFetchExpansions(true) }>
							{isLoadingExpansions ? (
								<View className = 'items-center py-4'>
									<ActivityIndicator color = '#C45135'/>
								</View>
							) : expansions && expansions.length > 0 ? (
								<ScrollView horizontal showsHorizontalScrollIndicator = { false } contentContainerStyle = {{ gap: 12, paddingVertical: 4 }}
									onScroll = { ({ nativeEvent }) => {
										const isCloseToEnd = nativeEvent.layoutMeasurement.width + nativeEvent.contentOffset.x >= nativeEvent.contentSize.width - 100;
										if(isCloseToEnd && hasNextPage && !isFetchingNextPage) fetchNextPage();
									}}
									scrollEventThrottle = { 200 }
								>
									{expansions.map((expansion) => (
										<Pressable key = { expansion.bggId } onPress = { () => router.push(`/game/${expansion.bggId}`) } className = 'w-32 active:opacity-80'>
											<View className = 'h-28 w-28 overflow-hidden rounded-xl shadow-sm'>
												{expansion.thumbnailUrl ? (
													<Image source = {{ uri: expansion.thumbnailUrl }} style = {{ width: '100%', height: '100%' }} contentFit = 'cover'/>
												) : (
													<View className = 'h-full w-full items-center justify-center'>
														<Dices size = { 20 } color = '#736E65'/>
													</View>
												)}
											</View>
											<Text style = {{ width: 114 }} className = 'mt-1.5 font-sans-medium text-sm leading-4'>
												{expansion.name}
											</Text>
											{expansion.yearPublished != null && (
												<Text className = 'mt-1 text-xs text-muted-foreground'>{expansion.yearPublished}</Text>
											)}
										</Pressable>
									))}
									{isFetchingNextPage && (
										<View className = 'w-28 items-center justify-center'>
												<ActivityIndicator size = 'small' color = '#C45135'/>
											</View>
										)}
								</ScrollView>
							) : (
								<Text className = 'py-2 text-sm text-muted-foreground'>Nessuna espansione trovata.</Text>
							)}
						</AccordionItem>
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