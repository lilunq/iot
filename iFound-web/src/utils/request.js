import axios from 'axios'

export const iotRequest = axios.create({
  baseURL: '/iot',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'X-MCP-Token': 'mcp-server-secret-token-2026'
  }
})

export const aiRequest = axios.create({
  baseURL: '/xian',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
})

export const posRequest = axios.create({
  baseURL: '/pos',
  timeout: 130000,
  headers: { 'Content-Type': 'application/json' }
})

export const ragflowRequest = axios.create({
  baseURL: '/ragflow',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

export const keliuRequest = axios.create({
  baseURL: '/keliuApi',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

export const emsRequest = axios.create({
  baseURL: '/emsAgent',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

ragflowRequest.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('Ragflow request error:', err)
    return Promise.reject(err)
  }
)

iotRequest.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('IoT request error:', err)
    return Promise.reject(err)
  }
)

aiRequest.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('AI request error:', err)
    return Promise.reject(err)
  }
)

posRequest.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('POS request error:', err)
    return Promise.reject(err)
  }
)

keliuRequest.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.warn('Keliu request error:', err.message)
    return null
  }
)

emsRequest.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('EMS request error:', err)
    return Promise.reject(err)
  }
)

export default iotRequest
