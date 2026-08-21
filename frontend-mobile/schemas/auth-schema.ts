import { z } from 'zod';

export const loginSchema = z.object({
	identifier: z.string().min(1, 'Inserisci email o username'),
	password: z.string().min(1, 'Inserisci la password'),
});

export const signupSchema = z
	.object({
		username: z.string().min(3, 'Minimo 3 caratteri').max(50),
		email: z.string().email('Email non valida'),
		password: z.string().min(8, 'Minimo 8 caratteri').max(72),
		confirmPassword: z.string().min(1, 'Conferma la password'),
	})
	.refine((data) => data.password === data.confirmPassword, {
		message: 'Le password non coincidono',
		path: ['confirmPassword'],
	});

export const activateSchema = z.object({
	otpCode: z.string().length(6, 'Il codice deve avere 6 cifre'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type SignupFormValues = z.infer<typeof signupSchema>;
export type ActivateFormValues = z.infer<typeof activateSchema>;