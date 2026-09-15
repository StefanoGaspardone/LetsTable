import { AxiosError } from 'axios';

import { toast } from '@/components/ui/toast';

interface ApiErrorOptions {
    fallback: string;
    statusMessages?: Partial<Record<number, string>>;
    toastError?: boolean;
}

export const hasAxiosStatus = (error: unknown, status: number): boolean => {
    return error instanceof AxiosError && error.response?.status === status;
}

const resolveApiErrorMessage = (error: unknown, options: ApiErrorOptions): string => {
    const { fallback, statusMessages = {} } = options;

    if(!(error instanceof AxiosError)) return fallback;

    const responseMessage = error.response?.data as { message?: unknown } | undefined;
    const apiMessage = typeof responseMessage?.message === 'string' ? responseMessage.message : undefined;
    const status = error.response?.status;

    if(!error.response) return 'Network error. Check your connection and retry.';
    if(status && statusMessages[status]) return apiMessage ?? statusMessages[status] ?? fallback;
    if(status && status >= 500) return 'Server error. Please try again in a moment.';

    return apiMessage ?? fallback;
}

export function handleApiError(error: unknown, options?: ApiErrorOptions & { throwError?: true }): never;

export function handleApiError(error: unknown, options: ApiErrorOptions & { throwError: false }): string;

export function handleApiError(error: unknown, options: ApiErrorOptions & { throwError?: boolean } = { fallback: 'Something went wrong.' }): string | never {
	const message = resolveApiErrorMessage(error, options);
	console.log('[handleApiError] message:', message, 'toastError:', options.toastError);

	if(options.toastError) {
		console.log('[handleApiError] calling toast.add');
		toast.add({ type: 'error', description: message, priority: 'high' });
	}

	if(options.throwError === false) return message;

	console.log('[handleApiError] about to throw');
	throw new Error(message);
}