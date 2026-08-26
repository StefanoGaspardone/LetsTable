import { useCallback, useEffect, useRef, useState } from 'react';
import { View, GestureResponderEvent, PanResponder, Pressable, Modal } from 'react-native';
import Animated, { useSharedValue, useAnimatedStyle, withTiming, withRepeat, Easing } from 'react-native-reanimated';
import { Text } from '@/components/ui/text';

interface ActiveTouch {
	touchId: string;
	x: number;
	y: number;
}

interface FingerOrderPickerProps {
	visible: boolean;
	onClose: () => void;
}

const CIRCLE_SIZE = 100;
const MIN_FINGERS = 2;
const STABLE_HOLD_MS = 1200;

const FingerOrderPicker = ({ visible, onClose }: FingerOrderPickerProps) => {
	const [activeTouches, setActiveTouches] = useState<ActiveTouch[]>([]);
	const [phase, setPhase] = useState<'waiting' | 'spinning' | 'revealed'>('waiting');
	const [litIndex, setLitIndex] = useState<number | null>(null);
	const [winnerTouchId, setWinnerTouchId] = useState<string | null>(null);
	const [holdProgress, setHoldProgress] = useState(0);

	const spinTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const holdTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const holdIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
	const touchesRef = useRef<ActiveTouch[]>([]);

	useEffect(() => {
		touchesRef.current = activeTouches;
	}, [activeTouches]);

	const clearHoldTimers = useCallback(() => {
		if (holdTimerRef.current) clearTimeout(holdTimerRef.current);
		if (holdIntervalRef.current) clearInterval(holdIntervalRef.current);
		setHoldProgress(0);
	}, []);

	const resetAll = useCallback(() => {
		if (spinTimerRef.current) clearTimeout(spinTimerRef.current);
		clearHoldTimers();
		setActiveTouches([]);
		setPhase('waiting');
		setLitIndex(null);
		setWinnerTouchId(null);
	}, [clearHoldTimers]);

	useEffect(() => {
		if (!visible) resetAll();
	}, [visible, resetAll]);

	const startSpin = useCallback(() => {
		const touches = touchesRef.current;
		if (touches.length < MIN_FINGERS) return;

		setPhase('spinning');

		const totalSteps = 26 + Math.floor(Math.random() * 8);
		const winnerIndex = Math.floor(Math.random() * touches.length);
		let step = 0;

		const tick = () => {
			const progress = step / totalSteps;
			const delay = 90 + progress * progress * progress * 520;
			const isLastStep = step === totalSteps;
			const currentIndex = isLastStep ? winnerIndex : step % touches.length;
			setLitIndex(currentIndex);

			if (isLastStep) {
				setWinnerTouchId(touches[winnerIndex].touchId);
				setPhase('revealed');
				return;
			}

			step += 1;
			spinTimerRef.current = setTimeout(tick, delay);
		};

		tick();
	}, []);

	const scheduleHoldCountdown = useCallback(() => {
		clearHoldTimers();
		if (touchesRef.current.length < MIN_FINGERS) return;

		const startedAt = Date.now();
		holdIntervalRef.current = setInterval(() => {
			const elapsed = Date.now() - startedAt;
			setHoldProgress(Math.min(1, elapsed / STABLE_HOLD_MS));
		}, 50);

		holdTimerRef.current = setTimeout(() => {
			clearHoldTimers();
			startSpin();
		}, STABLE_HOLD_MS);
	}, [clearHoldTimers, startSpin]);

	const handleTouchesChange = useCallback(
		(evt: GestureResponderEvent) => {
			if (phase !== 'waiting') return;

			const touches = evt.nativeEvent.touches;

			setActiveTouches((prev) => {
				const next: ActiveTouch[] = [];
				touches.forEach((touch) => {
					const touchId = String(touch.identifier);
					next.push({ touchId, x: touch.pageX, y: touch.pageY });
				});
				return next;
			});

			// qualunque variazione (dito aggiunto, tolto, o semplicemente mosso) riavvia l'attesa
			scheduleHoldCountdown();
		},
		[phase, scheduleHoldCountdown]
	);

	const panResponder = useRef(
		PanResponder.create({
			onStartShouldSetPanResponder: () => true,
			onMoveShouldSetPanResponder: () => true,
			onPanResponderGrant: handleTouchesChange,
			onPanResponderMove: handleTouchesChange,
			onPanResponderRelease: handleTouchesChange,
			onPanResponderTerminate: handleTouchesChange,
		})
	).current;

	useEffect(() => {
		return () => {
			if (spinTimerRef.current) clearTimeout(spinTimerRef.current);
			clearHoldTimers();
		};
	}, [clearHoldTimers]);

	return (
		<Modal visible={visible} animationType="fade" statusBarTranslucent onRequestClose={onClose}>
			<View style={{ flex: 1, backgroundColor: '#1E1C1A' }} {...panResponder.panHandlers}>
				<View style={{ position: 'absolute', top: 60, left: 0, right: 0, alignItems: 'center', paddingHorizontal: 24 }}>
					{phase === 'waiting' && (
						<>
							<Text className="text-center text-lg font-semibold text-white">
								Ogni giocatore metta un dito sullo schermo
							</Text>
							<Text className="mt-1 text-center text-sm text-white/60">
								{activeTouches.length < MIN_FINGERS
									? `${activeTouches.length} dita rilevate`
									: 'Tenete fermo, si parte tra poco...'}
							</Text>
						</>
					)}
					{phase === 'revealed' && (
						<Text className="text-center text-2xl font-bold text-white">Fatto! 🎉</Text>
					)}
				</View>

				{phase === 'waiting' && activeTouches.length >= MIN_FINGERS && (
					<View
						style={{
							position: 'absolute',
							top: 110,
							left: 40,
							right: 40,
							height: 4,
							borderRadius: 2,
							backgroundColor: 'rgba(255,255,255,0.15)',
							overflow: 'hidden',
						}}
					>
						<View style={{ width: `${holdProgress * 100}%`, height: '100%', backgroundColor: '#C45135' }} />
					</View>
				)}

				{activeTouches.map((touch, index) => (
					<FingerCircle
						key={touch.touchId}
						touch={touch}
						isLit={phase === 'spinning' && litIndex === index}
						isWinner={phase === 'revealed' && winnerTouchId === touch.touchId}
						isFaded={phase === 'revealed' && winnerTouchId !== touch.touchId}
					/>
				))}

				<View style={{ position: 'absolute', bottom: 60, left: 0, right: 0, alignItems: 'center', gap: 12 }}>
					{phase === 'revealed' && (
						<Pressable onPress={resetAll} className="rounded-full border border-white/30 px-6 py-2.5">
							<Text className="text-sm text-white">Rifai</Text>
						</Pressable>
					)}
					<Pressable onPress={onClose}>
						<Text className="text-sm text-white/50">Chiudi</Text>
					</Pressable>
				</View>
			</View>
		</Modal>
	);
};

interface FingerCircleProps {
	touch: ActiveTouch;
	isLit: boolean;
	isWinner: boolean;
	isFaded: boolean;
}

const FingerCircle = ({ touch, isLit, isWinner, isFaded }: FingerCircleProps) => {
	const scale = useSharedValue(1);
	const opacity = useSharedValue(1);
	const brightness = useSharedValue(0);

	useEffect(() => {
		brightness.value = withTiming(isLit || isWinner ? 1 : 0, { duration: 80 });
	}, [isLit, isWinner]);

	useEffect(() => {
		if (isWinner) {
			scale.value = withRepeat(withTiming(1.25, { duration: 450, easing: Easing.inOut(Easing.ease) }), -1, true);
		}
	}, [isWinner]);

	useEffect(() => {
		opacity.value = withTiming(isFaded ? 0.15 : 1, { duration: 300 });
	}, [isFaded]);

	const animatedStyle = useAnimatedStyle(() => ({
		left: touch.x - CIRCLE_SIZE / 2,
		top: touch.y - CIRCLE_SIZE / 2,
		transform: [{ scale: scale.value }],
		opacity: opacity.value,
		borderWidth: brightness.value * 4,
	}));

	return (
		<Animated.View
			pointerEvents="none"
			style={[
				{
					position: 'absolute',
					width: CIRCLE_SIZE,
					height: CIRCLE_SIZE,
					borderRadius: CIRCLE_SIZE / 2,
					backgroundColor: '#C45135',
					borderColor: '#FFFFFF',
				},
				animatedStyle,
			]}
		/>
	);
};

export default FingerOrderPicker;