import { ReactNode } from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { renderHook } from '@testing-library/react-native';

import { ConfirmDialogProvider, useConfirmDialog } from '@/contexts/confirm-dialog-context';

const wrapper = ({ children }: { children: ReactNode }) => (
	<ConfirmDialogProvider>{children}</ConfirmDialogProvider>
);

describe('ConfirmDialogProvider / useConfirmDialog', () => {
	it('does not show the dialog before confirm is called', async () => {
        await render(<ConfirmDialogProvider><></></ConfirmDialogProvider>);

        expect(screen.queryByText('Annulla')).toBeNull();
    });

	it('shows the dialog with the given title and message after calling confirm', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		result.current.confirm({ title: 'Elimina partita', message: 'Sei sicuro?' });

		await waitFor(() => expect(screen.getByText('Elimina partita')).toBeTruthy());
		expect(screen.getByText('Sei sicuro?')).toBeTruthy();
	});

	it('resolves to true when the confirm button is pressed', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		const confirmPromise = result.current.confirm({ title: 'Elimina' });

		await waitFor(() => expect(screen.getByText('Conferma')).toBeTruthy());

		fireEvent.press(screen.getByText('Conferma'));

		await expect(confirmPromise).resolves.toBe(true);
	});

	it('resolves to false when the cancel button is pressed', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		const confirmPromise = result.current.confirm({ title: 'Elimina' });

		await waitFor(() => expect(screen.getByText('Annulla')).toBeTruthy());

		fireEvent.press(screen.getByText('Annulla'));

		await expect(confirmPromise).resolves.toBe(false);
	});

	it('resolves to false when the backdrop is pressed', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		const confirmPromise = result.current.confirm({ title: 'Elimina' });

		await waitFor(() => expect(screen.getByText('Elimina')).toBeTruthy());

		fireEvent.press(screen.getByTestId('confirm-dialog-backdrop'));

		await expect(confirmPromise).resolves.toBe(false);
	});

	it('uses custom confirm and cancel labels when provided', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		result.current.confirm({ title: 'Elimina', confirmLabel: 'Sì, elimina', cancelLabel: 'No, torna indietro' });

		await waitFor(() => expect(screen.getByText('Sì, elimina')).toBeTruthy());
		expect(screen.getByText('No, torna indietro')).toBeTruthy();
	});

	it('does not render the message when none is provided', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		result.current.confirm({ title: 'Elimina' });

		await waitFor(() => expect(screen.getByText('Elimina')).toBeTruthy());

		expect(screen.queryByText('Sei sicuro?')).toBeNull();
	});

	it('hides the dialog after resolving', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		const confirmPromise = result.current.confirm({ title: 'Elimina' });

		await waitFor(() => expect(screen.getByText('Elimina')).toBeTruthy());

		fireEvent.press(screen.getByText('Conferma'));

		await confirmPromise;

		await waitFor(() => expect(screen.queryByText('Elimina')).toBeNull());
	});

	it('handles multiple sequential confirm calls independently', async () => {
		const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

		const firstPromise = result.current.confirm({ title: 'Prima' });
		await waitFor(() => expect(screen.getByText('Prima')).toBeTruthy());
		fireEvent.press(screen.getByText('Conferma'));
		await expect(firstPromise).resolves.toBe(true);

		const secondPromise = result.current.confirm({ title: 'Seconda' });
		await waitFor(() => expect(screen.getByText('Seconda')).toBeTruthy());
		fireEvent.press(screen.getByText('Annulla'));
		await expect(secondPromise).resolves.toBe(false);
	});

	it('throws when used without a provider', async () => {
		await expect(renderHook(() => useConfirmDialog())).rejects.toThrow(
			'useConfirmDialog must be used within a ConfirmDialogProvider'
		);
	});
});