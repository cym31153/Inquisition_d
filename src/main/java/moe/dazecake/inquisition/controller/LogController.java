package moe.dazecake.inquisition.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import moe.dazecake.inquisition.annotation.Login;
import moe.dazecake.inquisition.entity.LogEntity;
import moe.dazecake.inquisition.mapper.LogMapper;
import moe.dazecake.inquisition.service.impl.LogServiceImpl;
import moe.dazecake.inquisition.util.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;

@Tag(name = "日志接口")
@ResponseBody
@RestController
public class LogController {
    @Resource
    LogMapper logMapper;

    @Resource
    LogServiceImpl logService;

    @Operation(summary = "增加日志")
    @PostMapping("/addLog")
    public Result<String> addLog(@RequestBody LogEntity logEntity, String deviceToken) {
        Result<String> result = new Result<>();

        logService.addLog(logEntity, deviceToken);

        return result;
    }

    @Login
    @Operation(summary = "删除日志")
    @PostMapping("/delLog")
    public Result<String> delLog(Long id) {
        Result<String> result = new Result<>();

        var logEntity = logMapper.selectById(id);

        if (logEntity != null) {
            logEntity.setDelete(1);
            logMapper.updateById(logEntity);
            result.setCode(200)
                    .setMsg("success")
                    .setData(null);
        } else {
            result.setCode(404)
                    .setMsg("Unable to find this log")
                    .setData(null);
        }
        return result;
    }

    @Login
    @Operation(summary = "查询日志")
    @GetMapping("/showLog")
    public Result<ArrayList<LogEntity>> showLog(Long current, Long size) {
        Result<ArrayList<LogEntity>> result = new Result<>();
        result.setData(new ArrayList<>());

        //降序分页查找
        var data = logMapper.selectPage(new Page<>(current, size), Wrappers.<LogEntity>lambdaQuery()
                .orderByDesc(LogEntity::getId));
        result.setCode(200)
                .setMsg("success")
                .getData()
                .addAll(data.getRecords());

        return result;
    }

    @Login
    @Operation(summary = "搜索日志")
    @GetMapping("/searchLog")
    public Result<ArrayList<LogEntity>> searchLog(String key, Long current, Long size) {
        Result<ArrayList<LogEntity>> result = new Result<>();
        result.setData(new ArrayList<>());

        //模糊搜索
        var data = logMapper.selectPage(new Page<>(current, size), Wrappers.<LogEntity>lambdaQuery()
                .like(LogEntity::getTitle, key)
                .or()
                .like(LogEntity::getDetail, key)
                .or()
                .like(LogEntity::getName, key)
                .or()
                .like(LogEntity::getAccount, key)
                .orderByDesc(LogEntity::getId)
        );
        result.setCode(200)
                .setMsg("success")
                .getData()
                .addAll(data.getRecords());

        return result;
    }

    @Login
    @Operation(summary = "精确查询账号日志")
    @GetMapping("/searchLogByAccount")
    public Result<ArrayList<LogEntity>> searchLogByAccount(String account, Long current, Long size) {
        Result<ArrayList<LogEntity>> result = new Result<>();
        result.setData(new ArrayList<>());

        //精确搜索
        var data = logMapper.selectPage(new Page<>(current, size), Wrappers.<LogEntity>lambdaQuery()
                .eq(LogEntity::getAccount, account)
                .orderByDesc(LogEntity::getId)
        );
        result.setCode(200)
                .setMsg("success")
                .getData()
                .addAll(data.getRecords());

        return result;
    }
}
