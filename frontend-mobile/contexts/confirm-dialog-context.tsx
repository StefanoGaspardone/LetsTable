import { createContext, useCallback, useContext, useMemo, useRef, useState, ReactNode } from 'react';
import { View, Modal, Pressable, KeyboardAvoidingView } from 'react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

import { cn } from '@/lib/utils';

interface ConfirmOptions {
	title: string;
	message?: string;
	confirmLabel?: string;
	cancelLabel?: string;
	destructive?: boolean;
	infoOnly?: boolean;
	confirmText?: string;
}

interface ConfirmDialogContextValue {
	confirm: (options: ConfirmOptions) => Promise<boolean>;
}

const ConfirmDialogContext = createContext<ConfirmDialogContextValue | undefined>(undefined);

const normalize = (value: string) => value.trim().toLowerCase();

export const ConfirmDialogProvider = ({ children }: { children: ReactNode }) => {
	const [options, setOptions] = useState<ConfirmOptions | null>(null);
	const [typedText, setTypedText] = useState('');
	const resolverRef = useRef<((value: boolean) => void) | null>(null);

	const confirm = useCallback((opts: ConfirmOptions) => {
		return new Promise<boolean>((resolve) => {
			resolverRef.current?.(false);
			resolverRef.current = resolve;
			setTypedText('');
			setOptions(opts);
		});
	}, []);

	const handleClose = useCallback((result: boolean) => {
		resolverRef.current?.(result);
		resolverRef.current = null;
		setOptions(null);
	}, []);

	const value = useMemo(() => ({ confirm }), [confirm]);

	const isConfirmBlocked = !!options?.confirmText && normalize(typedText) !== normalize(options.confirmText);

	return (
		<ConfirmDialogContext.Provider value = { value }>
			{children}
			<Modal visible = { !!options } transparent animationType = 'fade' onRequestClose = { () => handleClose(false) }>
				<KeyboardAvoidingView behavior = 'padding' style = {{ flex: 1 }}>
					<Pressable testID = 'confirm-dialog-backdrop' className = 'flex-1 items-center justify-center bg-black/50 px-6' onPress = { () => handleClose(false) }>
						<Pressable className = 'w-full max-w-sm rounded-2xl bg-background p-5' onPress = { e => e.stopPropagation() }>
							{options && (
								<>
									<Text className = 'mb-2 font-display text-lg text-foreground'>{options.title}</Text>
									{options.message && (
										<Text className = 'mb-5 text-sm text-muted-foreground'>{options.message}</Text>
									)}
									{options.confirmText && (
										<View className = 'mb-5'>
											<Text className = 'mb-2 text-sm text-muted-foreground'>
												Scrivi <Text className = 'font-sans-semibold text-foreground'>{options.confirmText}</Text> per confermare
											</Text>
											<Input value = { typedText } onChangeText = { setTypedText } placeholder = { options.confirmText } autoCapitalize = 'none' autoCorrect = { false } autoComplete = 'off' className = 'h-11 rounded-xl'/>
										</View>
									)}
									<View className = 'flex-row gap-3'>
										{!options.infoOnly && (
											<Button variant = 'outline' className = 'flex-1' onPress = { () => handleClose(false) }>
												<Text>{options.cancelLabel ?? 'Annulla'}</Text>
											</Button>
										)}
										<Button disabled = { isConfirmBlocked } className = { cn('flex-1', options.destructive && 'bg-destructive', isConfirmBlocked && 'opacity-50') } onPress = { () => handleClose(true) }>
											<Text className = { options.destructive ? 'text-destructive-foreground' : '' }>
												{options.confirmLabel ?? 'Conferma'}
											</Text>
										</Button>
									</View>
								</>
							)}
						</Pressable>
					</Pressable>
				</KeyboardAvoidingView>
			</Modal>
		</ConfirmDialogContext.Provider>
	)
}

export const useConfirmDialog = () => {
	const context = useContext(ConfirmDialogContext);

	if(!context) {
		throw new Error('useConfirmDialog must be used within a ConfirmDialogProvider');
	}

	return context;
}