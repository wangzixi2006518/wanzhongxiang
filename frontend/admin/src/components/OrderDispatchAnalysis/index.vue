<template>
  <el-dialog title="AI 派送分析" :visible="visible" width="720px" append-to-body @close="close">
    <p>订单号：{{ orderNumber || '—' }} · 订单主键：{{ orderId }}</p>
    <el-alert title="分析反映订单读取时刻的状态；实际派送仍需业务校验。" type="info" :closable="false" show-icon />
    <div v-if="loading" class="analysis-loading"><i class="el-icon-loading" /> {{ phase }}</div>
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon class="analysis-error" />
    <section v-else-if="result" class="analysis-result">
      <div class="analysis-facts">
        <span>查询状态：{{ statusName }}（{{ result.orderStatus }}）</span>
        <span>事实读取时间：{{ result.queriedAt }}</span>
      </div>
      <h4>分析结果</h4>
      <div class="analysis-answer">{{ result.answer }}</div>
      <RagSources :sources="result.sources || []" />
    </section>
    <span slot="footer">
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="analyze()">{{ error ? '重试' : '重新分析' }}</el-button>
    </span>
  </el-dialog>
</template>

<script lang="ts">
import { Component, Prop, Vue, Watch } from 'vue-property-decorator'
import { OrderDispatchAnalysis, ensureRuleDocumentsReady, getOrderDispatchAnalysis, resultData, aiErrorMessage } from '@/api/ai'
import RagSources from '@/components/RagSources/index.vue'

@Component({ name: 'OrderDispatchAnalysis', components: { RagSources } })
export default class extends Vue {
  @Prop({ default: false }) visible!: boolean
  @Prop({ default: '' }) orderId!: number | string
  @Prop({ default: '' }) orderNumber!: string
  private loading = false
  private phase = ''
  private error = ''
  private result: OrderDispatchAnalysis | null = null
  private requestSerial = 0

  get statusName() {
    const names = { 1: '待付款', 2: '待接单', 3: '已接单', 4: '派送中', 5: '已完成', 6: '已取消' }
    return this.result ? names[this.result.orderStatus] || '未知状态' : ''
  }

  @Watch('visible', { immediate: true })
  onVisible(value: boolean) {
    if (value) this.analyze()
    else { this.requestSerial++; this.loading = false }
  }

  @Watch('orderId')
  onOrderChanged() {
    if (this.visible) { this.requestSerial++; this.loading = false; this.analyze() }
  }

  beforeDestroy() { this.requestSerial++ }

  private close() {
    this.requestSerial++
    this.loading = false
    this.$emit('update:visible', false)
  }

  private async analyze() {
    if (this.loading || !this.visible) return
    this.result = null
    this.error = ''
    if (!/^[1-9]\d*$/.test(String(this.orderId))) { this.error = '请选择有效的订单'; return }
    const serial = ++this.requestSerial
    const id = this.orderId
    this.loading = true
    this.phase = '正在检查规则资料…'
    try {
      await ensureRuleDocumentsReady()
      if (serial !== this.requestSerial || !this.visible) return
      this.phase = '正在读取订单并生成分析…'
      const result = resultData(await getOrderDispatchAnalysis(id))
      if (serial !== this.requestSerial || !this.visible) return
      if (!result || String(result.orderId) !== String(id) || !result.answer) throw new Error('订单分析返回了无效结果')
      this.result = result
    } catch (error) {
      if (serial === this.requestSerial && this.visible) {
        this.error = aiErrorMessage(error, '分析失败，请重试')
      }
    } finally {
      if (serial === this.requestSerial) this.loading = false
    }
  }
}
</script>

<style scoped>
.analysis-loading { padding: 40px 0; text-align: center; color: #64748b; }
.analysis-error, .analysis-result { margin-top: 20px; }
.analysis-facts { display: flex; flex-wrap: wrap; gap: 12px; color: #64748b; }
.analysis-answer { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
</style>
