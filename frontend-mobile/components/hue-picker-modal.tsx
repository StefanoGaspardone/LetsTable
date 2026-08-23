import { useState } from 'react';
import { View, Modal, PanResponder, GestureResponderEvent } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

interface HuePickerModalProps {
	visible: boolean;
	onClose: () => void;
	onConfirm: (color: string) => void;
}

const BAR_WIDTH = 280;

const hueToHex = (hue: number): string => {
	const s = 0.7;
	const l = 0.5;
	const c = (1 - Math.abs(2 * l - 1)) * s;
	const x = c * (1 - Math.abs(((hue / 60) % 2) - 1));
	const m = l - c / 2;
	let r = 0, g = 0, b = 0;

	if(hue < 60) [r, g, b] = [c, x, 0];
	else if(hue < 120) [r, g, b] = [x, c, 0];
	else if(hue < 180) [r, g, b] = [0, c, x];
	else if(hue < 240) [r, g, b] = [0, x, c];
	else if(hue < 300) [r, g, b] = [x, 0, c];
	else [r, g, b] = [c, 0, x];

	const toHex = (v: number) => Math.round((v + m) * 255).toString(16).padStart(2, '0');
	return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

const HuePickerModal = ({ visible, onClose, onConfirm }: HuePickerModalProps) => {
	const [hue, setHue] = useState(0);

	const panResponder = PanResponder.create({
		onStartShouldSetPanResponder: () => true,
		onMoveShouldSetPanResponder: () => true,
		onPanResponderMove: (event: GestureResponderEvent) => {
			const x = Math.max(0, Math.min(BAR_WIDTH, event.nativeEvent.locationX));
			setHue(Math.round((x / BAR_WIDTH) * 360));
		},
	});

	const currentColor = hueToHex(hue);

	return (
		<Modal visible = { visible } transparent animationType = 'fade' onRequestClose = { onClose }>
			<View className = 'flex-1 items-center justify-center bg-black/50 px-6'>
				<View className = 'w-full rounded-2xl bg-background p-5'>
					<Text className = 'mb-4 text-center font-display text-lg text-foreground'>
						Scegli un colore
					</Text>
					<View className = 'mb-4 items-center'>
						<View className = 'mb-3 h-12 w-12 rounded-full border border-border' style = {{ backgroundColor: currentColor }}/>
						<View style = {{ width: BAR_WIDTH }} {...panResponder.panHandlers}>
							<LinearGradient colors = {['#FF0000', '#FFFF00', '#00FF00', '#00FFFF', '#0000FF', '#FF00FF', '#FF0000']} start = {{ x: 0, y: 0 }} end = {{ x: 1, y: 0 }} style = {{ height: 24, borderRadius: 12 }}/>
							<View className = 'absolute top-0 h-6 w-1 rounded-full bg-foreground' style = {{ left: (hue / 360) * BAR_WIDTH - 1 }}/>
						</View>
					</View>
					<View className = 'flex-row gap-3'>
						<Button variant = 'outline' className = 'flex-1' onPress = { onClose }>
							<Text>Annulla</Text>
						</Button>
						<Button className = 'flex-1' onPress = { () => onConfirm(currentColor) }>
							<Text>Conferma</Text>
						</Button>
					</View>
				</View>
			</View>
		</Modal>
	)
}

export default HuePickerModal;