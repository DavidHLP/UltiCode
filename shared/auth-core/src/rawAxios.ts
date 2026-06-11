/**
 * Independent axios instance for /auth/refresh calls.
 *
 * Does NOT mount any interceptors (neither CSRF nor auth) to prevent
 * infinite loops when refresh itself returns 401.
 *
 * The whole point of this file: structural isolation from the main axios
 * instance. URL-string blacklists are fragile; an independent instance
 * guarantees no interceptor can ever touch a refresh call.
 */
import axios from 'axios';

const API_BASE_URL =
  (typeof import.meta !== 'undefined' &&
    (import.meta as { env?: { VITE_API_BASE_URL?: string } }).env?.VITE_API_BASE_URL) ||
  'http://localhost:9001';

export const rawAxios = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});
