<template>
  <div class="ai-assistant-page">
    <header class="assistant-header">
      <div>
        <div class="title-row">
          <span class="title-icon"><i class="el-icon-magic-stick" /></span>
          <h1>AI 运营助手</h1>
          <el-tag type="success" size="small">真实接口</el-tag>
        </div>
        <p>查询经营数据、连续追问，或展开订单规则与引用资料</p>
      </div>
      <div class="header-actions">
        <el-button icon="el-icon-connection" :loading="healthLoading" @click="checkHealth">检查服务</el-button>
        <el-button icon="el-icon-chat-dot-round" @click="singleVisible = true">单次问答</el-button>
        <el-button icon="el-icon-document" @click="knowledgeVisible = true">规则知识库</el-button>
        <el-button type="primary" icon="el-icon-plus" :disabled="busy" @click="createConversation">新建会话</el-button>
      </div>
    </header>

    <main class="assistant-workspace">
      <aside class="conversation-panel">
        <div class="panel-heading">
          <span>历史会话</span>
          <el-button type="text" icon="el-icon-refresh" title="刷新历史会话" :disabled="busy" @click="loadConversations()" />
        </div>
        <div v-if="conversations.length" class="conversation-list">
          <div v-for="item in conversations" :key="item.id" class="conversation-item" :class="{ active: item.id === activeConversationId }">
            <button class="conversation-main" :disabled="busy" @click="selectConversation(item.id)">
              <i class="el-icon-chat-line-round" />
              <span><strong :title="item.title">{{ item.title }}</strong><small>{{ formatTime(item.updatedAt) }}</small></span>
            </button>
            <el-button class="conversation-edit" type="text" icon="el-icon-edit" :disabled="busy" title="重命名会话" @click="openRename(item)" />
            <el-button class="conversation-delete" type="text" icon="el-icon-delete" :disabled="busy" title="删除会话" @click="removeConversation(item.id)" />
          </div>
        </div>
        <div v-else class="conversation-empty"><i class="el-icon-chat-dot-round" /><p>暂无历史会话</p></div>
        <div class="learning-tip">
          <i class="el-icon-reading" />
          <div><strong>连续追问</strong><p>同一会话会恢复已完成的历史轮次。切换或新建会话后，上下文分别保存。</p></div>
        </div>
      </aside>

      <section class="chat-panel">
        <div ref="messageContainer" class="message-container">
          <div v-if="messages.length === 1 && messages[0].id.indexOf('welcome') === 0" class="welcome-block">
            <span class="welcome-icon"><i class="el-icon-data-analysis" /></span>
            <h2>今天想查询什么？</h2>
            <p>可以直接查询经营数据或订单规则；规则回答可展开引用原文</p>
            <div class="suggestion-grid">
              <button v-for="suggestion in suggestions" :key="suggestion.title" :disabled="busy" @click="useSuggestion(suggestion.question)">
                <i :class="suggestion.icon" />
                <span><strong>{{ suggestion.title }}</strong><small>{{ suggestion.description }}</small></span>
              </button>
            </div>
          </div>
          <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
            <div class="avatar"><i :class="message.role === 'assistant' ? 'el-icon-magic-stick' : 'el-icon-user'" /></div>
            <div class="message-content">
              <div class="message-meta">
                <strong>{{ message.role === 'assistant' ? '运营助手' : '我' }}</strong><span>{{ formatTime(message.createdAt) }}</span>
                <el-tag v-if="message.status === 'CANCELLED'" type="warning" size="mini">已停止 · 回答不完整</el-tag>
                <el-tag v-else-if="message.status === 'FAILED'" type="danger" size="mini">生成失败</el-tag>
                <el-tag v-else-if="message.status === 'GENERATING'" size="mini">生成中</el-tag>
              </div>
              <div class="message-bubble">
                <span v-if="message.content">{{ message.content }}</span>
                <span v-else-if="message.status === 'GENERATING'" class="typing-indicator"><i /><i /><i /></span>
                <span v-else>{{ message.status === 'CANCELLED' ? '生成已停止，尚无回答内容。' : '未生成回答内容。' }}</span>
              </div>
              <RagSources v-if="message.status === 'COMPLETED' && message.sources && message.sources.length" :sources="message.sources" />
            </div>
          </div>
        </div>
        <div class="composer">
          <div class="mode-row"><span>经营查询、规则问答与连续对话<span v-if="rulesReady"> · 规则资料可用</span></span><el-button type="text" :disabled="busy" :loading="rulePreparing" @click="prepareRules">检查资料</el-button></div>
          <div class="input-row">
            <el-input v-model="inputMessage" type="textarea" :rows="2" :disabled="busy" maxlength="500" show-word-limit placeholder="例如：昨天已完成订单的营业额是多少？" @keydown.native="handleKeydown" />
            <el-button v-if="!streaming" type="primary" class="send-button" :disabled="busy || !inputMessage.trim()" @click="sendMessage"><i class="el-icon-position" /></el-button>
            <el-button v-else type="danger" class="send-button" @click="stopGeneration"><i class="el-icon-video-pause" /></el-button>
          </div>
          <p class="composer-tip">规则问答使用当前生效资料。回答完成后可展开引用资料，结合业务数据与原文复核。</p>
        </div>
      </section>

      <aside class="capability-panel">
        <div class="panel-heading">已接入能力</div>
        <div v-for="item in capabilities" :key="item.name" class="capability-item">
          <span class="capability-icon ready"><i :class="item.icon" /></span>
          <div><strong>{{ item.name }}</strong><p>{{ item.description }}</p></div>
          <el-tag type="success" size="mini">已接通</el-tag>
        </div>
        <div class="api-card">
          <span><i class="el-icon-truck" /> 订单派送分析</span>
          <p>在订单管理或工作台订单列表中，点击“AI 派送分析”查看订单事实、结论和引用。</p>
          <el-button type="text" @click="$router.push('/order')">打开订单管理</el-button>
        </div>
      </aside>
    </main>

    <RagKnowledge :visible.sync="knowledgeVisible" />
    <el-dialog title="重命名会话" :visible.sync="renameVisible" width="420px" :close-on-click-modal="!renaming" :close-on-press-escape="!renaming" :show-close="!renaming">
      <el-input v-model="renameTitle" maxlength="60" show-word-limit placeholder="输入1到60字的会话名称" :disabled="renaming" @keyup.enter.native="confirmRename" />
      <p v-if="renameError" class="dialog-error">{{ renameError }}</p>
      <span slot="footer"><el-button :disabled="renaming" @click="renameVisible = false">取消</el-button><el-button type="primary" :loading="renaming" :disabled="!renameTitle.trim()" @click="confirmRename">保存</el-button></span>
    </el-dialog>
    <el-dialog title="AI 服务检查" :visible.sync="healthVisible" width="460px">
      <p v-if="healthLoading"><i class="el-icon-loading" /> 正在请求模型检查服务…</p>
      <el-alert v-else-if="healthError" :title="healthError" type="error" :closable="false" />
      <div v-else-if="health"><el-tag :type="health.status === 'UP' ? 'success' : 'danger'">{{ health.status === 'UP' ? '模型服务可用' : '模型服务不可用' }}</el-tag><p>服务提供方：{{ health.provider }}</p></div>
      <p class="composer-tip">此检查显示模型服务与规则资料状态；规则资料也可单独检查。</p>
    </el-dialog>
    <el-dialog title="单次问答" :visible.sync="singleVisible" width="620px">
      <el-alert title="单次问答独立返回完整回答，不保存会话，不使用会话记忆或经营工具。连续追问请使用主聊天窗口。" type="info" :closable="false" />
      <el-input v-model="singleQuestion" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="singleLoading" placeholder="输入一个普通问题" class="single-question" />
      <el-alert v-if="singleError" :title="singleError" type="error" :closable="false" />
      <div v-if="singleAnswer" class="single-answer">{{ singleAnswer }}</div>
      <span slot="footer"><el-button @click="singleVisible = false">关闭</el-button><el-button type="primary" :loading="singleLoading" :disabled="!singleQuestion.trim()" @click="askSingle">发送</el-button></span>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import moment from 'moment'
import { AiConversation, AiMessage, AiHealth, RagSource, ensureRuleDocumentsReady, createAiConversation, deleteAiConversation, getAiConversations, getAiMessages, renameAiConversation, getAiHealth, askAiOnce, resultData, aiErrorMessage } from '@/api/ai'
import { AiStreamEvent, streamAiChat } from '@/utils/aiStream'
import RagKnowledge from '@/components/RagKnowledge/index.vue'
import RagSources from '@/components/RagSources/index.vue'

interface ViewMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  status?: AiMessage['status']
  sources?: RagSource[] | null
}

@Component({ name: 'AiAssistant', components: { RagKnowledge, RagSources } })
export default class extends Vue {
  private streaming = false
  private loadingHistory = false
  private managingConversation = false
  private knowledgeVisible = false
  private rulePreparing = false
  private rulesReady = false
  private inputMessage = ''
  private activeConversationId = ''
  private activeRequest: { abort: () => void; promise: Promise<void> } | null = null
  private conversations: AiConversation[] = []
  private messages: ViewMessage[] = []
  private navigationSerial = 0
  private disposed = false
  private renameVisible = false
  private renameId = ''
  private renameTitle = ''
  private renameError = ''
  private renaming = false
  private healthVisible = false
  private healthLoading = false
  private healthError = ''
  private health: AiHealth | null = null
  private singleVisible = false
  private singleQuestion = ''
  private singleAnswer = ''
  private singleError = ''
  private singleLoading = false
  private suggestions = [
    { title: '昨天营业额', description: '查询已完成订单的金额和笔数', question: '昨天已完成订单的营业额是多少？请注明日期和订单笔数。', icon: 'el-icon-data-analysis' },
    { title: '指定日期', description: '查询昨天之前的完整日期', question: `请查询 ${moment().subtract(2, 'days').format('YYYY-MM-DD')} 的已完成订单营业额和笔数。`, icon: 'el-icon-date' },
    { title: '连续追问', description: '在同一会话里使用上一轮内容', question: '请将你上一轮给出的结论简要复述，不要编造新的经营数据。', icon: 'el-icon-chat-line-round' },
    { title: '订单规则', description: '检索规则并核对引用原文', question: '订单开始派送前需要满足什么条件？', icon: 'el-icon-collection' },
  ]
  private capabilities = [
    { name: '会话管理', description: '历史恢复、重命名与删除', icon: 'el-icon-chat-dot-round' },
    { name: 'SSE 流式输出', description: '逐段回答、停止生成与终态显示', icon: 'el-icon-sort' },
    { name: 'Tool Calling', description: '昨天与指定过去日期的营业额查询', icon: 'el-icon-set-up' },
    { name: 'RAG 知识库', description: '规则问答、生效资料与引用原文', icon: 'el-icon-collection' },
    { name: 'Chat Memory', description: '按会话恢复最近已完成的轮次', icon: 'el-icon-time' },
  ]

  get busy() { return this.streaming || this.loadingHistory || this.managingConversation || this.renaming || this.rulePreparing }
  created() { this.resetMessages(); this.loadConversations() }
  beforeDestroy() { this.disposed = true; this.navigationSerial++; if (this.activeRequest) this.activeRequest.abort() }

  private resetMessages() {
    this.messages = [{ id: `welcome-${Date.now()}`, role: 'assistant', content: '你好，可以查询昨天或指定过去日期的营业额，也可以在同一会话中连续追问。规则问答使用当前生效资料，回答后可以查看引用原文。', createdAt: new Date().toISOString() }]
  }

  private async loadConversations(listOnly = false) {
    if (this.busy && !listOnly) return
    const serial = ++this.navigationSerial
    if (!listOnly) this.loadingHistory = true
    try {
      const conversations = resultData(await getAiConversations()) as AiConversation[]
      if (this.disposed || serial !== this.navigationSerial) return
      this.conversations = conversations || []
      if (listOnly) return
      const id = this.conversations.some((item) => item.id === this.activeConversationId) ? this.activeConversationId : this.conversations.length ? this.conversations[0].id : ''
      if (id) await this.readMessages(id, serial)
      else { this.activeConversationId = ''; this.resetMessages() }
    } catch (error) {
      if (!this.disposed && serial === this.navigationSerial) this.$message.error(aiErrorMessage(error, '会话列表加载失败'))
    } finally { if (serial === this.navigationSerial) this.loadingHistory = false }
  }

  private async createConversation() {
    if (this.busy) return
    this.managingConversation = true
    this.navigationSerial++
    try {
      const conversation = resultData(await createAiConversation('新会话')) as AiConversation
      if (this.disposed) return
      this.conversations.unshift(conversation)
      this.activeConversationId = conversation.id
      this.resetMessages()
    } catch (error) { if (!this.disposed) this.$message.error(aiErrorMessage(error, '创建会话失败')) }
    finally { this.managingConversation = false }
  }

  private async selectConversation(id: string) {
    if (this.busy) return
    const serial = ++this.navigationSerial
    this.loadingHistory = true
    try { await this.readMessages(id, serial) }
    catch (error) { if (!this.disposed && serial === this.navigationSerial) this.$message.error(aiErrorMessage(error, '会话消息加载失败')) }
    finally { if (serial === this.navigationSerial) this.loadingHistory = false }
  }

  private async readMessages(id: string, serial: number) {
    const records = resultData(await getAiMessages(id)) as AiMessage[]
    if (this.disposed || serial !== this.navigationSerial) return
    this.activeConversationId = id
    if (records && records.length) this.messages = records.map((record) => ({ id: record.id, role: record.role === 'USER' ? 'user' : 'assistant', content: record.content || '', status: record.status, createdAt: record.createdAt }))
    else this.resetMessages()
    this.scrollToBottom()
  }

  private openRename(item: AiConversation) {
    if (this.busy) return
    this.renameId = item.id
    this.renameTitle = item.title
    this.renameError = ''
    this.renameVisible = true
  }

  private async confirmRename() {
    if (this.renaming || !this.renameVisible) return
    const title = this.renameTitle.trim()
    if (!title || title.length > 60) { this.renameError = '标题需为1到60字'; return }
    this.renaming = true
    this.renameError = ''
    try {
      const renamed = resultData(await renameAiConversation(this.renameId, title))
      if (this.disposed) return
      const index = this.conversations.findIndex((item) => item.id === renamed.id)
      if (index !== -1) this.$set(this.conversations, index, renamed)
      this.renameVisible = false
      this.$message.success('会话已重命名')
    } catch (error) { this.renameError = aiErrorMessage(error, '重命名失败，请重试') }
    finally { this.renaming = false }
  }

  private async removeConversation(id: string) {
    if (this.busy) return
    this.managingConversation = true
    try {
      try { await this.$confirm('删除此会话及其全部消息？', '删除会话', { type: 'warning' }) } catch (_) { return }
      resultData(await deleteAiConversation(id))
      if (this.disposed) return
      this.conversations = this.conversations.filter((item) => item.id !== id)
      if (this.activeConversationId === id) {
        this.activeConversationId = ''
        this.resetMessages()
        if (this.conversations.length) await this.readMessages(this.conversations[0].id, ++this.navigationSerial)
      }
    } catch (error) { if (!this.disposed) this.$message.error(aiErrorMessage(error, '删除会话失败')) }
    finally { this.managingConversation = false }
  }

  private useSuggestion(question: string) {
    if (!question) { this.knowledgeVisible = true; return }
    this.inputMessage = question
    this.sendMessage()
  }
  private handleKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); this.sendMessage() }
  }

  private async sendMessage() {
    const text = this.inputMessage.trim()
    if (!text || this.busy) return
    const assistant: ViewMessage = { id: `assistant-${Date.now()}`, role: 'assistant', content: '', createdAt: new Date().toISOString(), status: 'GENERATING', sources: null }
    this.messages.push({ id: `user-${Date.now()}`, role: 'user', content: text, createdAt: assistant.createdAt }, assistant)
    this.inputMessage = ''
    this.streaming = true
    this.scrollToBottom()
    try {
      this.activeRequest = streamAiChat({ conversationId: this.activeConversationId, message: text, onEvent: (event) => this.handleStreamEvent(event, assistant) })
      await this.activeRequest.promise
    } catch (error) {
      if (error && (error as any).name === 'AbortError') { if (assistant.status === 'GENERATING') assistant.status = 'CANCELLED' }
      else { assistant.status = 'FAILED'; if (!this.disposed) this.$message.error(aiErrorMessage(error, 'AI 请求失败')) }
    } finally {
      if (assistant.status === 'GENERATING') assistant.status = 'FAILED'
      this.streaming = false
      this.activeRequest = null
      if (!this.disposed) { await this.loadConversations(true); this.scrollToBottom() }
    }
  }

  private handleStreamEvent(event: AiStreamEvent, message: ViewMessage) {
    if (this.disposed) return
    if (event.type === 'meta') {
      if (event.conversationId) this.activeConversationId = event.conversationId
      if (event.messageId) message.id = event.messageId
    } else if (event.type === 'delta') message.content += event.content || ''
    else if (event.type === 'sources') {
      if (event.messageId === message.id && Array.isArray(event.sources)) message.sources = event.sources
    } else if (event.type === 'done') message.status = 'COMPLETED'
    else if (event.type === 'error') { message.status = 'FAILED'; message.sources = null; this.$message.error(event.errorMessage || '生成失败') }
    this.scrollToBottom()
  }
  private stopGeneration() { if (this.activeRequest) this.activeRequest.abort() }
  private scrollToBottom() {
    this.$nextTick(() => { const container = this.$refs.messageContainer as HTMLElement; if (container) container.scrollTop = container.scrollHeight })
  }

  private async prepareRules() {
    if (this.busy) return
    this.rulePreparing = true
    try {
      await ensureRuleDocumentsReady()
      if (!this.disposed) { this.rulesReady = true; this.$message.success('规则资料可用') }
    } catch (error) {
      if (!this.disposed) { this.rulesReady = false; this.$message.error(aiErrorMessage(error, '规则资料检查失败')) }
    } finally { this.rulePreparing = false }
  }

  private async checkHealth() {
    if (this.healthLoading) return
    this.healthVisible = true
    this.healthLoading = true
    this.health = null
    this.healthError = ''
    try { this.health = resultData(await getAiHealth()) }
    catch (error) { this.healthError = aiErrorMessage(error, '服务检查失败') }
    finally { this.healthLoading = false }
  }
  private async askSingle() {
    if (this.singleLoading || !this.singleQuestion.trim()) return
    this.singleLoading = true
    this.singleAnswer = ''
    this.singleError = ''
    try {
      const result = resultData(await askAiOnce(this.singleQuestion.trim()))
      if (!result || !result.content || !result.content.trim()) throw new Error('未得到有效模型文本')
      this.singleAnswer = result.content
    } catch (error) { this.singleError = aiErrorMessage(error, '单次问答失败') }
    finally { this.singleLoading = false }
  }
  private formatTime(value: string) { return value ? moment(value).format('MM-DD HH:mm') : '' }
}
</script>

<style lang="scss" scoped>
.conversation-edit { align-self: center; padding: 8px; }
.dialog-error { color: #f56c6c; }
.single-question { margin: 16px 0; }
.single-answer { white-space: pre-wrap; line-height: 1.8; overflow-wrap: anywhere; }
.header-actions { flex-wrap: wrap; justify-content: flex-end; }
.ai-assistant-page {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  padding: 24px;
  color: #0f172a;
  background:
    radial-gradient(circle at 85% 5%, rgba(20, 184, 166, 0.09), transparent 26%),
    #f1f5f9;
}

.assistant-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;

  h1 {
    margin: 0;
    font-size: 24px;
    line-height: 34px;
  }
}

.title-icon,
.welcome-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  box-shadow: 0 8px 20px rgba(20, 184, 166, 0.22);
}

.title-icon {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  font-size: 17px;
}

.assistant-header p {
  margin: 5px 0 0 46px;
  color: #64748b;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.assistant-workspace {
  display: grid;
  grid-template-columns: 230px minmax(600px, 1fr) 280px;
  min-height: 0;
  flex: 1;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.06);
}

.conversation-panel,
.capability-panel {
  padding: 18px;
  background: #f8fafc;
}

.conversation-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #e2e8f0;
}

.capability-panel {
  min-height: 0;
  overflow-y: auto;
}

.capability-panel {
  border-left: 1px solid #e2e8f0;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 12px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 6px;
  color: #475569;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;

  &:hover,
  &.active {
    color: #0f766e;
    background: #ecfdf5;
    border-color: #ccfbf1;
  }

  .conversation-main {
    display: flex;
    min-width: 0;
    flex: 1;
    gap: 9px;
    padding: 11px;
    text-align: left;
    color: inherit;
    background: transparent;
    border: 0;
    cursor: pointer;
  }

  .conversation-main:disabled {
    cursor: not-allowed;
  }

  .conversation-main > i {
    margin-top: 2px;
  }

  .conversation-delete {
    align-self: center;
    padding: 8px;
    margin-right: 3px;
  }

  span {
    display: flex;
    min-width: 0;
    flex: 1;
    flex-direction: column;
  }

  strong {
    overflow: hidden;
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    margin-top: 5px;
    color: #94a3b8;
  }
}

.conversation-empty,
.document-empty {
  padding: 35px 10px;
  text-align: center;
  color: #94a3b8;

  i {
    font-size: 32px;
  }

  p {
    margin: 10px 0 4px;
  }
}

.learning-tip {
  display: flex;
  gap: 9px;
  padding: 12px;
  margin-top: 14px;
  color: #475569;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;

  > i {
    margin-top: 2px;
    color: #f59e0b;
  }

  strong {
    font-size: 12px;
  }

  p {
    margin: 4px 0 0;
    font-size: 11px;
    line-height: 17px;
  }
}

.chat-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.message-container {
  flex: 1;
  min-height: 0;
  padding: 28px 34px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.welcome-block {
  max-width: 680px;
  margin: 25px auto 35px;
  text-align: center;

  .welcome-icon {
    width: 52px;
    height: 52px;
    border-radius: 16px;
    font-size: 23px;
  }

  h2 {
    margin: 15px 0 7px;
    font-size: 22px;
  }

  > p {
    margin: 0 0 20px;
    color: #64748b;
  }
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  button {
    display: flex;
    align-items: center;
    gap: 11px;
    padding: 13px;
    text-align: left;
    color: #334155;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 11px;
    cursor: pointer;

    &:hover {
      color: #0f766e;
      background: #f0fdfa;
      border-color: #99f6e4;
    }

    > i {
      color: #14b8a6;
      font-size: 20px;
    }

    span {
      display: flex;
      flex-direction: column;
    }

    strong {
      font-size: 13px;
    }

    small {
      margin-top: 4px;
      color: #94a3b8;
    }
  }
}

.message-row {
  display: flex;
  gap: 12px;
  max-width: 820px;
  margin: 0 auto 24px;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;
    }

    .message-bubble {
      color: #fff;
      background: #0f766e;
      border-color: #0f766e;
      border-radius: 14px 4px 14px 14px;
    }
  }
}

.avatar {
  display: flex;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  color: #0f766e;
  background: #ccfbf1;
  border-radius: 10px;
}

.user .avatar {
  color: #475569;
  background: #e2e8f0;
}

.message-content {
  display: flex;
  max-width: calc(100% - 50px);
  flex-direction: column;
  align-items: flex-start;
}

.message-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 11px;

  strong {
    color: #334155;
    font-size: 12px;
  }
}

.message-bubble {
  min-width: 54px;
  padding: 12px 15px;
  color: #334155;
  font-size: 14px;
  line-height: 23px;
  white-space: pre-wrap;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px 14px 14px;
}

.typing-indicator {
  display: inline-flex;
  gap: 4px;

  i {
    width: 6px;
    height: 6px;
    background: #94a3b8;
    border-radius: 50%;
    animation: pulse 1.2s infinite ease-in-out;

    &:nth-child(2) { animation-delay: 0.15s; }
    &:nth-child(3) { animation-delay: 0.3s; }
  }
}

.tool-trace {
  width: 100%;
  margin-bottom: 8px;
}

.tool-card {
  display: flex;
  min-width: 380px;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  margin-bottom: 6px;
  background: #f0fdfa;
  border: 1px solid #ccfbf1;
  border-radius: 9px;

  > div {
    flex: 1;
  }

  strong {
    color: #0f766e;
    font-size: 12px;
  }

  p {
    margin: 2px 0 0;
    color: #64748b;
    font-size: 11px;
  }

  small {
    color: #94a3b8;
  }
}

.tool-status {
  color: #14b8a6;
}

.source-list {
  width: 100%;
  margin-top: 8px;
}

.source-title {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 11px;
}

.source-item {
  display: flex;
  flex-direction: column;
  padding: 8px 10px;
  margin-bottom: 5px;
  color: #64748b;
  font-size: 11px;
  background: #fffbeb;
  border-left: 3px solid #f59e0b;
  border-radius: 5px;

  strong {
    margin-bottom: 3px;
    color: #92400e;
  }
}

.composer {
  flex-shrink: 0;
  padding: 13px 24px 12px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
}

.mode-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 11px;
}

.input-row {
  display: flex;
  gap: 9px;
  align-items: stretch;
}

.send-button {
  width: 48px;
  padding: 0;
  font-size: 18px;
  border-radius: 9px;
}

.composer-tip {
  margin: 7px 0 0;
  text-align: center;
  color: #94a3b8;
  font-size: 10px;
}

.capability-item {
  display: grid;
  grid-template-columns: 34px 1fr auto;
  gap: 9px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #e2e8f0;

  strong {
    color: #334155;
    font-size: 12px;
  }

  p {
    margin: 3px 0 0;
    color: #94a3b8;
    font-size: 10px;
    line-height: 15px;
  }
}

.capability-icon {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  color: #64748b;
  background: #e2e8f0;
  border-radius: 9px;

  &.ready {
    color: #0f766e;
    background: #ccfbf1;
  }
}

.api-card {
  padding: 13px;
  margin-top: 18px;
  background: #0f172a;
  border-radius: 11px;

  span {
    display: block;
    margin-bottom: 9px;
    color: #cbd5e1;
    font-size: 11px;
  }

  code {
    display: block;
    padding: 8px;
    color: #5eead4;
    font-size: 10px;
    background: #1e293b;
    border-radius: 6px;
  }

  p {
    margin: 8px 0 0;
    color: #94a3b8;
    font-size: 10px;
    line-height: 16px;
  }
}

.knowledge-drawer {
  padding: 0 24px 24px;
}

.knowledge-actions {
  display: flex;
  gap: 10px;
  margin: 18px 0;
}

.document-item {
  display: grid;
  grid-template-columns: 32px 1fr auto 28px;
  gap: 10px;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;

  > i {
    color: #14b8a6;
    font-size: 22px;
  }

  > div {
    display: flex;
    flex-direction: column;
  }

  strong {
    color: #334155;
    font-size: 13px;
  }

  span {
    margin-top: 4px;
    color: #94a3b8;
    font-size: 11px;
  }
}

@keyframes pulse {
  0%, 80%, 100% { opacity: 0.35; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

@media (max-width: 1500px) {
  .assistant-workspace {
    grid-template-columns: 210px minmax(560px, 1fr) 250px;
  }

  .capability-panel,
  .conversation-panel {
    padding: 14px;
  }
}
</style>
