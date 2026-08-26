import { useState } from 'react';
import { View, Pressable, Modal } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';
import { Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { PALETTE } from '@/lib/colors';

interface ColorSwatchPickerProps {
	value: string;
	onChange: (color: string) => void;
}

const ColorSwatchPicker = ({ value, onChange }: ColorSwatchPickerProps) => {
	const [isOpen, setIsOpen] = useState(false);
	const [hue, setHue] = useState(0);

	const isCustomColor = !PALETTE.includes(value);

	const handleHueSelect = () => {
		const h = hue / 360;
		const rgb = hslToRgb(h, 0.55, 0.45);
		
        onChange(rgb);
		setIsOpen(false);
	}

    const getContrastColor = (hex: string): string => {
        const clean = hex.replace('#', '');

        const r = Number.parseInt(clean.substring(0, 2), 16) / 255;
        const g = Number.parseInt(clean.substring(2, 4), 16) / 255;
        const b = Number.parseInt(clean.substring(4, 6), 16) / 255;
        const luminance = 0.299 * r + 0.587 * g + 0.114 * b;

        return luminance > 0.6 ? '#1E1C1A' : '#FFFFFF';
    }

	return (
		<>
			<Pressable onPress = { () => setIsOpen(true) } style = {{ backgroundColor: value }} className = 'h-7 w-7 items-center justify-center rounded-full border border-black shadow-sm active:scale-95'/>
			<Modal visible = { isOpen } transparent animationType = 'fade' onRequestClose = { () => setIsOpen(false) }>
                <GestureHandlerRootView style = {{ flex: 1 }}>
                    <Pressable className = 'flex-1 items-center justify-center bg-black/40' onPress = { () => setIsOpen(false) }>
                        <Pressable className = 'w-[85%] rounded-2xl bg-card p-4' onPress = { e => e.stopPropagation()}>
                            <Text className = 'mb-3 font-display text-base text-foreground'>Scegli un colore</Text>
                            <View className = 'flex-row flex-wrap gap-2.5'>
                                {PALETTE.map(color => (
                                    <Pressable key = { color } onPress = { () => { onChange(color); setIsOpen(false); } } style = {{ backgroundColor: color }} className = 'h-9 w-9 items-center justify-center rounded-full border border-black active:scale-95'>
                                        {value === color && <Check size = { 16 } color = { getContrastColor(value) } strokeWidth = { 3 }/>}
                                    </Pressable>
                                ))}
                                {isCustomColor && (
                                    <View style = {{ backgroundColor: value }} className = 'h-9 w-9 items-center justify-center rounded-full border-2 border-foreground'>
                                        <Check size = { 16 } color = '#FFFFFF' strokeWidth = { 3 }/>
                                    </View>
                                )}
                            </View>
                            <View className = 'mt-4 border-t border-border pt-4'>
                                <Text className = 'mb-2 text-xs text-muted-foreground'>Colore personalizzato</Text>
                                <View className = 'flex-row items-center gap-3'>
                                    <View style = {{ backgroundColor: hslToRgb(hue / 360, 0.55, 0.45) }} className = 'h-8 w-8 rounded-full border border-black'/>
                                    <View className = 'flex-1'>
                                        <HueSlider hue = { hue } onChange = { setHue }/>
                                    </View>
                                </View>
                                <Pressable onPress = { handleHueSelect } className = 'mt-3 h-10 items-center justify-center rounded-full bg-[#C45135]'>
                                    <Text className = 'text-sm font-semibold text-white'>Usa questo colore</Text>
                                </Pressable>
                            </View>
                        </Pressable>
                    </Pressable>
                </GestureHandlerRootView>
            </Modal>
		</>
	)
}

interface HueSliderProps {
	hue: number;
	onChange: (hue: number) => void;
}

const SLIDER_HEIGHT = 36;
const TRACK_HEIGHT = 16;
const THUMB_SIZE = 26;

const HueSlider = ({ hue, onChange }: HueSliderProps) => {
	const [width, setWidth] = useState(0);

	const updateHue = (x: number) => {
		if(width <= 0) return;
		
        const clamped = Math.max(0, Math.min(width, x));
		onChange(Math.round((clamped / width) * 360));
	}

	const panGesture = Gesture.Pan()
		.onBegin(e => {
			scheduleOnRN(updateHue, e.x);
		})
		.onUpdate(e => {
			scheduleOnRN(updateHue, e.x);
		});

	return (
		<GestureDetector gesture = { panGesture }>
			<View onLayout = { e => setWidth(e.nativeEvent.layout.width)} style = {{ height: SLIDER_HEIGHT, justifyContent: 'center' }}>
				<View style = {{ height: TRACK_HEIGHT, borderRadius: TRACK_HEIGHT / 2, backgroundColor: '#DDD8CE', overflow: 'hidden' }}>
					<View style = {{ position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 }}>
						{Array.from({ length: 36 }).map((_, i) => (
							<View key = { i } style = {{ position: 'absolute', left: `${(i / 36) * 100}%`, width: `${100 / 36 + 0.5}%`, top: 0, bottom: 0, backgroundColor: hslToRgb((i * 10) / 360, 0.7, 0.5) }}/>
						))}
					</View>
				</View>
				{width > 0 && (
					<View pointerEvents = 'none' style = {{ position: 'absolute', left: (hue / 360) * width - THUMB_SIZE / 2, width: THUMB_SIZE, height: THUMB_SIZE, borderRadius: THUMB_SIZE / 2, backgroundColor: '#FFFFFF', borderWidth: 3, borderColor: '#1E1C1A' }}/>
				)}
			</View>
		</GestureDetector>
	)
}

const hslToRgb = (h: number, s: number, l: number): string => {
	let r: number, g: number, b: number;

	if(s === 0) {
		r = g = b = l;
	} else {
		const hue2rgb = (p: number, q: number, t: number) => {
			if(t < 0) t += 1;
			if(t > 1) t -= 1;
			
            if(t < 1 / 6) return p + (q - p) * 6 * t;
			if(t < 1 / 2) return q;
			if(t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
			
            return p;
		}

		const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
		const p = 2 * l - q;
		
        r = hue2rgb(p, q, h + 1 / 3);
		g = hue2rgb(p, q, h);
		b = hue2rgb(p, q, h - 1 / 3);
	}

	const toHex = (x: number) => Math.round(x * 255).toString(16).padStart(2, '0');
	return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

export default ColorSwatchPicker;