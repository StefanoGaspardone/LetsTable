import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

import { TooltipProvider } from '@/components/ui/tooltip';

import { AuthProvider } from '@/contexts/auth-context';
import { ThemeProvider } from '@/contexts/theme-context';

import '@/index.css';

import App from '@/App.tsx';

createRoot(document.getElementById('root')!).render(
    <StrictMode>
		<BrowserRouter>
			<ThemeProvider>
				<AuthProvider>
					<TooltipProvider>
						<App/>
					</TooltipProvider>
				</AuthProvider>
			</ThemeProvider>
        </BrowserRouter>
    </StrictMode>,
);