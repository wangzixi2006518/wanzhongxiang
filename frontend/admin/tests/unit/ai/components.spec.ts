import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import OrderAnalysis from '@/components/OrderDispatchAnalysis/index.vue'
import RagKnowledge from '@/components/RagKnowledge/index.vue'
import RagSources from '@/components/RagSources/index.vue'
import AiAssistant from '@/views/aiAssistant/index.vue'
import * as api from '@/api/ai'
import { streamAiChat } from '@/utils/aiStream'

jest.mock('@/api/ai', () => {
  const actual = jest.requireActual('@/api/ai')
  return { ...actual, ensureRuleDocumentsReady: jest.fn(), getOrderDispatchAnalysis: jest.fn(), answerOrderRules: jest.fn(), getAiConversations: jest.fn(), getAiMessages: jest.fn(), renameAiConversation: jest.fn(), deleteAiConversation: jest.fn() }
})
jest.mock('@/utils/request', () => ({ __esModule: true, default: jest.fn() }))
jest.mock('@/utils/aiStream', () => ({ streamAiChat: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
const success = (data: any) => ({ data: { code: 1, data } })
const source = { documentId: 'wx_order_rules', documentTitle: '订单规则', version: 'v1', chunkId: 'chunk_2', text: '<script>原文</script> 已接单可派送。' }
const wrappers: any[] = []
const render = (component: any, propsData = {}): any => {
  const wrapper = mount(component, { localVue, propsData, mocks: { $router: { push: jest.fn() }, $message: { error: jest.fn(), success: jest.fn() }, $confirm: jest.fn().mockResolvedValue(true) } })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => { jest.clearAllMocks(); (api.ensureRuleDocumentsReady as jest.Mock).mockResolvedValue(undefined) })
afterEach(() => { wrappers.splice(0).forEach((wrapper) => wrapper.destroy()); document.body.innerHTML = '' })

it('订单分析发送主键并展示响应快照和校验来源，正文按纯文本显示', async () => {
  ;(api.getOrderDispatchAnalysis as jest.Mock).mockResolvedValue(success({ orderId: 7, orderStatus: 3, queriedAt: '2026-10-04 12:00', answer: '满足前提【来源：chunk_2】', sources: [source] }))
  const wrapper = render(OrderAnalysis, { visible: true, orderId: 7, orderNumber: '202600001' })
  await flush()
  expect(api.getOrderDispatchAnalysis).toHaveBeenCalledWith(7)
  const text = document.body.textContent || wrapper.text()
  expect(text).toContain('已接单')
  expect(text).toContain('2026-10-04 12:00')
  expect(wrapper.vm.result.sources[0].text).toBe(source.text)
  const sources = render(RagSources, { sources: [source] })
  expect(sources.findAll('script').length).toBe(0)
  expect(sources.text()).toContain(source.text)
})

it('规则准备失败停止调用分析，业务code0显示错误，不假报资料不足', async () => {
  ;(api.ensureRuleDocumentsReady as jest.Mock).mockRejectedValueOnce(new Error('Embedding失败'))
  const wrapper = render(OrderAnalysis, { visible: true, orderId: 7 })
  await flush()
  expect(api.getOrderDispatchAnalysis).not.toHaveBeenCalled()
  expect(wrapper.vm.error).toBe('Embedding失败')
  ;(api.getOrderDispatchAnalysis as jest.Mock).mockResolvedValueOnce({ data: { code: 0, msg: '订单不存在' } })
  await wrapper.vm.analyze()
  expect(wrapper.vm.error).toBe('订单不存在')
  expect(wrapper.vm.result).toBeNull()
})

it('关闭分析后忽略迟到的响应，不展示上一笔订单结果', async () => {
  let resolve: any
  ;(api.getOrderDispatchAnalysis as jest.Mock).mockImplementationOnce(() => new Promise((done) => { resolve = done }))
  const wrapper = render(OrderAnalysis, { visible: true, orderId: 7 })
  await flush()
  wrapper.vm.close()
  resolve(success({ orderId: 7, answer: '旧回答', sources: [] }))
  await flush()
  expect(wrapper.vm.result).toBeNull()
  expect(wrapper.emitted()['update:visible'][0]).toEqual([false])
})

it('规则问答通过真实适配器发送问题，正常拒答保留空来源，模型失败显示错误', async () => {
  ;(api.answerOrderRules as jest.Mock).mockResolvedValueOnce(success({ answer: '当前检索资料不足以回答该问题。', sources: [] }))
  const wrapper = render(RagKnowledge, { visible: true })
  wrapper.vm.question = '  开始派送？  '
  await wrapper.vm.ask()
  expect(api.answerOrderRules).toHaveBeenCalledWith('开始派送？')
  expect(wrapper.vm.result.sources).toEqual([])
  ;(api.answerOrderRules as jest.Mock).mockRejectedValueOnce(new Error('模型调用失败'))
  await wrapper.vm.ask()
  expect(wrapper.vm.result).toBeNull()
  expect(wrapper.vm.error).toBe('模型调用失败')
})

it('读取服务端会话和消息状态，重命名只有保存成功才更新列表', async () => {
  const conversation = { id: 'conv_1', title: '原名', createdAt: '2026-10-04 12:00', updatedAt: '2026-10-04 12:00' }
  ;(api.getAiConversations as jest.Mock).mockResolvedValue(success([conversation]))
  ;(api.getAiMessages as jest.Mock).mockResolvedValue(success([{ id: 'msg_1', conversationId: 'conv_1', role: 'ASSISTANT', content: '部分回答', status: 'CANCELLED', createdAt: conversation.createdAt }]))
  const wrapper = render(AiAssistant)
  await flush()
  expect(wrapper.vm.activeConversationId).toBe('conv_1')
  expect(wrapper.text()).toContain('已停止 · 回答不完整')
  wrapper.vm.openRename(conversation)
  wrapper.vm.renameTitle = '  新标题  '
  ;(api.renameAiConversation as jest.Mock).mockResolvedValueOnce({ data: { code: 0, msg: '保存失败' } })
  await wrapper.vm.confirmRename()
  expect(wrapper.vm.conversations[0].title).toBe('原名')
  expect(wrapper.vm.renameVisible).toBe(true)
  ;(api.renameAiConversation as jest.Mock).mockResolvedValueOnce(success({ ...conversation, title: '新标题' }))
  await wrapper.vm.confirmRename()
  expect(api.renameAiConversation).toHaveBeenCalledWith('conv_1', '新标题')
  expect(wrapper.vm.conversations[0].title).toBe('新标题')
  expect(wrapper.vm.renameVisible).toBe(false)
})

it('主聊天将本轮来源绑定到消息，完成后展示原文卡片，保留连续会话ID', async () => {
  ;(api.getAiConversations as jest.Mock).mockResolvedValue(success([]))
  let emit: any
  let finish: any
  ;(streamAiChat as jest.Mock).mockImplementation((options) => {
    emit = options.onEvent
    return { abort: jest.fn(), promise: new Promise((resolve) => { finish = resolve }) }
  })
  const wrapper = render(AiAssistant)
  await flush()
  wrapper.vm.inputMessage = '开始派送有什么条件？'
  const sending = wrapper.vm.sendMessage()
  emit({ type: 'meta', conversationId: 'conv_rules', messageId: 'msg_rules' })
  emit({ type: 'delta', content: '订单必须已接单。【来源：chunk_2】' })
  emit({ type: 'sources', messageId: 'msg_rules', sources: [source] })
  await flush()
  expect(wrapper.findAll(RagSources).length).toBe(0)
  emit({ type: 'done' })
  finish()
  await sending
  await flush()
  expect(wrapper.vm.activeConversationId).toBe('conv_rules')
  const message = wrapper.vm.messages[wrapper.vm.messages.length - 1]
  expect(message.id).toBe('msg_rules')
  expect(message.status).toBe('COMPLETED')
  expect(message.sources).toEqual([source])
  expect(wrapper.find(RagSources).text()).toContain('chunk_2')
  expect(wrapper.find(RagSources).text()).toContain(source.text)
  expect(wrapper.findAll('script').length).toBe(0)
})

it.each(['已完成3笔，数据来源：本次查询结果。', '当前检索资料不足以回答该问题。'])(
  '主聊天空来源不展示引用卡片：%s', async (answer) => {
    ;(api.getAiConversations as jest.Mock).mockResolvedValue(success([]))
    const wrapper = render(AiAssistant)
    await flush()
    const message = { id: 'msg_empty', role: 'assistant', content: answer, status: 'GENERATING', sources: null }
    wrapper.vm.messages = [message]
    wrapper.vm.handleStreamEvent({ type: 'sources', messageId: message.id, sources: [] }, message)
    wrapper.vm.handleStreamEvent({ type: 'done' }, message)
    await flush()
    expect(message.sources).toEqual([])
    expect(wrapper.findAll(RagSources).length).toBe(0)
  }
)

it('忽略其他消息来源，错误终态清空来源且保留失败标签', async () => {
  ;(api.getAiConversations as jest.Mock).mockResolvedValue(success([]))
  const wrapper = render(AiAssistant)
  await flush()
  const message = { id: 'msg_current', role: 'assistant', content: '部分回答', status: 'GENERATING', sources: null }
  wrapper.vm.messages = [message]
  wrapper.vm.handleStreamEvent({ type: 'sources', messageId: 'msg_other', sources: [source] }, message)
  expect(message.sources).toBeNull()
  wrapper.vm.handleStreamEvent({ type: 'sources', messageId: message.id, sources: [source] }, message)
  wrapper.vm.handleStreamEvent({ type: 'error', errorMessage: '生成失败' }, message)
  await flush()
  expect(message.sources).toBeNull()
  expect(wrapper.text()).toContain('生成失败')
  expect(wrapper.findAll(RagSources).length).toBe(0)
})

it('主聊天只读检查生效资料，失败时显示真实错误', async () => {
  ;(api.getAiConversations as jest.Mock).mockResolvedValue(success([]))
  const wrapper = render(AiAssistant)
  await flush()
  await wrapper.vm.prepareRules()
  expect(api.ensureRuleDocumentsReady).toHaveBeenCalledWith()
  expect(wrapper.vm.rulesReady).toBe(true)
  ;(api.ensureRuleDocumentsReady as jest.Mock).mockRejectedValueOnce(new Error('Embedding失败'))
  await wrapper.vm.prepareRules()
  expect(wrapper.vm.rulesReady).toBe(false)
  expect(wrapper.vm.$message.error).toHaveBeenCalledWith('Embedding失败')
})
