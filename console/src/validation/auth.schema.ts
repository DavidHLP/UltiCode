import { z } from "zod";

/**
 * Login form validation schema
 */
export const loginSchema = z.object({
  username: z
    .string()
    .min(1, "validation.username.required")
    .min(3, "validation.username.minLength")
    .max(20, "validation.username.maxLength")
    .regex(/^[a-zA-Z0-9_]+$/, "validation.username.format"),
  password: z
    .string()
    .min(1, "validation.password.required")
    .min(8, "validation.password.minLength"),
});

export type LoginForm = z.infer<typeof loginSchema>;

/**
 * Registration form validation schema
 */
export const registerSchema = z
  .object({
    username: z
      .string()
      .min(1, "validation.username.required")
      .min(3, "validation.username.minLength")
      .max(20, "validation.username.maxLength")
      .regex(/^[a-zA-Z0-9_]+$/, "validation.username.format"),
    email: z
      .string()
      .min(1, "validation.email.required")
      .email("validation.email.invalid"),
    password: z
      .string()
      .min(1, "validation.password.required")
      .min(8, "validation.password.minLength")
      .regex(/[A-Z]/, "validation.password.uppercase")
      .regex(/[a-z]/, "validation.password.lowercase")
      .regex(/[0-9]/, "validation.password.number"),
    confirmPassword: z.string().min(1, "validation.confirmPassword.required"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "validation.confirmPassword.mismatch",
    path: ["confirmPassword"],
  });

export type RegisterForm = z.infer<typeof registerSchema>;
