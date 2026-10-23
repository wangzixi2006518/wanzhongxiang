import { getToken } from '@/utils/cookies'
import { RagSource } from '@/api/ai'

export type AiStreamEventType =
  | 'meta'
  | 'tool_start'
  | 'tool_result'
  | 'sources'
  | 'delta'
  | 'done'
  | 'error'

export interface AiStreamEvent {
  type: AiStreamEventType
  conversationId?: string
  messageId?: string
  content?: string
  toolCallId?: string
  toolName?: string
  arguments?: { [key: string]: any }
  summary?: string
  durationMs?: number
  sources?: RagSource[]
  errorCode?: string
  errorMessage?: string
}

export interface StreamChatOptions {
  conversationId?: string
  message: string
  mode?: 'OPERATIONS' | 'KNOWLEDGE' | 'AUTO'
  onEvent: (event: AiStreamEvent) => void
}

const isMockEnabled = () => process.env.VUE_APP_AI_MOCK === 'true'
const isChatMockEnabled = () =>
  process.env.VUE_APP_AI_CHAT_MOCK === undefined
    ? isMockEnabled()
    : process.env.VUE_APP_AI_CHAT_MOCK === 'true'

const emitMockEvents = async (
  options: StreamChatOptions,
  signal: AbortSignal
) => {
  const conversationId = options.conversationId || `mock-${Date.now()}`
  const messageId = `mock-message-${Date.now()}`
  const wait = (duration: number) =>
    new Promise((resolve, reject) => {
      const timer = window.setTimeout(resolve, duration)
      signal.addEventListener('abort', () => {
        window.clearTimeout(timer)
        reject(new DOMException('请求已取消', 'AbortError'))
      })
    })

  options.onEvent({ type: 'meta', conversationId, messageId })
  await wait(350)
  options.onEvent({
    type: 'tool_start',
    toolCallId: 'mock-tool-1',
    toolName: 'queryBusinessSummary',
    arguments: { begin: '最近7天', end: '今天' }
  })
  await wait(650)
  options.onEvent({
    type: 'tool_result',
    toolCallId: 'mock-tool-1',
    toolName: 'queryBusinessSummary',
    summary: '已获得最近 7 天交易额、订单量和有效订单数据',
    durationMs: 128
  })
  await wait(300)

  const answer =
    '这是前端 Mock 返回的演示结果。\n\n完成 Spring AI 和 Tool Calling 接口后，系统会在这里展示基于真实经营数据生成的分析结论、问题定位和运营建议。'
  for (const part of answer.match(/.{1,8}/g) || []) {
    if (signal.aborted) return
    options.onEvent({ type: 'delta', content: part })
    await wait(80)
  }
  options.onEvent({ type: 'done', conversationId, messageId })
}

const parseEventBlock = (block: string): AiStreamEvent | null => {
  let eventName = ''
  const dataLines: string[] = []

  block.split(/\r?\n/).forEach((line) => {
    if (line.indexOf('event:') === 0) {
      eventName = line.slice(6).trim()
    } else if (line.indexOf('data:') === 0) {
      dataLines.push(line.slice(5).trim())
    }
  })

  if (!dataLines.length) return null

  try {
    const payload = JSON.parse(dataLines.join('\n'))
    return Object.assign({ type: eventName || payload.type || 'delta' }, payload)
  } catch (error) {
    return {
      type: 'error',
      errorCode: 'INVALID_SSE_EVENT',
      errorMessage: '服务端返回了无法解析的 SSE 数据'
    }
  }
}

export const streamAiChat = (options: StreamChatOptions) => {
  const controller = new AbortController()

  const promise = (async () => {
    if (isChatMockEnabled()) {
      await emitMockEvents(options, controller.signal)
      return
    }

    const baseUrl = (process.env.VUE_APP_BASE_API || '/api').replace(/\/$/, '')
    const response = await fetch(`${baseUrl}/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        token: getToken() || ''
      },
      body: JSON.stringify({
        conversationId: options.conversationId || undefined,
        message: options.message
      }),
      signal: controller.signal
    })

    if (!response.ok) {
      const body = await response.json().catch(() => null)
      const message = body && typeof body.msg === 'string' && body.msg.trim()
      throw new Error(message || `AI 接口请求失败（HTTP ${response.status}）`)
    }
    if ((response.headers.get('Content-Type') || '').indexOf('text/event-stream') === -1) {
      const body = await response.json().catch(() => null)
      throw new Error(body && body.msg || '服务端未返回流式回答')
    }
    if (!response.body) {
      throw new Error('当前浏览器不支持流式响应')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let terminal = false
    const emit = (block: string) => {
      if (controller.signal.aborted) return
      const event = parseEventBlock(block)
      if (!event) return
      if (event.type === 'done' || event.type === 'error') terminal = true
      options.onEvent(event)
    }

    try {
      while (true) {
      const result = await reader.read()
      buffer += decoder.decode(result.value || new Uint8Array(), {
        stream: !result.done
      })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() || ''
      blocks.forEach(emit)
      if (result.done) break
      }

    if (buffer.trim()) {
      emit(buffer)
    }
    if (controller.signal.aborted) throw new DOMException('请求已取消', 'AbortError')
    if (!terminal) throw new Error('回答连接中断，请刷新历史会话确认保存状态')
    } finally { reader.releaseLock() }
  })()

  return {
    abort: () => controller.abort(),
    promise
  }
}

export const aiMockEnabled = isMockEnabled
export const aiChatMockEnabled = isChatMockEnabled
