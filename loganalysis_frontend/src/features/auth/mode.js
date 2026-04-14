const AUTH_STATUS_URL = '/api/v1/auth/status'

let authEnabledCache = null
let authEnabledPromise = null

const resolveEnabledFromPayload = (payload) => {
  return Boolean(payload?.data?.enabled)
}

export const getAuthEnabledCache = () => authEnabledCache

export const resolveAuthEnabled = async ({ force = false } = {}) => {
  if (!force && typeof authEnabledCache === 'boolean') {
    return authEnabledCache
  }
  if (!force && authEnabledPromise) {
    return authEnabledPromise
  }

  authEnabledPromise = fetch(AUTH_STATUS_URL, {
    method: 'GET',
    headers: {
      Accept: 'application/json'
    },
    credentials: 'same-origin',
    cache: 'no-store'
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`status ${response.status}`)
      }
      const payload = await response.json()
      const enabled = resolveEnabledFromPayload(payload)
      authEnabledCache = enabled
      return enabled
    })
    .catch(() => {
      // Fail-safe: if status endpoint is unreachable, keep auth on.
      authEnabledCache = true
      return true
    })
    .finally(() => {
      authEnabledPromise = null
    })

  return authEnabledPromise
}
