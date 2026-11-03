import { ReactNode } from 'react';
import { render, renderHook, screen, fireEvent, act } from '@testing-library/react-native';

import { ConfirmDialogProvider, useConfirmDialog } from '@/contexts/confirm-dialog-context';

type ConfirmOptions = Parameters<ReturnType<typeof useConfirmDialog>['confirm']>[0];

const wrapper = ({ children }: { children: ReactNode }) => (
	<ConfirmDialogProvider>{children}</ConfirmDialogProvider>
);

const renderDialog = async () => {
	const { result } = await renderHook(() => useConfirmDialog(), { wrapper });

	const open = async (options: ConfirmOptions) => {
		let promise!: Promise<boolean>;
		await act(async () => {
			promise = result.current.confirm(options);
		});
		return { promise };
	}

	return { open };
}

describe('ConfirmDialogProvider / useConfirmDialog', () => {
	it('does not show the dialog before confirm is called', async () => {
		await render(<ConfirmDialogProvider><></></ConfirmDialogProvider>);

		expect(screen.queryByText('Annulla')).toBeNull();
	});

	it('shows the dialog with the given title and message after calling confirm', async () => {
		const { open } = await renderDialog();

		await open({ title: 'Elimina partita', message: 'Sei sicuro?' });

		expect(screen.getByText('Elimina partita')).toBeTruthy();
		expect(screen.getByText('Sei sicuro?')).toBeTruthy();
	});

	it('does not render the message when none is provided', async () => {
		const { open } = await renderDialog();

		await open({ title: 'Elimina' });

		expect(screen.getByText('Elimina')).toBeTruthy();
		expect(screen.queryByText('Sei sicuro?')).toBeNull();
	});

	it('uses the default labels', async () => {
		const { open } = await renderDialog();

		await open({ title: 'Elimina' });

		expect(screen.getByText('Conferma')).toBeTruthy();
		expect(screen.getByText('Annulla')).toBeTruthy();
	});

	it('uses custom confirm and cancel labels when provided', async () => {
		const { open } = await renderDialog();

		await open({ title: 'Elimina', confirmLabel: 'Sì, elimina', cancelLabel: 'No, torna indietro' });

		expect(screen.getByText('Sì, elimina')).toBeTruthy();
		expect(screen.getByText('No, torna indietro')).toBeTruthy();
	});

	it('hides the cancel button for info-only dialogs', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Info', confirmLabel: 'Ok', infoOnly: true });

		expect(screen.queryByText('Annulla')).toBeNull();

		await fireEvent.press(screen.getByText('Ok'));

		await expect(promise).resolves.toBe(true);
	});

	it('resolves to true when the confirm button is pressed', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Elimina' });
		await fireEvent.press(screen.getByText('Conferma'));

		await expect(promise).resolves.toBe(true);
	});

	it('resolves to true for destructive dialogs too', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Elimina', confirmLabel: 'Elimina definitivamente', destructive: true });
		await fireEvent.press(screen.getByText('Elimina definitivamente'));

		await expect(promise).resolves.toBe(true);
	});

	it('resolves to false when the cancel button is pressed', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Elimina' });
		await fireEvent.press(screen.getByText('Annulla'));

		await expect(promise).resolves.toBe(false);
	});

	it('resolves to false when the backdrop is pressed', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Elimina' });
		await fireEvent.press(screen.getByTestId('confirm-dialog-backdrop'));

		await expect(promise).resolves.toBe(false);
	});

	it('resolves to false when the modal is closed with the back button', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Elimina' });
		await fireEvent(screen.getByTestId('confirm-dialog-backdrop'), 'requestClose');

		await expect(promise).resolves.toBe(false);
		expect(screen.queryByText('Elimina')).toBeNull();
	});

	it('keeps the dialog open when pressing inside the card', async () => {
		const { open } = await renderDialog();
		const stopPropagation = jest.fn();

		await open({ title: 'Elimina', message: 'Sei sicuro?' });
		await fireEvent.press(screen.getByText('Sei sicuro?'), { stopPropagation });

		expect(stopPropagation).toHaveBeenCalled();
		expect(screen.getByText('Elimina')).toBeTruthy();
	});

	it('hides the dialog after resolving', async () => {
		const { open } = await renderDialog();

		const { promise } = await open({ title: 'Elimina' });
		await fireEvent.press(screen.getByText('Conferma'));
		await promise;

		expect(screen.queryByText('Elimina')).toBeNull();
	});

	it('resolves a pending confirm to false when a new one is opened', async () => {
		const { open } = await renderDialog();

		const { promise: firstPromise } = await open({ title: 'Prima' });
		const { promise: secondPromise } = await open({ title: 'Seconda' });

		await expect(firstPromise).resolves.toBe(false);
		expect(screen.queryByText('Prima')).toBeNull();
		expect(screen.getByText('Seconda')).toBeTruthy();

		await fireEvent.press(screen.getByText('Conferma'));

		await expect(secondPromise).resolves.toBe(true);
	});

	it('handles multiple sequential confirm calls independently', async () => {
		const { open } = await renderDialog();

		const { promise: firstPromise } = await open({ title: 'Prima' });
		await fireEvent.press(screen.getByText('Conferma'));
		await expect(firstPromise).resolves.toBe(true);

		const { promise: secondPromise } = await open({ title: 'Seconda' });
		await fireEvent.press(screen.getByText('Annulla'));
		await expect(secondPromise).resolves.toBe(false);
	});

	it('throws when used without a provider', async () => {
		const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});

		await expect(renderHook(() => useConfirmDialog())).rejects.toThrow(
			'useConfirmDialog must be used within a ConfirmDialogProvider'
		);

		consoleError.mockRestore();
	});
});