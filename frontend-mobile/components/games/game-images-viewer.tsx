import { useCallback, useEffect, useState } from 'react';
import { FlatList, Linking, Modal, NativeScrollEvent, NativeSyntheticEvent, Pressable, StatusBar, useWindowDimensions, View } from 'react-native';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ExternalLink, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { GameImage } from '@/types/game';

interface GameImagesViewerProps {
	images: GameImage[];
	initialIndex: number | null;
	onClose: () => void;
}

const PREFETCH_RANGE = 2;

const GameImagesViewer = ({ images, initialIndex, onClose }: GameImagesViewerProps) => {
	const insets = useSafeAreaInsets();
	const { width, height } = useWindowDimensions();

	const [currentIndex, setCurrentIndex] = useState(initialIndex ?? 0);
	const [isChromeVisible, setIsChromeVisible] = useState(true);

	const isVisible = initialIndex !== null;
	const currentImage = images[currentIndex];

	useEffect(() => {
		if(initialIndex === null) return;

		setCurrentIndex(initialIndex);
		setIsChromeVisible(true);
	}, [initialIndex]);

	useEffect(() => {
		if(!isVisible) return;

		const urls = images
			.slice(Math.max(0, currentIndex - PREFETCH_RANGE), currentIndex + PREFETCH_RANGE + 1)
			.map(image => image.imageUrl);

		Image.prefetch(urls);
	}, [currentIndex, images, isVisible]);

	const handleMomentumScrollEnd = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
		const index = Math.round(event.nativeEvent.contentOffset.x / width);
		if(index !== currentIndex) setCurrentIndex(index);
	}

	const handleOpenOnBgg = () => {
		if(currentImage?.pageUrl) Linking.openURL(currentImage.pageUrl);
	}

	const getItemLayout = useCallback((_: ArrayLike<GameImage> | null | undefined, index: number) => ({
		length: width,
		offset: width * index,
		index,
	}), [width]);

	const renderItem = useCallback(({ item }: { item: GameImage }) => (
		<Pressable onPress = { () => setIsChromeVisible(prev => !prev) } style = {{ width, height }} className = 'items-center justify-center'>
			<Image source = {{ uri: item.imageUrl }} placeholder = {{ uri: item.thumbnailUrl }} placeholderContentFit = 'contain' style = {{ width, height }} contentFit = 'contain' transition = { 200 } recyclingKey = { item.id }/>
		</Pressable>
	), [width, height]);

	if(!isVisible) return null;

	return (
		<Modal visible animationType = 'fade' transparent statusBarTranslucent navigationBarTranslucent onRequestClose = { onClose }>
			<StatusBar barStyle = 'light-content' backgroundColor = 'transparent' translucent/>
			<View className = 'flex-1 bg-black'>
				<FlatList
					data = { images }
					keyExtractor = { image => image.id }
					renderItem = { renderItem }
					horizontal
					pagingEnabled
					showsHorizontalScrollIndicator = { false }
					initialScrollIndex = { initialIndex ?? 0 }
					getItemLayout = { getItemLayout }
					onMomentumScrollEnd = { handleMomentumScrollEnd }
					windowSize = { 3 }
					initialNumToRender = { 1 }
					maxToRenderPerBatch = { 2 }
				/>
				{isChromeVisible && (
					<>
						<LinearGradient colors = { ['rgba(0,0,0,0.6)', 'rgba(0,0,0,0)'] } style = {{ position: 'absolute', top: 0, left: 0, right: 0, paddingTop: insets.top + 8, paddingBottom: 24, paddingHorizontal: 16 }}>
							<View className = 'flex-row items-center justify-between'>
								<Text className = 'text-sm font-semibold text-white'>
									{currentIndex + 1} / {images.length}
								</Text>
								<Pressable onPress = { onClose } hitSlop = { 12 } className = 'h-10 w-10 items-center justify-center rounded-full bg-white/15 active:bg-white/30'>
									<X size = { 20 } color = '#FFFFFF'/>
								</Pressable>
							</View>
						</LinearGradient>
						<LinearGradient colors = { ['rgba(0,0,0,0)', 'rgba(0,0,0,0.75)'] } style = {{ position: 'absolute', bottom: 0, left: 0, right: 0, paddingTop: 32, paddingBottom: insets.bottom + 16, paddingHorizontal: 16 }}>
							{!!currentImage?.caption && (
								<Text className = 'mb-1 text-sm text-white' numberOfLines = { 3 }>
									{currentImage.caption}
								</Text>
							)}
							<View className = 'flex-row items-center justify-between gap-3'>
								<Text className = 'flex-1 text-xs text-white/70' numberOfLines = { 1 }>
									{currentImage?.author ? `Foto di ${currentImage.author} su BoardGameGeek` : 'Foto da BoardGameGeek'}
								</Text>
								{!!currentImage?.pageUrl && (
									<Pressable onPress = { handleOpenOnBgg } hitSlop = { 8 } className = 'flex-row items-center gap-1.5 rounded-full bg-white/15 px-3 py-1.5 active:bg-white/30'>
										<ExternalLink size = { 13 } color = '#FFFFFF'/>
										<Text className = 'text-xs font-medium text-white'>Apri su BGG</Text>
									</Pressable>
								)}
							</View>
						</LinearGradient>
					</>
				)}
			</View>
		</Modal>
	)
}

export default GameImagesViewer;