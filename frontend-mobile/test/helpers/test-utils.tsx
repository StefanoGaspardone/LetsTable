import { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export const createTestQueryClient = () => new QueryClient({
	defaultOptions: {
		queries: {
			retry: false,
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