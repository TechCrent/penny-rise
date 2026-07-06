import { z } from 'zod';

/**
 * Client-side validation schema — mirrors server-side rules from v0.2-011
 * (SignupRequest) so errors appear inline before any network round-trip.
 */
export const registerSchema = z.object({
  displayName: z
    .string()
    .min(1, 'Display name is required')
    .max(100, 'Display name must not exceed 100 characters'),

  email: z
    .string()
    .min(1, 'Email is required')
    .email('Please enter a valid email address')
    .max(320, 'Email must not exceed 320 characters'),

  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .regex(
      /^(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/,
      'Password must contain at least one number and one special character',
    ),

  referralCode: z.string().optional(),

  termsAccepted: z.boolean().refine(value => value === true, {
    message: 'You must accept the Terms of Service and Privacy Policy',
  }),
});

export type RegisterFormValues = z.infer<typeof registerSchema>;
