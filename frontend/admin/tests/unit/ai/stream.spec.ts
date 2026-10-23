jest.mock('@/utils/cookies', () => ({ getToken: () => 'test-token' }))
import { streamAiChat } from '@/utils/aiStream'

const decoder = require('util')
const encoder = new decoder.TextEncoder()
beforeEach(() => {
  process.env.VUE_APP_AI_CHAT_MOCK = 'false'
  process.env.VUE_APP_BASE_API = '/api'
  ;(global as any).TextDecoder = decoder.TextDecoder
  ;(global as any).AbortController = class {
    signal = { aborted: false, addEventListener: jest.fn() }
    abort() { this.signal.aborted = true }
  }
})

it('按同一会话ID发送请求，解析跨网络块的中文和终态', async () => {
  const events: any[] = []
  const bytes = encoder.encode('event: meta\ndata: {"conversationId":"conv_1","messageId":"msg_1"}\n\nevent: delta\ndata: {"content":"中文"}\n\nevent: done\ndata: {}\n\n')
  const parts = [bytes.slice(0, 114), bytes.slice(114, 115), bytes.slice(115)]
  const reader = { read: jest.fn().mockImplementation(async () => parts.length ? { done: false, value: parts.shift() } : { done: true }), releaseLock: jest.fn() }
  ;(global as any).fetch = jest.fn().mockResolvedValue({ ok: true, headers: { get: () => 'text/event-stream' }, body: { getReader: () => reader } })
  await streamAiChat({ conversationId: 'conv_1', message: '昨天营业额？', onEvent: (event) => events.push(event) }).promise
  const options = ((global as any).fetch as jest.Mock).mock.calls[0][1]
  expect(JSON.parse(options.body)).toEqual({ conversationId: 'conv_1', message: '昨天营业额？' })
  expect(options.headers.token).toBe('test-token')
  expect(events.map((event) => event.type)).toEqual(['meta', 'delta', 'done'])
  expect(events[1].content).toBe('中文')
  expect(reader.releaseLock).toHaveBeenCalled()
})

it('HTTP200业务错误的JSON不能误当成成功SSE', async () => {
  ;(global as any).fetch = jest.fn().mockResolvedValue({ ok: true, headers: { get: () => 'application/json' }, json: async () => ({ code: 0, msg: '会话不存在' }) })
  await expect(streamAiChat({ message: '你好', onEvent: jest.fn() }).promise).rejects.toThrow('会话不存在')
})

it('跨网络块解析来源数组，保留真实来源字段及对应消息ID', async () => {
  const source = { documentId: 'wx_order_rules', documentTitle: '订单规则', version: 'v1', chunkId: 'chunk_2', text: '订单必须已接单。' }
  const bytes = encoder.encode('event: sources\ndata: ' + JSON.stringify({ messageId: 'msg_2', sources: [source] }) + '\n\nevent: done\ndata: {}\n\n')
  const parts = [bytes.slice(0, 65), bytes.slice(65, 66), bytes.slice(66)]
  const reader = { read: jest.fn().mockImplementation(async () => parts.length ? { done: false, value: parts.shift() } : { done: true }), releaseLock: jest.fn() }
  ;(global as any).fetch = jest.fn().mockResolvedValue({ ok: true, headers: { get: () => 'text/event-stream' }, body: { getReader: () => reader } })
  const events: any[] = []
  await streamAiChat({ message: '派送条件？', onEvent: (event) => events.push(event) }).promise
  expect(events).toEqual([{ type: 'sources', messageId: 'msg_2', sources: [source] }, { type: 'done' }])
})

it('HTTP409展示服务端会话忙提示且不产生SSE事件', async () => {
  const onEvent = jest.fn()
  ;(global as any).fetch = jest.fn().mockResolvedValue({
    ok: false, status: 409,
    json: async () => ({ code: 0, msg: '该会话正在生成，请等待结束后再发送' })
  })
  await expect(streamAiChat({ message: '再问一次', onEvent }).promise)
    .rejects.toThrow('该会话正在生成，请等待结束后再发送')
  expect(onEvent).not.toHaveBeenCalled()
})

it.each([null, { msg: ' ' }, { msg: { unexpected: true } }])(
  'HTTP错误没有可用提示时保留状态码兜底：%p', async (body) => {
    ;(global as any).fetch = jest.fn().mockResolvedValue({ ok: false, status: 502, json: async () => body })
    await expect(streamAiChat({ message: '你好', onEvent: jest.fn() }).promise)
      .rejects.toThrow('AI 接口请求失败（HTTP 502）')
  }
)

it('HTTP错误不是JSON时仍使用状态码提示', async () => {
  ;(global as any).fetch = jest.fn().mockResolvedValue({
    ok: false, status: 502, json: async () => { throw new SyntaxError('非JSON响应') }
  })
  await expect(streamAiChat({ message: '你好', onEvent: jest.fn() }).promise)
    .rejects.toThrow('AI 接口请求失败（HTTP 502）')
})
