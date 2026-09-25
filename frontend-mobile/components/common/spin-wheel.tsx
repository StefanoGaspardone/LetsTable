import React, { useEffect, useRef, useState } from 'react';
import { Modal, View, Animated, Easing, Pressable, Dimensions } from 'react-native';
import Svg, { G, Path, Circle as SvgCircle, Text as SvgText, Polygon } from 'react-native-svg';
import { X, Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface WheelEntry {
	id: string;
	label: string;
	color: string;
}

interface SpinWheelProps {
	visible: boolean;
	entries: WheelEntry[];
	onClose: () => void;
	onResult: (id: string) => void;
}

const WHEEL_SIZE = Math.min(Dimensions.get('window').width - 96, 280);
const RADIUS = WHEEL_SIZE / 2;

const polarToCartesian = (cx: number, cy: number, r: number, angleDeg: number) => {
	const angleRad = ((angleDeg - 90) * Math.PI) / 180;
	return { x: cx + r * Math.cos(angleRad), y: cy + r * Math.sin(angleRad) };
}

const describeArc = (cx: number, cy: number, r: number, startAngle: number, endAngle: number) => {
	const start = polarToCartesian(cx, cy, r, endAngle);
	const end = polarToCartesian(cx, cy, r, startAngle);
	const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1';
	
	return [`M ${cx} ${cy}`, `L ${start.x} ${start.y}`, `A ${r} ${r} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`, 'Z'].join(' ');
}

const SpinWheel = ({ visible, entries, onClose, onResult }: SpinWheelProps) => {
	const rotation = useRef(new Animated.Value(0)).current;
	const currentRotationRef = useRef(0);

	const [isSpinning, setIsSpinning] = useState(false);
	const [resultId, setResultId] = useState<string | null>(null);

	const { colors } = useThemeColors();

	useEffect(() => {
		if(visible) {
		setResultId(null);
		setIsSpinning(false);
		
		rotation.setValue(0);
		currentRotationRef.current = 0;
		}
	}, [visible]);

	const handleSpin = () => {
		if(isSpinning || entries.length === 0) return;

		setIsSpinning(true);
		setResultId(null);

		const targetIndex = Math.floor(Math.random() * entries.length);

		const anglePerSlice = 360 / entries.length;
		const sliceCenter = targetIndex * anglePerSlice + anglePerSlice / 2;
		const extraSpins = 5 * 360;
		const targetRotation = currentRotationRef.current + extraSpins + ((360 - sliceCenter) % 360);

		Animated.timing(rotation, {
			toValue: targetRotation,
			duration: 3500,
			easing: Easing.out(Easing.cubic),
			useNativeDriver: true,
			}).start(() => {
			currentRotationRef.current = targetRotation;
			setIsSpinning(false);
			setResultId(entries[targetIndex].id);
		});
	}

	const handleConfirm = () => {
		if(resultId) {
			onResult(resultId);
			onClose();
		}
	}

	const anglePerSlice = 360 / Math.max(entries.length, 1);
	const resultEntry = entries.find((e) => e.id === resultId);

	return (
		<Modal visible = { visible } transparent animationType = 'fade' onRequestClose = { onClose }>
			<View className = 'flex-1 items-center justify-center bg-black/60 px-6'>
				<View className = 'w-full max-w-sm items-center rounded-3xl bg-card p-6 shadow-2xl border border-border'>
					<View className = 'w-full flex-row items-center justify-between mb-6'>
						<View className = 'flex-row items-center gap-2'>
							<Dices size = { 22 } color = { colors.primary }/>
							<Text className = 'text-lg font-bold text-foreground'>Chi inizia?</Text>
						</View>
						<Pressable  onPress = { onClose }  disabled = { isSpinning } className = 'h-8 w-8 items-center justify-center rounded-full bg-muted active:opacity-70'>
							<X size = { 18 } color = { colors.mutedForeground }/>
						</Pressable>
					</View>
					<View style = {{ width: WHEEL_SIZE, height: WHEEL_SIZE }} className = 'items-center justify-center my-2'>
						<View style = {{ position: 'absolute', top: -10, zIndex: 30 }}>
							<Svg width = { 28 } height = { 26 } viewBox = '0 0 28 26'>
								<Polygon points = '2,2 26,2 14,24' fill = { colors.primary } stroke = '#FFFFFF' strokeWidth = { 2 } strokeLinejoin = 'round'/>
							</Svg>
						</View>
						<Animated.View
							style = {{
								width: WHEEL_SIZE,
								height: WHEEL_SIZE,
								transform: [
									{ 
										rotate: rotation.interpolate({ 
											inputRange: [0, 360], 
											outputRange: ['0deg', '360deg'] 
										}) 
									}
								],
							}}
						>
						<Svg width = { WHEEL_SIZE } height = { WHEEL_SIZE } viewBox = { `0 0 ${WHEEL_SIZE} ${WHEEL_SIZE}` }>
							<G>
								{entries.length === 1 ? (
									<G>
										<SvgCircle cx = { RADIUS } cy = { RADIUS } r = { RADIUS - 2 } fill = { entries[0].color } stroke = '#FFFFFF' strokeWidth = { 2 }/>
										<SvgText x = { RADIUS } y = { RADIUS - RADIUS * 0.4 } fill = '#FFFFFF' fontSize = { 20 } fontWeight = '800' textAnchor = 'middle' alignmentBaseline = 'middle'>
											{entries[0].label.length > 10 ? `${entries[0].label.slice(0, 9)}…` : entries[0].label}
										</SvgText>
									</G>
								) : (
									entries.map((entry, index) => {
										const startAngle = index * anglePerSlice;
										const endAngle = startAngle + anglePerSlice;
										const midAngle = startAngle + anglePerSlice / 2;
										const labelPos = polarToCartesian(RADIUS, RADIUS, RADIUS * 0.60, midAngle);

										return (
											<G key = { `${entry.id}-${index}` }>
												<Path  d = { describeArc(RADIUS, RADIUS, RADIUS - 2, startAngle, endAngle) }  fill = { entry.color }  stroke = '#FFFFFF'  strokeWidth = { 2 } />
												<SvgText x = { labelPos.x } y = { labelPos.y } fill = '#FFFFFF' fontSize = { 20 } fontWeight = '800' textAnchor = 'middle' alignmentBaseline = 'middle' transform = { `rotate(${midAngle + 90}, ${labelPos.x}, ${labelPos.y})` }>
													{entry.label.length > 10 ? `${entry.label.slice(0, 9)}…` : entry.label}
												</SvgText>
											</G>
										)
									})
								)}
								<SvgCircle cx = { RADIUS } cy = { RADIUS } r = { 22 } fill = '#FFFFFF'/>
								<SvgCircle cx = { RADIUS } cy = { RADIUS } r = { 18 } fill = '#F3F4F6'/>
							</G>
						</Svg>
						</Animated.View>
					</View>
					<View className = 'w-full mt-6 items-center'>
						{resultEntry ? (
							<View className='items-center w-full'>
								<Text className='text-xs text-muted-foreground uppercase tracking-wider mb-1'>
									Primo giocatore estratto
								</Text>
								<Text className='text-xl font-extrabold text-primary mb-4'>
									{resultEntry.label}
								</Text>
								<Pressable  onPress = { handleConfirm }  className = 'w-full h-12 items-center justify-center rounded-full bg-primary active:opacity-80 shadow'>
									<Text className = 'text-base font-semibold text-primary-foreground'>
										Conferma selezione
									</Text>
								</Pressable>
							</View>
						) : (
							<Pressable  onPress = { handleSpin }  disabled = { isSpinning }  className = 'w-full h-12 items-center justify-center rounded-full bg-primary active:opacity-80 shadow disabled:opacity-50'>
								<Text className = 'text-base font-semibold text-primary-foreground'>
									{isSpinning ? 'Giro in corso...' : 'Gira la ruota'}
								</Text>
							</Pressable>
						)}
					</View>
				</View>
			</View>
		</Modal>
	)
}

export default SpinWheel;