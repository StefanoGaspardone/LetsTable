import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { loginSchema, type LoginFormValues } from '@/schemas/auth-schema';

import { login } from '@/apis/auth';
import { clearAuthTokens } from '@/apis/axiosConfig';

import { useAuth } from '@/contexts/auth-context';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldError, FieldLabel } from '@/components/ui/field';
import Logo from '@/components/layout/logo';
import { toast } from '@/components/ui/toast';

const LoginForm = () => {
	const { setUser } = useAuth();

	const [showPassword, setShowPassword] = useState<boolean>(false);
	const [isLoading, setIsLoading] = useState<boolean>(false);

	const form = useForm<LoginFormValues>({
		resolver: zodResolver(loginSchema),
		mode: 'onChange',
		defaultValues: { identifier: '', password: '' },
	});

	const onSubmit = async (values: LoginFormValues) => {
		if(isLoading || !form.formState.isValid) return;

		setIsLoading(true);
		
        try {
			const res = await login(values);

			if(res.user.role !== 'ADMIN') {
				toast.add({ type: 'error', description: 'Invalid credentials' });

				clearAuthTokens();
				return;
			}

			setUser(res.user);
		} catch {
			// swallow
		} finally {
			setIsLoading(false);
		}
	}

	return (
		<div className = 'flex min-h-screen items-center justify-center bg-muted/40 px-4'>
			<div className = 'flex w-full max-w-sm flex-col gap-6'>
				<div className = 'flex flex-col items-center gap-2'>
					<Logo size = { 100 }/>
					<h1 className = 'font-heading text-2xl font-bold'>Fetchly Admin</h1>
					<p className = 'text-muted-foreground text-sm'>Sign in to manage the store.</p>
				</div>
				<form onSubmit = { form.handleSubmit(onSubmit) } className = 'flex flex-col gap-4'>
					<Controller name = 'identifier' control = { form.control } render = { ({ field, fieldState }) => (
						<Field>
							<FieldLabel htmlFor = { field.name }>Email or username</FieldLabel>
							<Input { ...field } id = { field.name } aria-invalid = { fieldState.invalid } placeholder = 'admin@fetchly.local' className = 'bg-card'/>
							{fieldState.invalid && <FieldError errors = { [fieldState.error] }/>}
						</Field>
					)}/>
					<Controller name = 'password' control = { form.control } render = { ({ field, fieldState }) => (
						<Field>
							<FieldLabel htmlFor = { field.name }>Password</FieldLabel>
							<div className = 'relative'>
								<Input { ...field } id = { field.name } type = { showPassword ? 'text' : 'password' } aria-invalid = { fieldState.invalid } className = 'pr-10 bg-card' placeholder = '&#9679;&#9679;&#9679;&#9679;&#9679;'/>
								<button type = 'button' onClick = { () => setShowPassword(v => !v) } className = 'text-muted-foreground hover:text-foreground absolute top-1/2 right-3 -translate-y-1/2 cursor-pointer' tabIndex = { -1 }>
								    {showPassword ? <EyeOff className = 'h-4 w-4'/> : <Eye className = 'h-4 w-4'/>}
								</button>
							</div>
							{fieldState.invalid && <FieldError errors = { [fieldState.error] }/>}
						</Field>
					)}/>
					<Button type = 'submit' disabled = { isLoading || !form.formState.isValid } className = 'cursor-pointer'>
						{isLoading ? 'Signing in...' : 'Sign in'}
					</Button>
				</form>
			</div>
		</div>
	)
}

export default LoginForm;