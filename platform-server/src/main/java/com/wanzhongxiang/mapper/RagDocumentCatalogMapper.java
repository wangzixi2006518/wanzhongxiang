package com.wanzhongxiang.mapper;

import com.wanzhongxiang.rag.RagDocumentSpec;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RagDocumentCatalogMapper {

    @Select("SELECT" +
            "    d.document_id," +
            "    d.document_title," +
            "    d.enabled," +
            "    d.active_version," +
            "    v.id AS version_id," +
            "    v.version," +
            "    v.source_type," +
            "    v.source_path," +
            "    v.status," +
            "    v.chunk_count " +
            "FROM ai_rule_document d " +
            "INNER JOIN ai_rule_document_version v " +
            "    ON d.document_id = v.document_id AND d.active_version = v.version AND d.enabled = 1 AND v.status = 'READY' " +
            "ORDER BY d.document_id ASC;")
    List<RagDocumentSpec> listActiveSpecs();

    // 确保目录存在
    @Insert("insert into ai_rule_document(document_id,document_title,enabled,active_version,create_time,update_time) " +
            "values (#{documentId},#{documentTitle},1,null,now(),now()) ON DUPLICATE KEY UPDATE document_id = document_id")
    int ensureDocument(RagDocumentSpec spec);

    // 登记待导入版本
    @Insert("insert into ai_rule_document_version(document_id,version,source_type,source_path,status,chunk_count,create_time,update_time) " +
            "values (#{documentId},#{version},#{sourceType},#{sourcePath},'PENDING',0,now(),now())")
    int insertPendingVersion(RagDocumentSpec spec);

    @Update("update ai_rule_document_version set status = 'READY',chunk_count = #{chunkCount},update_time = now() " +
            "where document_id = #{documentId} and version = #{version} and status = 'PENDING'")
    int markVersionReady(@Param("documentId") String documentId,
                         @Param("version") String version,
                         @Param("chunkCount") int chunkCount);

    @Update("update ai_rule_document set active_version = #{version},update_time = now() where document_id = #{documentId}")
    int activateVersion(@Param("documentId") String documentId,
                        @Param("version") String version);

    @Select("SELECT" +
            "    d.document_id," +
            "    d.document_title," +
            "    v.version," +
            "    v.source_type," +
            "    v.source_path " +
            "FROM ai_rule_document d " +
            "INNER JOIN ai_rule_document_version v ON d.document_id = v.document_id " +
            "where d.document_id=#{documentId} and v.version=#{version} and v.status='PENDING'")
    RagDocumentSpec findPendingSpec(
            @Param("documentId") String documentId,
            @Param("version") String version);

}
