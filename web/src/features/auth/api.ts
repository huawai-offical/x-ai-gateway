import { apiClient } from '@/lib/api'
import type {
  AdminAuthChallenge,
  AdminAuthSettings,
  AdminAuthSettingsUpdatePayload,
  AdminLoginPayload,
  AdminSession,
} from './types'

export function getAdminSession() {
  return apiClient.get<AdminSession>('/admin/auth/session')
}

export function createAdminChallenge() {
  return apiClient.post<AdminAuthChallenge>('/admin/auth/challenge')
}

export function loginAdminConsole(payload: AdminLoginPayload) {
  return apiClient.post<AdminSession>('/admin/auth/login', {
    body: payload,
  })
}

export function logoutAdminConsole() {
  return apiClient.post<void>('/admin/auth/logout', {
    responseType: 'void',
  })
}

export function getAdminAuthSettings() {
  return apiClient.get<AdminAuthSettings>('/admin/auth/settings')
}

export function updateAdminAuthSettings(payload: AdminAuthSettingsUpdatePayload) {
  return apiClient.put<AdminAuthSettings>('/admin/auth/settings', {
    body: payload,
  })
}
