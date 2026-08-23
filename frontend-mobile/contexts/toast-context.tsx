import { createContext, useCallback, useContext, useRef, useState, ReactNode } from 'react';
import { View, Animated } from 'react-native';
import { CheckCircle2, XCircle, Info } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

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

const VARIANT_STYLES: Record<ToastVariant, { bg: string; icon: string }> = {
	success: { bg: 'bg-primary', icon: 'text-primary-foreground' },
	error: { bg: 'bg-destructive', icon: 'text-destructive-foreground' },
	info: { bg: 'bg-card border border-border', icon: 'text-foreground' },
}

export const ToastProvider = ({ children }: { children: ReactNode }) => {
	const [toast, setToast] = useState<ToastState | null>(null);

	const opacity = useRef(new Animated.Value(0)).current;
	const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

	const showToast = useCallback((message: string, variant: ToastVariant = 'info') => {
		if(timeoutRef.current) clearTimeout(timeoutRef.current);

		setToast({ message, variant });
		opacity.setValue(0);

		Animated.timing(opacity, {
			toValue: 1,
			duration: 200,
			useNativeDriver: true,
		}).start();

		timeoutRef.current = setTimeout(() => {
			Animated.timing(opacity, {
				toValue: 0,
				duration: 200,
				useNativeDriver: true,
			}).start(() => setToast(null));
		}, 2800);
	}, [opacity]);

	const Icon = toast ? ICONS[toast.variant] : null;
	const styles = toast ? VARIANT_STYLES[toast.variant] : null;

	return (
		<ToastContext.Provider value = {{ showToast }}>
			{children}
			{toast && styles && Icon && (
				<Animated.View pointerEvents = 'none' style = {{ opacity }} className = 'absolute bottom-24 left-6 right-6 items-center'>
					<View className = { `flex-row items-center gap-2 rounded-full px-4 py-3 shadow-lg ${styles.bg}` }>
						<Icon size = { 18 } className = { styles.icon }/>
						<Text className = { `flex-1 text-sm ${styles.icon}` } numberOfLines = { 2 }>
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