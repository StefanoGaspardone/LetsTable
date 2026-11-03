import { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export const createTestQueryClient = () => new QueryClient({
	defaultOptions: {
		queries: {
			retry: false,
			gcTime: Infinity,
		},
		mutations: {
			gcTime: Infinity,
		},
	},
});

export const createWrapper = () => {
	const queryClient = createTestQueryClient();

	const wrapper = ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client = { queryClient }>
			{children}
		</QueryClientProvider>
	)

	return { wrapper, queryClient }
}