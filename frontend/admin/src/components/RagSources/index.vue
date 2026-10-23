<template>
  <section class="rag-sources">
    <h4>引用资料（{{ sources.length }}）</h4>
    <el-collapse v-if="sources.length">
      <el-collapse-item v-for="source in sources" :key="source.chunkId" :name="source.chunkId">
        <template slot="title">{{ source.documentTitle }} · {{ source.chunkId }}</template>
        <p class="source-meta">文档：{{ source.documentId }} · 版本：{{ source.version }}</p>
        <div class="source-text">{{ source.text }}</div>
      </el-collapse-item>
    </el-collapse>
    <p v-else class="source-meta">本次回答没有引用资料。</p>
  </section>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import { RagSource } from '@/api/ai'

@Component({ name: 'RagSources' })
export default class extends Vue {
  @Prop({ default: () => [] }) sources!: RagSource[]
}
</script>

<style scoped>
.rag-sources { margin-top: 20px; }
.source-meta { color: #64748b; font-size: 12px; overflow-wrap: anywhere; }
.source-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
</style>
