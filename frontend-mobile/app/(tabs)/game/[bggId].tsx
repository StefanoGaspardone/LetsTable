import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Dimensions, Linking, Pressable, View } from 'react-native';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import * as DocumentPicker from 'expo-document-picker';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as IntentLauncher from 'expo-intent-launcher';
import * as FileSystem from 'expo-file-system/legacy';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Users, Clock, Calendar, Trophy, Dices, ExternalLink, Plus, UserCheck, ArrowLeftRight, Puzzle, Gauge, PenTool, Palette, Building2, Trash2, Library, Check, Heart, Layers, Ruler, Download, Award } from 'lucide-react-native';
import Animated, { useAnimatedScrollHandler, useAnimatedStyle, useSharedValue, interpolate, interpolateColor, Extrapolation, useDerivedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ScrollView } from 'react-native-gesture-handler';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import MeepleIllustration from '@/components/common/meeple-illustration';
import BackButton from '@/components/common/back-button';
import FabMenu from '@/components/common/fab-menu';
import SegmentedControl from '@/components/common/segmented-control';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';
import GameListItem from '@/components/common/game-list-item';
import MatchListItem from '@/components/common/match-list-item';

import { getGameByBggId, getGameExpansions } from '@/api/game';
import { downloadRuleFile, listGameRules, uploadGameRule } from '@/api/game-rules';
import { listMatches } from '@/api/match';

import { useToast } from '@/contexts/toast-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useCollectionStatus, useToggleCollection } from '@/hooks/use-game';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { getFileIconColor, getFileIconName } from '@/lib/file';

const IMAGE_HEIGHT = 280;
const SHEET_RADIUS = 28;
const SHEET_OVERLAP = 32;
const BADGE_SIZE = 48;
const SCREEN_WIDTH = Dimensions.get('window').width;
const INFINITE_SCROLL_THRESHOLD = 400;

type TabKey = 'info' | 'file' | 'expansions' | 'matches';

const GameDetailScreen = () => {
	const insets = useSafeAreaInsets();
	const { bggId } = useLocalSearchParams<{ bggId: string }>();

	const scrollY = useSharedValue(0);
	const contentHeight = useSharedValue(0);
	const layoutHeight = useSharedValue(0);
	const inlineTabBarY = useSharedValue(0);
	const [measuredHeaderHeight, setMeasuredHeaderHeight] = useState(insets.top + 56);

	const queryClient = useQueryClient();

	const { showToast } = useToast();
	const router = useNavigationStack();
	const { colors } = useThemeColors();
	
	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);
	const pagerRef = useRef<ScrollView>(null);
	const mainScrollRef = useRef<Animated.ScrollView>(null);
	const isReturningFromExternalActionRef = useRef(false);

	const [activeTab, setActiveTab] = useState<TabKey>('info');
	const [tabHeights, setTabHeights] = useState<Record<TabKey, number>>({
		info: 300,
		file: 300,
		expansions: 300,
		matches: 300,
	});
	const [shouldFetchExpansions, setShouldFetchExpansions] = useState(false);
	const [shouldFetchRules, setShouldFetchRules] = useState(false);
	const [shouldFetchMatches, setShouldFetchMatches] = useState(false);

	const HEADER_HEIGHT = measuredHeaderHeight;
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

	const { data: matchesData, isLoading: isLoadingMatches, fetchNextPage: fetchNextMatchesPage, hasNextPage: hasNextMatchesPage, isFetchingNextPage: isFetchingNextMatchesPage } = useInfiniteQuery({
		queryKey: ['matches', 'list', game?.id ?? null],
		queryFn: ({ pageParam }) => listMatches({ page: pageParam, size: 20, gameId: game!.id, sort: 'playedAt-desc' }),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: shouldFetchMatches && !!game?.id,
	});

	const matches = matchesData?.pages.flatMap(page => page.content) ?? [];

	const { data: ruleFiles, isLoading: isLoadingRules } = useQuery({
		queryKey: ['games', 'rules', game?.id],
		queryFn: () => listGameRules(game!!.id),
		enabled: shouldFetchRules && !!game?.id,
	});

	const handleVisitBgg = () => {
		Linking.openURL(`https://boardgamegeek.com/boardgame/${bggId}`);
	}

	const { data: collectionStatus } = useCollectionStatus(game?.id);
	const toggleCollection = useToggleCollection();

	useRefetchOnFocus(['games', 'detail', bggId]);
	useRefetchOnFocus(['collection', 'status', game?.id]);

	const handleOpenRuleFile = async (fileId: string, fileName: string, contentType: string) => {
		isReturningFromExternalActionRef.current = true;

		try {
			const localUri = await downloadRuleFile(game!.id, fileId, fileName);
			const contentUri = await FileSystem.getContentUriAsync(localUri);

			await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
				data: contentUri,
				flags: 1,
				type: contentType,
			});
		} catch {
			isReturningFromExternalActionRef.current = false;
			showToast('Impossibile aprire il file', 'error');
		}
	}

	const handleUploadRule = async () => {
		isReturningFromExternalActionRef.current = true;

		const result = await DocumentPicker.getDocumentAsync({
			type: [
				'application/pdf',
				'application/msword',
				'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
				'application/vnd.ms-excel',
				'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
				'application/vnd.ms-powerpoint',
				'application/vnd.openxmlformats-officedocument.presentationml.presentation',
				'image/*',
				'video/*',
			],
		});

		if(result.canceled) return;

		const asset = result.assets[0];

		try {
			await uploadGameRule(game!.id, asset.uri, asset.name, asset.mimeType ?? 'application/octet-stream');
			queryClient.invalidateQueries({ queryKey: ['games', 'rules', game!.id] });

			showToast('Regolamento caricato con successo', 'success');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante il caricamento';
			showToast(message, 'error');
		}
	}

	const TABS: { key: TabKey; label: string }[] = [
		{ key: 'info', label: 'Info' },
		{ key: 'file', label: 'File' },
		...(!game?.isExpansion ? [{ key: 'expansions' as TabKey, label: `Espansioni` }] : []),
		{ key: 'matches', label: `Partite` },
	]

	const activateTab = (key: TabKey) => {
		setActiveTab(key);
		
		if(key === 'file' && !shouldFetchRules) setShouldFetchRules(true);
		if(key === 'expansions' && !shouldFetchExpansions) setShouldFetchExpansions(true);
		if(key === 'matches' && !shouldFetchMatches) setShouldFetchMatches(true);
	}

	const handleTabPress = (value: string) => {
		const key = value as TabKey;
		const index = TABS.findIndex(t => t.key === key);
		
		pagerRef.current?.scrollTo({ x: index * SCREEN_WIDTH, animated: true });
		
		activateTab(key);
	}

	const handlePagerScrollEnd = (e: any) => {
		const index = Math.round(e.nativeEvent.contentOffset.x / SCREEN_WIDTH);
		const key = TABS[index]?.key;
		
		if(key) activateTab(key);
	}

	const loadMoreExpansions = () => {
		if(activeTab === 'expansions' && hasNextPage && !isFetchingNextPage) fetchNextPage();
		if(activeTab === 'matches' && hasNextMatchesPage && !isFetchingNextMatchesPage) fetchNextMatchesPage();
	}

	const scrollHandler = useAnimatedScrollHandler({
		onScroll: (event) => {
			scrollY.value = event.contentOffset.y;

			const distanceFromBottom = contentHeight.value - (event.contentOffset.y + layoutHeight.value);
			if(distanceFromBottom < INFINITE_SCROLL_THRESHOLD) {
				scheduleOnRN(loadMoreExpansions);
			}
		},
	});

	const headerBackgroundStyle = useAnimatedStyle(() => ({
		opacity: interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [0, 1], Extrapolation.CLAMP),
	}));

	const titleColorStyle = useAnimatedStyle(() => ({
		color: interpolateColor(
			scrollY.value, 
			[0, COLLAPSE_DISTANCE], 
			['#FFFFFF', colors.foreground]
		),
	}));

	const scrimStyle = useAnimatedStyle(() => ({
		opacity: interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [1, 0], Extrapolation.CLAMP),
	}));

	const stickyTabBarStyle = useAnimatedStyle(() => {
		const triggerPoint = inlineTabBarY.value - HEADER_HEIGHT;
		const fadeRange = 24;

		return {
			opacity: interpolate(scrollY.value, [triggerPoint, triggerPoint + fadeRange], [0, 1], Extrapolation.CLAMP),
		};
	});

	const handleToggleCollection = () => {
		const wasInCollection = collectionStatus?.inCollection;
		toggleCollection.mutate(
			{ gameId: game!.id, itemId: collectionStatus?.itemId ?? null },
			{
				onSuccess: () => {
					queryClient.invalidateQueries({ queryKey: ['collection', 'status', game!.id] });
					showToast(wasInCollection ? 'Rimosso dalla collezione' : 'Aggiunto alla collezione', 'success');
				},
				onError: (error: any) => {
					const message = error?.response?.data?.message ?? 'Errore durante l\'operazione';
					showToast(message, 'error');
				},
			}
		);
	}

	useFocusEffect(
		useCallback(() => {
			if(isReturningFromExternalActionRef.current) {
				isReturningFromExternalActionRef.current = false;
				return;
			}

			setActiveTab('info');
			setShouldFetchExpansions(false);
			setShouldFetchRules(false);
			setShouldFetchMatches(false);

			mainScrollRef.current?.scrollTo({ y: 0, animated: false });
			pagerRef.current?.scrollTo({ x: 0, animated: false });

			scrollY.value = 0;
		}, [bggId])
	);

	if(isLoading || !game) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = { colors.primary }/>
			</View>
		)
	}

	const hasRecommendations = Boolean(game.bestWith || (game.recommendedWith && game.recommendedWith !== game.bestWith));
	const hasCredits = game.designers.length > 0 || game.artists.length > 0 || game.publishers.length > 0;

	return (
		<View className = 'flex-1 bg-background'>
			<Image source = {{ uri: game.imageUrl ?? game.thumbnailUrl ?? undefined }} style = {{ position: 'absolute', top: 0, left: 0, right: 0, height: IMAGE_HEIGHT }} contentFit = 'cover'/>
			<Animated.View style = { [{ position: 'absolute', top: 0, left: 0, right: 0, height: HEADER_HEIGHT }, scrimStyle] }>
				<LinearGradient colors = { ['rgba(0,0,0,0.45)', 'rgba(0,0,0,0)'] } style = {{ flex: 1 }}/>
			</Animated.View>
			<Animated.View pointerEvents = { activeTab ? 'box-none' : 'none' } style = { [{ position: 'absolute', top: HEADER_HEIGHT, left: 0, right: 0, zIndex: 9 }, stickyTabBarStyle] }>
				<View className = 'bg-background px-4 pb-2 pt-1 border-b border-border/50'>
					<SegmentedControl options = { TABS.map(t => ({ value: t.key, label: t.label })) } selected = { activeTab } onSelect = { handleTabPress }/>
				</View>
			</Animated.View>
			<View style = {{ position: 'absolute', top: 0, left: 0, right: 0, zIndex: 10 }} onLayout = { e => setMeasuredHeaderHeight(e.nativeEvent.layout.height) }>
				<ScreenHeader title = 'Dettagli Gioco' titleStyle = { titleColorStyle } renderBackground = { <Animated.View style = { [{ flex: 1 }, headerBackgroundStyle] } className = 'bg-background'/> } leftElement = { <BackButton progress = { headerProgress }/> }/>
			</View>
			<Animated.ScrollView ref = { mainScrollRef } onScroll = { scrollHandler } scrollEventThrottle = { 16 } onLayout = { e => { layoutHeight.value = e.nativeEvent.layout.height; } } onContentSizeChange = { (_, height) => { contentHeight.value = height; } }>
				<View style = {{ height: IMAGE_HEIGHT - SHEET_OVERLAP }}/>
				<View style = {{ borderTopLeftRadius: SHEET_RADIUS, borderTopRightRadius: SHEET_RADIUS }} className = 'bg-background pb-6 pt-6'>
					<View style = {{ position: 'absolute', top: -BADGE_SIZE / 2, right: 24, width: BADGE_SIZE, height: BADGE_SIZE }} className = 'items-center justify-center rounded-full border-2 border-background bg-primary shadow-lg'>
						<MeepleIllustration size = { 28 } color = '#FFFFFF'/>
					</View>
					<View className = 'px-4'>
						<View className = 'mt-2 flex-row items-start gap-3'>
							<Text className = 'flex-1 font-display text-2xl text-foreground'>{game.name}</Text>
							<View className = 'flow-col'>
								{collectionStatus?.inCollection && (
									<View className = 'mt-1.5 flex-row items-center gap-1 rounded-full bg-primary/10 px-2 py-1'>
										<Check size = { 13 } color = { colors.primary } strokeWidth = { 2.5 }/>
										<Text className = 'text-xs font-medium text-primary'>Posseduto</Text>
									</View>
								)}
								{game.isExpansion && (
									<View className = 'mt-1.5 flex-row items-center gap-1 rounded-full bg-primary/10 px-2 py-1'>
										<Puzzle size = { 13 } color = { colors.primary } strokeWidth = { 2.5 }/>
										<Text className = 'text-xs font-medium text-primary'>Espansione</Text>
									</View>
								)}
							</View>
						</View>
						{game.isExpansion && game.baseGame && (
							<Pressable onPress = { () => router.push(`/game/${game.baseGame!.bggId}`) } className = 'mt-4 flex-row items-center gap-3 rounded-2xl border border-border bg-card p-3 active:opacity-80'>
								<View style = {{ width: 48, height: 48 }} className = 'overflow-hidden rounded-xl bg-secondary'>
									{game.baseGame.thumbnailUrl ? (
										<Image source = {{ uri: game.baseGame.thumbnailUrl }} style = {{ width: 48, height: 48 }} contentFit = 'cover'/>
									) : (
										<View className = 'h-full w-full items-center justify-center'>
											<Dices size = { 18 } color = '#736E65'/>
										</View>
									)}
								</View>
								<View className = 'flex-1'>
									<Text className = 'text-xs font-medium text-muted-foreground'>Gioco base</Text>
									<Text className = 'font-semibold text-sm text-foreground' numberOfLines = { 1 }>
										{game.baseGame.name}
									</Text>
								</View>
								<ArrowLeftRight size = { 16 } color = { colors.primary }/>
							</Pressable>
						)}
						<View className = 'mt-4' onLayout = { e => { inlineTabBarY.value = e.nativeEvent.layout.y + IMAGE_HEIGHT - SHEET_OVERLAP; } }>
							<SegmentedControl options = { TABS.map(t => ({ value: t.key, label: t.label })) } selected = { activeTab } onSelect = { handleTabPress }/>
						</View>
					</View>
					<View style = {{ height: tabHeights[activeTab], marginTop: 16 }}>
						<ScrollView ref = { pagerRef } horizontal pagingEnabled showsHorizontalScrollIndicator = { false } onMomentumScrollEnd = { handlePagerScrollEnd } contentContainerStyle = {{ alignItems: 'flex-start' }}>
							<View style = {{ width: SCREEN_WIDTH }} onLayout = { e => { const height = e.nativeEvent?.layout?.height; if(height) setTabHeights(prev => ({ ...prev, info: height })); } }>
								<View className = 'px-4 gap-4'>
									{game.description && (
										<Text className = 'text-sm leading-5 text-muted-foreground'>{game.description}</Text>
									)}
									<View className = 'rounded-2xl border border-border bg-card shadow-sm overflow-hidden'>
										<View className = 'p-3'>
											<View className = 'flex-row items-center justify-around'>
												{game.minPlayers && game.maxPlayers && (
													<View className = 'flex-1 items-center px-2'>
														<View className = 'mb-1 flex-row items-center gap-1.5'>
															<Users size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
															<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
																Giocatori
															</Text>
														</View>
														<Text className = 'font-display text-lg'>
															{game.minPlayers === game.maxPlayers ? game.minPlayers : `${game.minPlayers}-${game.maxPlayers}`}
														</Text>
													</View>
												)}
												{game.minPlayers && game.playingTimeMinutes && <View className = 'h-8 w-[1px] bg-border'/>}
												{game.playingTimeMinutes && (
													<View className = 'flex-1 items-center px-2'>
														<View className = 'mb-1 flex-row items-center gap-1.5'>
															<Clock size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
															<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
																Durata
															</Text>
														</View>
														<Text className = 'font-display text-lg'>
															{game.playingTimeMinutes} <Text className = 'font-sans text-xs text-muted-foreground'>min</Text>
														</Text>
													</View>
												)}
											</View>
											{(game.yearPublished || game.difficulty) && (game.minPlayers || game.playingTimeMinutes) && (
												<View className = 'my-3 h-[0.75px] bg-border'/>
											)}
											<View className = 'flex-row items-center justify-around'>
												{game.yearPublished && (
													<View className = 'flex-1 items-center px-2'>
														<View className = 'mb-1 flex-row items-center gap-1.5'>
															<Calendar size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
															<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
																Anno
															</Text>
														</View>
														<Text className = 'font-display text-lg'>{game.yearPublished}</Text>
													</View>
												)}
												{game.yearPublished && game.difficulty && <View className = 'h-8 w-[1px] bg-border'/>}
												{game.difficulty && (
													<View className = 'flex-1 items-center px-2'>
														<View className = 'mb-1 flex-row items-center gap-1.5'>
															<Gauge size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
															<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
																Difficoltà
															</Text>
														</View>
														<Text className = 'font-display text-lg'>{game.difficulty.toFixed(1)} / 5</Text>
													</View>
												)}
											</View>
										</View>
										{hasRecommendations && (
											<View className = 'flex-row items-center border-t border-border/50 bg-background py-2.5'>
												{game.bestWith && (
													<View className = 'flex-1 flex-row items-center justify-center gap-1.5 px-1'>
														<UserCheck size = { 13 } color = { colors.mutedForeground } strokeWidth = { 2 }/>
														<Text className = 'font-sans text-xs text-muted-foreground pb-1 leading-normal' style = {{ includeFontPadding: false }}>
															Ideale: <Text className = 'font-sans-semibold text-foreground'>{game.bestWith}</Text>
														</Text>
													</View>
												)}
												{game.bestWith && game.recommendedWith && game.recommendedWith !== game.bestWith && (
													<View className = 'h-4 w-[1px] bg-border'/>
												)}
												{game.recommendedWith && game.recommendedWith !== game.bestWith && (
													<View className = 'flex-1 flex-row items-center justify-center gap-1.5 px-1'>
														<Users size = { 13 } color = { colors.mutedForeground } strokeWidth = { 2 }/>
														<Text className = 'font-sans text-xs text-muted-foreground pb-1 leading-normal' style = {{ includeFontPadding: false }}>
															Consigliato: <Text className = 'font-sans-semibold text-foreground'>{game.recommendedWith}</Text>
														</Text>
													</View>
												)}
											</View>
										)}
									</View>
									{game.sleeves.length > 0 && (
										<View className = 'rounded-2xl border border-border bg-card p-4'>
											<View className = 'mb-3 flex-row items-center gap-2'>
												<Layers size = { 16 } color = { colors.primary }/>
												<Text className = 'font-display text-base text-foreground'>Componenti e Sleeve</Text>
											</View>
											<View className = 'gap-2'>
												{game.sleeves.map(sleeve => (
													<View key = { sleeve.id } className = 'flex-row items-center gap-2 rounded-xl bg-background px-3 py-2.5 border border-border'>
														<Ruler size = { 14 } color = { colors.mutedForeground }/>
														<View className = 'flex-1'>
															<Text className = 'text-sm text-foreground'>
																{sleeve.name ?? 'Componente'}
															</Text>
															{sleeve.height != null && sleeve.width != null && (
																<Text className = 'text-xs text-muted-foreground'>
																	{sleeve.width}×{sleeve.height} mm
																	{sleeve.quantity != null && ` · ${sleeve.quantity} pz`}
																	{sleeve.quantityNote ? ` (${sleeve.quantityNote})` : ''}
																</Text>
															)}
														</View>
													</View>
												))}
											</View>
										</View>
									)}
									{hasCredits && (
										<View className = 'rounded-2xl border border-border bg-card p-4'>
											<View className = 'mb-3 flex-row items-center gap-2'>
												<Award size = { 16 } color = { colors.primary }/>
												<Text className = 'font-display text-base text-foreground'>Crediti</Text>
											</View>
											{game.designers.length > 0 && (
												<View className = 'mb-2 flex-row items-start gap-2'>
													<PenTool size = { 14 } color = { colors.mutedForeground } style = {{ marginTop: 1 }}/>
													<View className = 'flex-1'>
														<Text className = 'text-xs text-muted-foreground'>Design</Text>
														<Text className = 'text-sm text-foreground'>{game.designers.join(', ')}</Text>
													</View>
												</View>
											)}
											{game.artists.length > 0 && (
												<View className = 'mb-2 flex-row items-start gap-2'>
													<Palette size = { 14 } color = { colors.mutedForeground } style = {{ marginTop: 1 }}/>
													<View className = 'flex-1'>
														<Text className = 'text-xs text-muted-foreground'>Illustrazioni</Text>
														<Text className = 'text-sm text-foreground'>{game.artists.join(', ')}</Text>
													</View>
												</View>
											)}
											{game.publishers.length > 0 && (
												<View className = 'flex-row items-start gap-2'>
													<Building2 size = { 14 } color = { colors.mutedForeground } style = {{ marginTop: 1 }}/>
													<View className = 'flex-1'>
														<Text className = 'text-xs text-muted-foreground'>Editori</Text>
														<Text className = 'text-sm text-foreground'>{game.publishers.join(', ')}</Text>
													</View>
												</View>
											)}
										</View>
									)}
								</View>
							</View>
							<View style = {{ width: SCREEN_WIDTH }} onLayout = { e => { const height = e.nativeEvent?.layout?.height; if(height) setTabHeights(prev => ({ ...prev, file: height })); } }>
								<View className = 'px-4 gap-2'>
									<Pressable onPress = { handleVisitBgg } className = 'flex-row items-center gap-2 rounded-xl border border-border bg-secondary px-3 py-2.5 active:opacity-75 active:scale-[0.98]'>
										<ExternalLink size = { 16 } color = { colors.primary }/>
										<Text className = 'text-sm font-medium text-foreground'>Visita su BoardGameGeek</Text>
									</Pressable>
									{isLoadingRules ? (
										<View className = 'items-center py-4'>
											<ActivityIndicator color = { colors.primary }/>
										</View>
									) : (
										<>
											{ruleFiles?.map(ruleFile => (
												<Pressable key = { ruleFile.id } onPress = { () => handleOpenRuleFile(ruleFile.id, ruleFile.fileName, ruleFile.contentType) } className = 'flex-row items-center bg-card gap-3 rounded-xl border border-border px-3 py-2.5 active:opacity-75 active:scale-[0.98]'>
													<MaterialCommunityIcons name = { getFileIconName(ruleFile.contentType) } size = { 22 } color = { getFileIconColor(ruleFile.contentType) }/>
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
													<Download size = { 18 } color = { colors.mutedForeground } className = 'ml-auto'/>
												</Pressable>
											))}
											<Pressable onPress = { handleUploadRule } className = 'flex-row items-center justify-center gap-2 rounded-xl border border-dashed active:border-solid border-border px-3 py-2.5 active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
												{({ pressed }) => (
													<>
														<Plus size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
														<Text className = { `text-sm ${pressed ? 'text-white' : 'text-muted-foreground'}` }>
															Carica un regolamento o un file
														</Text>
													</>
												)}
											</Pressable>
										</>
									)}
								</View>
							</View>
							{!game.isExpansion && (
								<View style = {{ width: SCREEN_WIDTH }} onLayout = { e => { const height = e.nativeEvent?.layout?.height; if(height) setTabHeights(prev => ({ ...prev, expansions: height })); } }>
									<View className = 'px-4'>
										{isLoadingExpansions ? (
											<View className = 'items-center py-4'>
												<ActivityIndicator color = { colors.primary }/>
											</View>
										) : expansions.length > 0 ? (
												<View className = 'gap-2'>
													{expansions.map(expansion => (
														<GameListItem key = { expansion.bggId } game = { expansion } onPress = { () => router.push(`/game/${expansion.bggId}`) }/>
													))}
													{isFetchingNextPage && (
														<View className = 'items-center py-3'>
															<ActivityIndicator size = 'small' color = { colors.primary }/>
														</View>
													)}
												</View>
											) : (
												<Text className = 'py-2 text-sm text-muted-foreground'>Nessuna espansione trovata.</Text>
											)}
									</View>
								</View>
							)}
							<View style = {{ width: SCREEN_WIDTH }} onLayout = { e => { const height = e.nativeEvent?.layout?.height; if(height) setTabHeights(prev => ({ ...prev, matches: height })); } }>
								<View className = 'px-4'>
									{isLoadingMatches ? (
										<View className = 'items-center py-4'>
											<ActivityIndicator color = { colors.primary }/>
										</View>
									) : matches.length > 0 ? (
										<View className = 'gap-2'>
											{matches.map(match => (
												<MatchListItem key = { match.id } match = { match }/>
											))}
											{isFetchingNextMatchesPage && (
												<View className = 'items-center py-3'>
													<ActivityIndicator size = 'small' color = { colors.primary }/>
												</View>
											)}
										</View>
									) : (
										<Text className = 'py-2 text-sm text-muted-foreground'>Nessuna partita registrata per questo gioco.</Text>
									)}
								</View>
							</View>
						</ScrollView>
					</View>
				</View>
			</Animated.ScrollView>
			<FabMenu
				actions = { [
					...(!game.isExpansion
						? [
								{
									label: 'Registra partita',
									icon: <Trophy size = { 18 }/>,
									onPress: () =>
										registerMatchSheetRef.current?.present({
											id: game.id,
											bggId: game.bggId,
											name: game.name,
											thumbnailUrl: game.thumbnailUrl,
										}),
								},
							]
						: []),
					{
						label: collectionStatus?.inCollection ? 'Rimuovi dalla collezione' : 'Aggiungi alla collezione',
						icon: collectionStatus?.inCollection ? <Trash2 size = { 18 }/> : <Library size = { 18 }/>,
						onPress: handleToggleCollection,
					},
					{
						label: 'Aggiungi a una wishlist',
						icon: <Heart size = { 18 }/>,
						onPress: () => { },
					}
				]}
			/>
			<RegisterMatchSheet ref = { registerMatchSheetRef }/>
		</View>
	)
}

export default GameDetailScreen;