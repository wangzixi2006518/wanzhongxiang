package com.wanzhongxiang.controller.admin;

import com.wanzhongxiang.result.Result;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlbeans.impl.xb.xsdschema.Public;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@Api(tags = "Ai 相关接口")
@Slf4j
public class AiController {

    @PostMapping("/conversations")
    public Result<Integer> conversations(@RequestBody String string){

    }



}
