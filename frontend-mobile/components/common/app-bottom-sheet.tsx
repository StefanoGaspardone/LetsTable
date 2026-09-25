import { forwardRef, useCallback, useMemo } from 'react';
import { BottomSheetModal, BottomSheetBackdrop, BottomSheetBackdropProps, BottomSheetFooterProps } from '@gorhom/bottom-sheet';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface AppBottomSheetProps {
  children: React.ReactNode;
  onClose?: () => void;
  snapPoint?: string;
  footerComponent?: (props: BottomSheetFooterProps) => React.ReactElement;
}

const AppBottomSheet = forwardRef<BottomSheetModal, AppBottomSheetProps>(({ children, onClose, snapPoint = '70%', footerComponent }, ref) => {
	const snapPoints = useMemo(() => [snapPoint, '100%'], [snapPoint]);

	const { colors } = useThemeColors();

	const renderBackdrop = useCallback(
		(props: BottomSheetBackdropProps) => (
			<BottomSheetBackdrop { ...props } disappearsOnIndex = { -1 } appearsOnIndex = { 0 } opacity = { 0.5 }/>
		),
		[]
	);

	const handleSheetChanges = useCallback(
		(index: number) => {
			if(index === -1) onClose?.();
		},
		[onClose]
	);

	return (
		<BottomSheetModal ref = { ref } index = { 0 } snapPoints = { snapPoints } enableDynamicSizing = { false } enablePanDownToClose backdropComponent = { renderBackdrop } onChange = { handleSheetChanges } backgroundStyle = {{ backgroundColor: colors.card }} handleIndicatorStyle = {{ backgroundColor: colors.primary, width: 40 }} footerComponent = { footerComponent }>
			{children}
		</BottomSheetModal>
	)
});

AppBottomSheet.displayName = 'AppBottomSheet';

export default AppBottomSheet;