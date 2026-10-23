jest.mock('@/utils/request', () => ({ __esModule: true, default: jest.fn() }))

describe('AI API 与生效资料检查', () => {
  let api: any
  let request: any
  beforeEach(() => {
    jest.resetModules()
    api = require('@/api/ai')
    request = require('@/utils/request').default
  })
  it('并发检查共享一次GET，后续调用重新读取状态，不重建资料', async () => {
    request.mockResolvedValue({ data: { code: 1, data: true } })
    const first = api.ensureRuleDocumentsReady()
    expect(api.ensureRuleDocumentsReady()).toBe(first)
    await expect(first).resolves.toBeUndefined()
    await expect(api.ensureRuleDocumentsReady()).resolves.toBeUndefined()
    expect(request).toHaveBeenCalledTimes(2)
    expect(request).toHaveBeenCalledWith({ url: '/ai/rag/ready', method: 'get' })
  })
  it('业务失败不会缓存成功；失败后可重试', async () => {
    request.mockResolvedValueOnce({ data: { code: 0, msg: '模型调用失败' } })
      .mockResolvedValueOnce({ data: { code: 1, data: true } })
    await expect(api.ensureRuleDocumentsReady()).rejects.toThrow('模型调用失败')
    await expect(api.ensureRuleDocumentsReady()).resolves.toBeUndefined()
    expect(request).toHaveBeenCalledTimes(2)
  })
  it('没有生效资料时拒绝继续，下一次检查可以恢复', async () => {
    request.mockResolvedValueOnce({ data: { code: 1, data: false } })
      .mockResolvedValueOnce({ data: { code: 1, data: true } })
    await expect(api.ensureRuleDocumentsReady()).rejects.toThrow('暂无可用规则资料')
    await expect(api.ensureRuleDocumentsReady()).resolves.toBeUndefined()
  })
  it('无效的非布尔响应不能当作资料已就绪', async () => {
    request.mockResolvedValue({ data: { code: 1, data: 3 } })
    await expect(api.ensureRuleDocumentsReady()).rejects.toThrow('资料状态返回无效')
  })
  it('使用订单主键、正文与标题，路径不重复添加admin前缀', async () => {
    request.mockResolvedValue({ data: { code: 1, data: {} } })
    await api.getOrderDispatchAnalysis('12345')
    await api.answerOrderRules('开始派送需要什么条件？')
    await api.renameAiConversation('conv/1', '订单分析')
    expect(request.mock.calls.map((call: any[]) => call[0])).toEqual([
      { url: '/ai/orders/12345/dispatch-analysis', method: 'get' },
      { url: '/ai/rag/answer', method: 'post', data: { message: '开始派送需要什么条件？' } },
      { url: '/ai/conversations/conv%2F1/title', method: 'put', data: { title: '订单分析' } },
    ])
  })
})
