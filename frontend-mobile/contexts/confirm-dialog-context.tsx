import { createContext, useCallback, useContext, useState, ReactNode } from 'react';
import { View, Modal, Pressable } from 'react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

interface ConfirmOptions {
	title: string;
	message?: string;
	confirmLabel?: string;
	cancelLabel?: string;
	destructive?: boolean;
}

interface ConfirmDialogContextValue {
	confirm: (options: ConfirmOptions) => Promise<boolean>;
}

const ConfirmDialogContext = createContext<ConfirmDialogContextValue | undefined>(undefined);

export const ConfirmDialogProvider = ({ children }: { children: ReactNode }) => {
	const [options, setOptions] = useState<ConfirmOptions | null>(null);
	const [resolver, setResolver] = useState<((value: boolean) => void) | null>(null);

	const confirm = useCallback((opts: ConfirmOptions) => {
		return new Promise<boolean>((resolve) => {
			setOptions(opts);
			setResolver(() => resolve);
		});
	}, []);

	const handleClose = (result: boolean) => {
		resolver?.(result);
		
        setOptions(null);
		setResolver(null);
	}

	return (
		<ConfirmDialogContext.Provider value = {{ confirm }}>
			{children}
			<Modal visible = { !!options } transparent animationType = 'fade' onRequestClose = { () => handleClose(false) }>
				<Pressable className = 'flex-1 items-center justify-center bg-black/50 px-6' onPress = { () => handleClose(false)}>
					<Pressable className = 'w-full max-w-sm rounded-2xl bg-background p-5' onPress = { e => e.stopPropagation() }>
						{options && (
							<>
								<Text className = 'mb-2 font-display text-lg text-foreground'>{options.title}</Text>
								{options.message && (
									<Text className = 'mb-5 text-sm text-muted-foreground'>{options.message}</Text>
								)}
								<View className = 'flex-row gap-3'>
									<Button variant = 'outline' className = 'flex-1' onPress = { () => handleClose(false) }>
										<Text>{options.cancelLabel ?? 'Annulla'}</Text>
									</Button>
									<Button className = { `flex-1 ${options.destructive && 'bg-destructive'}`} onPress = { () => handleClose(true) }>
										<Text className = { options.destructive ? 'text-destructive-foreground' : '' }>
											{options.confirmLabel ?? 'Conferma'}
										</Text>
									</Button>
								</View>
							</>
						)}
					</Pressable>
				</Pressable>
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