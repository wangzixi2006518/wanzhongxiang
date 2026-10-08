package com.wanzhongxiang.rag;


import lombok.Data;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuleRetrievalContext {
    // 普通的请求记录类

    public static final String CONTEXT_KEY = "ruleRetrievalContext";

    private boolean searched;
    private List<Document> documents = new ArrayList<>();

    public void recordSearch(List<Document> candidates){
        // 只要调用了这个方法，就说明执行过检索，空候选也记录“执行过检索”
        searched = true;

        // 候选非空时，追加到 documents
        if(candidates != null){
            documents.addAll(candidates);
        }
    }

}
