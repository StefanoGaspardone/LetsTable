import { View, Pressable } from 'react-native';
import { render, screen, fireEvent, act } from '@testing-library/react-native';

import { Text } from '@/components/ui/text';
import { ToastProvider, useToast } from '@/contexts/toast-context';

interface ToastTrigger {
	message: string;
	variant?: 'success' | 'error' | 'info';
}

const ToastHarness = ({ triggers }: { triggers: ToastTrigger[] }) => {
	const { showToast } = useToast();

	return (
		<View>
			{triggers.map((trigger, index) => (
				<Pressable key = { index } testID = { `trigger-${index}` } onPress = { () => showToast(trigger.message, trigger.variant) }>
					<Text>{`trigger-${index}`}</Text>
				</Pressable>
			))}
		</View>
	);
}

const renderHarness = async (triggers: ToastTrigger[]) => {
	await render(
		<ToastProvider>
			<ToastHarness triggers = { triggers }/>
		</ToastProvider>
	);
}

const pressTrigger = async (testId: string) => {
	await act(async () => {
		fireEvent.press(screen.getByTestId(testId));
	});
}

const advanceTime = async (ms: number) => {
	await act(async () => {
		jest.advanceTimersByTime(ms);
	});
	await act(async () => {
		jest.advanceTimersByTime(250);
	});
}

describe('ToastProvider / useToast', () => {
	beforeEach(() => {
		jest.useFakeTimers();
	});

	afterEach(() => {
		jest.useRealTimers();
	});

	it('does not show a toast before showToast is called', async () => {
		await renderHarness([{ message: 'Some message' }]);

		expect(screen.queryByText('Some message')).toBeNull();
	});

	it('shows the toast message after calling showToast', async () => {
		await renderHarness([{ message: 'Operazione completata' }]);

		await pressTrigger('trigger-0');

		expect(screen.getByText('Operazione completata')).toBeTruthy();
	});

	it('defaults to the "info" variant when none is specified', async () => {
		await renderHarness([{ message: 'Messaggio generico' }]);

		await pressTrigger('trigger-0');

		expect(screen.getByText('Messaggio generico')).toBeTruthy();
	});

	it('hides the toast automatically after the timeout elapses', async () => {
		await renderHarness([{ message: 'Messaggio temporaneo' }]);

		await pressTrigger('trigger-0');

		expect(screen.getByText('Messaggio temporaneo')).toBeTruthy();

		await advanceTime(2800);

		expect(screen.queryByText('Messaggio temporaneo')).toBeNull();
	});

	it('does not hide the toast before the timeout elapses', async () => {
		await renderHarness([{ message: 'Messaggio ancora visibile' }]);

		await pressTrigger('trigger-0');

		await advanceTime(2000);

		expect(screen.getByText('Messaggio ancora visibile')).toBeTruthy();
	});

	it('replaces the current toast when showToast is called again before the timeout', async () => {
		await renderHarness([
			{ message: 'Primo messaggio' },
			{ message: 'Secondo messaggio' },
		]);

		await pressTrigger('trigger-0');

		await advanceTime(1000);

		await pressTrigger('trigger-1');

		expect(screen.queryByText('Primo messaggio')).toBeNull();
		expect(screen.getByText('Secondo messaggio')).toBeTruthy();
	});

	it('clears the previous timeout when a new toast replaces an old one', async () => {
		await renderHarness([
			{ message: 'Primo messaggio' },
			{ message: 'Secondo messaggio' },
		]);

		await pressTrigger('trigger-0');

		await advanceTime(1000);

		await pressTrigger('trigger-1');

		await advanceTime(1800);

		expect(screen.getByText('Secondo messaggio')).toBeTruthy();

		await advanceTime(1000);

		expect(screen.queryByText('Secondo messaggio')).toBeNull();
	});

    it('throws when used without a provider', async () => {
        const BrokenComponent = () => {
            useToast();
            return null;
        }

        const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});

        let caughtError: Error | null = null;

        try {
            await render(<BrokenComponent/>);
        } catch(error) {
            caughtError = error as Error;
        }

        expect(caughtError).toBeInstanceOf(Error);
        expect(caughtError?.message).toBe('useToast must be used within a ToastProvider');

        consoleError.mockRestore();
    });
});