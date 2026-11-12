import { defineStore } from 'pinia'

function getCookie(name) {
  const cookies = document.cookie.split('; ')
  for (const item of cookies) {
    const [key, ...valueParts] = item.split('=')
    if (key === name) {
      return valueParts.join('=')
    }
  }
  return ''
}

function parseTokenCookie() {
  const raw = getCookie('authorized-token')
  if (!raw) return null
  try {
    return JSON.parse(decodeURIComponent(raw))
  } catch {
    return null
  }
}

const tokenInfo = parseTokenCookie()

export const useUserStore = defineStore('user', {
  state: () => ({
    user_id: tokenInfo?.user?.id || '9',
    user_name: tokenInfo?.user?.name || '开发者',
    org_id: tokenInfo?.org?.id || '3',
    org_cid: tokenInfo?.org?.cascade_id || '0.001.001.001',
    subarea_cid: '0.001.001.001',
    token: sessionStorage.getItem('access_token') || tokenInfo?.accessToken || ''
  }),
  persist: {
    key: 'user-store',
    storage: localStorage
  }
})
