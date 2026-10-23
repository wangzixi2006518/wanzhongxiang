import request from '@/utils/request'
import { AxiosRequestConfig, AxiosResponse } from 'axios'

function aiRequest<T>(config: AxiosRequestConfig): Promise<AxiosResponse<ApiResult<T>>> {
  return request(config)
}

export interface AiConversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ApiResult<T> {
  code: number
  msg?: string
  data: T
}

export interface RagSource {
  documentId: string
  documentTitle: string
  version: string
  chunkId: string
  text: string
}

export interface RagAnswer {
  answer: string
  sources: RagSource[]
}

export interface OrderDispatchAnalysis extends RagAnswer {
  orderId: number | string
  orderStatus: number
  queriedAt: string
}

export interface AiHealth {
  status: 'UP' | 'DOWN'
  provider: string
  ragReady: boolean
}

export function resultData<T>(response: { data: ApiResult<T> }): T {
  if (!response.data || response.data.code !== 1) {
    throw new Error(response.data && response.data.msg || '请求未成功，请重试')
  }
  return response.data.data
}

export function aiErrorMessage(error: any, fallback = '请求失败，请稍后重试'): string {
  const body = error && error.response && error.response.data
  if (body && typeof body.msg === 'string' && body.msg) return body.msg
  return error && error.message && !error.response ? error.message : fallback
}

export interface AiMessage {
  id: string
  conversationId: string
  role: 'USER' | 'ASSISTANT'
  content: string
  status: 'GENERATING' | 'COMPLETED' | 'CANCELLED' | 'FAILED'
  createdAt: string
  updatedAt: string
}

export interface AiSource {
  documentId: string
  documentName: string
  chunkId: string
  score: number
  excerpt: string
}

export interface KnowledgeDocument {
  id: string
  name: string
  status: 'PENDING' | 'INDEXING' | 'READY' | 'FAILED'
  chunkCount: number
  createdAt: string
  errorMessage?: string
}

export const createAiConversation = (title?: string) =>
  request({
    url: '/ai/conversations',
    method: 'post',
    data: { title: title || '新会话' }
  })

export const getAiConversations = () =>
  request({
    url: '/ai/conversations',
    method: 'get'
  })

export const getAiMessages = (conversationId: string) =>
  request({
    url: `/ai/conversations/${conversationId}/messages`,
    method: 'get'
  })

export const deleteAiConversation = (conversationId: string) =>
  request({
    url: `/ai/conversations/${conversationId}`,
    method: 'delete'
  })

export const renameAiConversation = (conversationId: string, title: string) =>
  aiRequest<AiConversation>({
    url: `/ai/conversations/${encodeURIComponent(conversationId)}/title`,
    method: 'put',
    data: { title }
  })

export const getAiHealth = () =>
  aiRequest<AiHealth>({ url: '/ai/health', method: 'get' })

export const askAiOnce = (message: string) =>
  aiRequest<{ content: string }>({ url: '/ai/chat', method: 'post', data: { message } })

export const answerOrderRules = (message: string) =>
  aiRequest<RagAnswer>({ url: '/ai/rag/answer', method: 'post', data: { message } })

export const getOrderDispatchAnalysis = (orderId: number | string) =>
  aiRequest<OrderDispatchAnalysis>({
    url: `/ai/orders/${encodeURIComponent(String(orderId))}/dispatch-analysis`,
    method: 'get'
  })

export const getRuleDocumentsReady = () =>
  aiRequest<boolean>({ url: '/ai/rag/ready', method: 'get' })

// 只合并同时发生的只读检查；后续请求重新检查当前生效资料。
let ruleReadinessRequest: Promise<void> | null = null

export function ensureRuleDocumentsReady(): Promise<void> {
  if (ruleReadinessRequest) return ruleReadinessRequest
  ruleReadinessRequest = getRuleDocumentsReady().then((response) => {
    const ready = resultData(response)
    if (typeof ready !== 'boolean') throw new Error('资料状态返回无效，请重试')
    if (!ready) throw new Error('暂无可用规则资料，请联系管理员更新资料')
  }).then(() => {
    ruleReadinessRequest = null
  }, (error) => {
    ruleReadinessRequest = null
    throw error
  })
  return ruleReadinessRequest
}

export const getKnowledgeDocuments = () =>
  request({
    url: '/ai/knowledge/documents',
    method: 'get'
  })

export const uploadKnowledgeDocument = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/ai/knowledge/documents',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deleteKnowledgeDocument = (documentId: string) =>
  request({
    url: `/ai/knowledge/documents/${documentId}`,
    method: 'delete'
  })

export const rebuildKnowledgeIndex = () =>
  request({
    url: '/ai/knowledge/reindex',
    method: 'post'
  })
