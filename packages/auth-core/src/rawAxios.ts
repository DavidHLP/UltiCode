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

type ViteImportMeta = ImportMeta & {
  env?: { VITE_API_BASE_URL?: string };
};
const viteMeta = import.meta as ViteImportMeta;
const API_BASE_URL = viteMeta.env?.VITE_API_BASE_URL || '/api';

export const rawAxios = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});
