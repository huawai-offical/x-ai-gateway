import { Navigate } from 'react-router-dom'

export function ErrorRulesPage() {
  return <Navigate to="/ops/governance?tab=error-policies" replace />
}
