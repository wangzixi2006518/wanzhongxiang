<template>
  <el-drawer title="订单规则知识库" :visible="visible" size="600px" direction="rtl" @close="$emit('update:visible', false)">
    <div class="rules-panel">
      <el-alert title="规则问答依据服务端当前规则文档，引用资料可展开核对原文。" type="info" :closable="false" show-icon />
      <p>万众享订单处理规则说明</p>
      <p class="rules-note">问答使用当前生效资料，引用原文可展开核对。</p>
      <el-button :loading="preparing" :disabled="asking" icon="el-icon-refresh" @click="prepareRules">检查资料</el-button>
      <span v-if="rulesReady" class="rules-note">规则资料可用</span>
      <el-divider />
      <el-input v-model="question" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="asking || preparing" placeholder="例如：开始派送需要满足什么条件？" />
      <div class="rules-actions">
        <el-button type="text" :disabled="asking || preparing" @click="question = '开始派送需要满足什么条件？'">填入示例</el-button>
        <el-button type="primary" :loading="asking" :disabled="preparing || !question.trim()" @click="ask">查询规则</el-button>
      </div>
      <p v-if="asking" class="rules-note">{{ phase }}</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <section v-if="result">
        <h4>规则回答</h4>
        <div class="rules-answer">{{ result.answer }}</div>
        <RagSources :sources="result.sources || []" />
      </section>
    </div>
  </el-drawer>
</template>

<script lang="ts">
import { Component, Prop, Vue, Watch } from 'vue-property-decorator'
import { RagAnswer, ensureRuleDocumentsReady, answerOrderRules, resultData, aiErrorMessage } from '@/api/ai'
import RagSources from '@/components/RagSources/index.vue'

@Component({ name: 'RagKnowledge', components: { RagSources } })
export default class extends Vue {
  @Prop({ default: false }) visible!: boolean
  private question = ''
  private rulesReady = false
  private preparing = false
  private asking = false
  private phase = ''
  private error = ''
  private result: RagAnswer | null = null
  private requestSerial = 0

  @Watch('visible')
  onVisible(value: boolean) {
    if (!value) { this.requestSerial++; this.asking = false; this.preparing = false }
  }
  beforeDestroy() { this.requestSerial++ }

  private async prepareRules() {
    if (this.preparing || this.asking) return
    const serial = ++this.requestSerial
    this.preparing = true
    this.error = ''
    this.result = null
    this.rulesReady = false
    try {
      await ensureRuleDocumentsReady()
      if (serial === this.requestSerial) this.rulesReady = true
    } catch (error) {
      if (serial === this.requestSerial) this.error = aiErrorMessage(error, '规则资料检查失败，请重试')
    } finally { if (serial === this.requestSerial) this.preparing = false }
  }

  private async ask() {
    const question = this.question.trim()
    if (!question || this.asking || this.preparing) return
    const serial = ++this.requestSerial
    this.asking = true
    this.error = ''
    this.result = null
    this.phase = '正在检查规则资料…'
    this.rulesReady = false
    try {
      await ensureRuleDocumentsReady()
      if (serial !== this.requestSerial) return
      this.rulesReady = true
      this.phase = '正在检索规则并生成回答…'
      const result = resultData(await answerOrderRules(question))
      if (serial !== this.requestSerial) return
      if (!result || !result.answer) throw new Error('规则问答返回了无效结果')
      this.result = result
    } catch (error) {
      if (serial === this.requestSerial) this.error = aiErrorMessage(error, '规则问答失败，请重试')
    } finally { if (serial === this.requestSerial) this.asking = false }
  }
}
</script>

<style scoped>
.rules-panel { height: calc(100vh - 100px); overflow-y: auto; padding: 0 24px 24px; }
.rules-note { color: #64748b; font-size: 12px; line-height: 1.8; }
.rules-actions { display: flex; justify-content: space-between; margin: 12px 0; }
.rules-answer { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
</style>
