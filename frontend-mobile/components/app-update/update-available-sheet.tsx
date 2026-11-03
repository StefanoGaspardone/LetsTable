import { useCallback, useEffect, useRef } from 'react';
import { View } from 'react-native';
import { BottomSheetFooter, BottomSheetFooterProps, BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';
import { Sparkles } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AppBottomSheet from '@/components/common/app-bottom-sheet';
import ReleaseNotes from '@/components/app-update/release-notes';
import UpdateButton from '@/components/app-update/update-button';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { AppVersionInfo } from '@/types/app-version';

const PRESENT_DELAY_MS = 800;

interface UpdateAvailableSheetProps {
	info: AppVersionInfo;
	currentVersion: string;
	onDismiss: () => void;
}

const UpdateAvailableSheet = ({ info, currentVersion, onDismiss }: UpdateAvailableSheetProps) => {
	const sheetRef = useRef<BottomSheetModal>(null);
	const { colors } = useThemeColors();

	useEffect(() => {
		const timeout = setTimeout(() => sheetRef.current?.present(), PRESENT_DELAY_MS);
		return () => clearTimeout(timeout);
	}, [info.latestVersion]);

	const renderFooter = useCallback((props: BottomSheetFooterProps) => (
		<BottomSheetFooter { ...props }>
			<View className = 'gap-1 border-t border-border bg-card px-4 pb-6 pt-3'>
				<UpdateButton info = { info }/>
				<Button variant = 'ghost' onPress = { () => sheetRef.current?.dismiss() }>
					<Text className = 'text-sm text-muted-foreground'>Più tardi</Text>
				</Button>
			</View>
		</BottomSheetFooter>
	), [info]);

	return (
		<AppBottomSheet ref = { sheetRef } snapPoint = '75%' onClose = { onDismiss } footerComponent = { renderFooter }>
			<BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 190 }}>
				<View className = 'mb-5 flex-row items-center gap-3'>
					<View className = 'h-12 w-12 items-center justify-center rounded-2xl bg-primary/15'>
						<Sparkles size = { 22 } color = { colors.primary }/>
					</View>
					<View className = 'flex-1'>
						<Text className = 'font-display text-xl text-foreground'>Nuova versione disponibile</Text>
						<Text className = 'text-sm text-muted-foreground'>
							{currentVersion} → <Text className = 'font-semibold text-primary'>{info.latestVersion}</Text>
						</Text>
					</View>
				</View>
				{info.releaseNotes ? (
					<ReleaseNotes markdown = { info.releaseNotes }/>
				) : (
					<Text className = 'text-sm text-muted-foreground'>Aggiorna per avere le ultime novità e correzioni.</Text>
				)}
			</BottomSheetScrollView>
		</AppBottomSheet>
	)
}

export default UpdateAvailableSheet;