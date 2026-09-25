import { createContext, useCallback, useContext, useRef, useState, ReactNode } from 'react';
import { View, Animated } from 'react-native';
import { CheckCircle2, XCircle, Info } from 'lucide-react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/ui/text';
import { useThemeColors } from '@/hooks/use-theme-colors';

type ToastVariant = 'success' | 'error' | 'info';

interface ToastState {
	message: string;
	variant: ToastVariant;
}

interface ToastContextValue {
	showToast: (message: string, variant?: ToastVariant) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

const ICONS: Record<ToastVariant, typeof CheckCircle2> = {
	success: CheckCircle2,
	error: XCircle,
	info: Info,
}

const SLIDE_DISTANCE = -120;

export const ToastProvider = ({ children }: { children: ReactNode }) => {
	const insets = useSafeAreaInsets();
	const [toast, setToast] = useState<ToastState | null>(null);

	const translateY = useRef(new Animated.Value(SLIDE_DISTANCE)).current;
	const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

	const { colors } = useThemeColors();

	const showToast = useCallback((message: string, variant: ToastVariant = 'info') => {
		if(timeoutRef.current) clearTimeout(timeoutRef.current);

		setToast({ message, variant });
		translateY.setValue(SLIDE_DISTANCE);

		Animated.timing(translateY, {
			toValue: 0,
			duration: 250,
			useNativeDriver: true,
		}).start();

		timeoutRef.current = setTimeout(() => {
			Animated.timing(translateY, {
				toValue: SLIDE_DISTANCE,
				duration: 250,
				useNativeDriver: true,
			}).start(() => setToast(null));
		}, 2800);
	}, [translateY]);

	const Icon = toast ? ICONS[toast.variant] : null;

	return (
		<ToastContext.Provider value = {{ showToast }}>
			{children}
			{toast && Icon && (
				<Animated.View pointerEvents = 'none' style = {{ transform: [{ translateY }], position: 'absolute', top: insets.top + 8, left: 16, right: 16 }}>
					<View className = 'flex-row items-center gap-3 rounded-xl bg-card px-4 py-3 shadow-lg border border-border'>
						<Icon size = { 22 } color = { colors.primary }/>
						<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 2 }>
							{toast.message}
						</Text>
					</View>
				</Animated.View>
			)}
		</ToastContext.Provider>
	)
}

export const useToast = () => {
	const context = useContext(ToastContext);
	
    if(!context) {
		throw new Error('useToast must be used within a ToastProvider');
	}
	
    return context;
}