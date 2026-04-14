const ACCESS_TOKEN_KEY = 'loganalysis_access_token'

export const getAccessToken = () => {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.localStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export const setAccessToken = (token) => {
  if (typeof window === 'undefined') {
    return
  }
  if (!token) {
    window.localStorage.removeItem(ACCESS_TOKEN_KEY)
    return
  }
  window.localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export const clearAccessToken = () => {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
}
