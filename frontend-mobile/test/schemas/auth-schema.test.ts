import { loginSchema, signupSchema, activateSchema } from '@/schemas/auth-schema';

describe('loginSchema', () => {
	it('accepts a valid identifier and password', () => {
		const result = loginSchema.safeParse({ identifier: 'stefano@example.com', password: 'password123' });

		expect(result.success).toBe(true);
	});

	it('rejects an empty identifier', () => {
		const result = loginSchema.safeParse({ identifier: '', password: 'password123' });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues[0].message).toBe('Inserisci email o username');
		}
	});

	it('rejects an empty password', () => {
		const result = loginSchema.safeParse({ identifier: 'stefano@example.com', password: '' });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues[0].message).toBe('Inserisci la password');
		}
	});
});

describe('signupSchema', () => {
	const validPayload = {
		username: 'stefano',
		email: 'stefano@example.com',
		password: 'password123',
		confirmPassword: 'password123',
		acceptTerms: true,
	};

	it('accepts a fully valid payload', () => {
		const result = signupSchema.safeParse(validPayload);

		expect(result.success).toBe(true);
	});

	it('rejects a username shorter than 3 characters', () => {
		const result = signupSchema.safeParse({ ...validPayload, username: 'ab' });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues.some(i => i.message === 'Minimo 3 caratteri')).toBe(true);
		}
	});

	it('rejects a username longer than 50 characters', () => {
		const result = signupSchema.safeParse({ ...validPayload, username: 'a'.repeat(51) });

		expect(result.success).toBe(false);
	});

	it('rejects an invalid email', () => {
		const result = signupSchema.safeParse({ ...validPayload, email: 'not-an-email' });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues.some(i => i.message === 'Email non valida')).toBe(true);
		}
	});

	it('rejects a password shorter than 8 characters', () => {
		const result = signupSchema.safeParse({ ...validPayload, password: 'short1', confirmPassword: 'short1' });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues.some(i => i.message === 'Minimo 8 caratteri')).toBe(true);
		}
	});

	it('rejects a password longer than 72 characters', () => {
		const longPassword = 'a'.repeat(73);
		const result = signupSchema.safeParse({ ...validPayload, password: longPassword, confirmPassword: longPassword });

		expect(result.success).toBe(false);
	});

	it('rejects an empty confirmPassword', () => {
		const result = signupSchema.safeParse({ ...validPayload, confirmPassword: '' });

		expect(result.success).toBe(false);
	});

	it('rejects when password and confirmPassword do not match', () => {
		const result = signupSchema.safeParse({ ...validPayload, confirmPassword: 'differentPassword' });

		expect(result.success).toBe(false);
		if(!result.success) {
			const mismatchIssue = result.error.issues.find(i => i.path.includes('confirmPassword'));
			expect(mismatchIssue?.message).toBe('Le password non coincidono');
		}
	});

	it('rejects when acceptTerms is false', () => {
		const result = signupSchema.safeParse({ ...validPayload, acceptTerms: false });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues.some(i => i.message === 'Devi accettare i Termini e Condizioni per proseguire')).toBe(true);
		}
	});

	it('rejects when acceptTerms is missing', () => {
		const { acceptTerms, ...payloadWithoutTerms } = validPayload;
		const result = signupSchema.safeParse(payloadWithoutTerms);

		expect(result.success).toBe(false);
	});
});

describe('activateSchema', () => {
	it('accepts a 6-digit OTP code', () => {
		const result = activateSchema.safeParse({ otpCode: '123456' });

		expect(result.success).toBe(true);
	});

	it('rejects an OTP code shorter than 6 digits', () => {
		const result = activateSchema.safeParse({ otpCode: '12345' });

		expect(result.success).toBe(false);
		if(!result.success) {
			expect(result.error.issues[0].message).toBe('Il codice deve avere 6 cifre');
		}
	});

	it('rejects an OTP code longer than 6 digits', () => {
		const result = activateSchema.safeParse({ otpCode: '1234567' });

		expect(result.success).toBe(false);
	});

	it('rejects an empty OTP code', () => {
		const result = activateSchema.safeParse({ otpCode: '' });

		expect(result.success).toBe(false);
	});
});