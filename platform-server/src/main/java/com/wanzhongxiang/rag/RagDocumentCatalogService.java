package com.wanzhongxiang.rag;

import com.wanzhongxiang.exception.RagVersionStateException;
import com.wanzhongxiang.mapper.RagDocumentCatalogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RagDocumentCatalogService {

    @Autowired
    private RagDocumentCatalogMapper ragDocumentCatalogMapper;

    // 查询所有处于启用状态的 RAG 文档配置
    public List<RagDocumentSpec> listActiveSpecs(){
        List<RagDocumentSpec> ragDocumentSpecs = ragDocumentCatalogMapper.listActiveSpecs();
        return ragDocumentSpecs;
    }

    // 把待导入的资料版本登记到 MySQL
    @Transactional
    public void registerVersion(RagDocumentSpec spec){
        // 检查是否为空
        if(spec == null
                || spec.getDocumentId() == null || spec.getDocumentId().isBlank()
                || spec.getDocumentTitle() == null || spec.getDocumentTitle().isBlank()
                || spec.getVersion() == null || spec.getVersion().isBlank()
                || spec.getSourceType() == null || spec.getSourceType().isBlank()
                || spec.getSourcePath() == null || spec.getSourcePath().isBlank()){
            throw new IllegalStateException("资料信息不完整");
        }

        // 调用 ensureDocument 传入 spec 确保后续版本记录的外键有对应目录
        ragDocumentCatalogMapper.ensureDocument(spec);

        // 调用 insertPendingVersion 传入 spec 创建待导入版本
        ragDocumentCatalogMapper.insertPendingVersion(spec);
    }

    @Transactional
    // 写入成功后的版本发布
    public void publishVersion(String documentId, String version, int chunkCount){
        // 校验两个字段非空并且 chunkCount > 0
        if(documentId == null || documentId.isBlank() || version == null || version.isBlank() || chunkCount <= 0){
            throw new IllegalArgumentException("版本发布参数不合法");
        }

        //将文档版本标记为 已准备完成，随后校验
        int readyRows = ragDocumentCatalogMapper.markVersionReady(documentId, version, chunkCount);
        if(readyRows != 1){
            throw new RagVersionStateException("版本不存在或不是待导入状态");
        }

        // 将这个版本设置为文档的 当前生效版本，随后校验
        int activeRows = ragDocumentCatalogMapper.activateVersion(documentId, version);
        if(activeRows != 1){
            throw new IllegalStateException("文档目录更新失败");
        }
    }

    public RagDocumentSpec getPendingSpec(String documentId, String version){
        // 根据文档 ID 和版本号，查询一份处于 PENDING（待导入）状态

        // 校验参数不为空
        if (documentId == null || documentId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("资料版本参数不合法");
        }

        // 查询待导入的文档版本
        RagDocumentSpec spec = ragDocumentCatalogMapper.findPendingSpec(documentId, version);

        if(spec == null){
            throw new RagVersionStateException("版本不存在或不是待导入状态");
        }

        // 返回 spec
        return spec;
    }


}
