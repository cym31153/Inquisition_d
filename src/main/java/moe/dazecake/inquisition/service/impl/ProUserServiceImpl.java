package moe.dazecake.inquisition.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import moe.dazecake.inquisition.constant.ResponseCodeConstants;
import moe.dazecake.inquisition.constant.enums.CDKWrapper;
import moe.dazecake.inquisition.mapper.AccountMapper;
import moe.dazecake.inquisition.mapper.LogMapper;
import moe.dazecake.inquisition.mapper.ProUserMapper;
import moe.dazecake.inquisition.mapper.mapstruct.AccountConvert;
import moe.dazecake.inquisition.mapper.mapstruct.ProUserConvert;
import moe.dazecake.inquisition.model.dto.account.AccountDTO;
import moe.dazecake.inquisition.model.dto.cdk.CreateCDKDTO;
import moe.dazecake.inquisition.model.dto.log.LogDTO;
import moe.dazecake.inquisition.model.dto.prouser.CreateProUserDTO;
import moe.dazecake.inquisition.model.dto.prouser.ProUserDTO;
import moe.dazecake.inquisition.model.dto.prouser.ProUserLoginDTO;
import moe.dazecake.inquisition.model.dto.prouser.UpdateProUserPasswordDTO;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.model.entity.LogEntity;
import moe.dazecake.inquisition.model.entity.ProUserEntity;
import moe.dazecake.inquisition.model.entity.TaskDateSet.LockTask;
import moe.dazecake.inquisition.model.vo.account.AccountWithSanVO;
import moe.dazecake.inquisition.model.vo.cdk.CDKListVO;
import moe.dazecake.inquisition.model.vo.prouser.ProUserLoginVO;
import moe.dazecake.inquisition.model.vo.query.PageQueryVO;
import moe.dazecake.inquisition.service.intf.ProUserService;
import moe.dazecake.inquisition.utils.DynamicInfo;
import moe.dazecake.inquisition.utils.Encoder;
import moe.dazecake.inquisition.utils.JWTUtils;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Service
public class ProUserServiceImpl implements ProUserService {

    private static final String salt = "arklightspro";

    @Resource
    private DynamicInfo dynamicInfo;

    @Resource
    private ProUserMapper proUserMapper;

    @Resource
    private AccountMapper accountMapper;

    @Resource
    private LogMapper logMapper;

    @Resource
    private LogServiceImpl logService;

    @Resource
    private AccountServiceImpl accountService;

    @Resource
    private TaskServiceImpl taskService;

    @Resource
    private CDKServiceImpl cdkService;

    @Value("${inquisition.price.daily:1.0}")
    private Double dailyPrice;

    @Value("${inquisition.price.rogue_1:40.0}")
    private Double rogue1Price;

    @Value("${inquisition.price.rogue_2:40.0}")
    private Double rogue2Price;

    @Override
    public void CreateProUser(CreateProUserDTO createProUserDTO) {
        var proUserEntity = ProUserConvert.INSTANCE.toProUserEntity(createProUserDTO);
        proUserEntity.setPassword(Encoder.MD5(proUserEntity.getPassword() + salt));
        proUserMapper.insert(proUserEntity);
    }

    @Override
    public Result<ProUserLoginVO> loginProUser(ProUserLoginDTO proUserLoginDTO) {
        if (proUserLoginDTO.getUsername() == null || proUserLoginDTO.getPassword() == null) {
            return Result.paramError("??????????????????????????????");
        }
        var account = proUserMapper.selectOne(
                Wrappers.<ProUserEntity>lambdaQuery()
                        .eq(ProUserEntity::getUsername, proUserLoginDTO.getUsername())
                        .eq(ProUserEntity::getPassword, Encoder.MD5(proUserLoginDTO.getPassword() + salt))
        );
        if (account != null) {
            return Result.success(new ProUserLoginVO(JWTUtils.generateTokenForProUser(account)), "????????????");
        } else {
            return Result.unauthorized("????????????????????????");
        }
    }

    @Override
    public Result<ProUserDTO> getProUserInfo(Long id) {
        var proUserEntity = proUserMapper.selectById(id);
        if (proUserEntity != null) {
            return Result.success(ProUserConvert.INSTANCE.toProUserDTO(proUserEntity), "????????????");
        } else {
            return Result.notFound("???????????????");
        }
    }

    @Override
    public Result<String> updateProUserPassword(Long id, UpdateProUserPasswordDTO updateProUserPasswordDTO) {
        var old = proUserMapper.selectById(id);

        if (Encoder.MD5(updateProUserPasswordDTO.getOldPassword() + salt).equals(old.getPassword())) {
            old.setPassword(Encoder.MD5(updateProUserPasswordDTO.getNewPassword() + salt));
            proUserMapper.updateById(old);
            return Result.success("????????????");
        } else {
            return Result.forbidden("???????????????");
        }
    }

    @Override
    public Result<PageQueryVO<AccountWithSanVO>> queryAllSubUser(Long id, Integer current, Integer size) {
        var data = accountMapper.selectPage(
                new Page<>(current, size),
                Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getAgent, id)
        );
        return Result.success(accountService.getAccountWithSanVOPageQueryVO(data), "????????????");
    }

    @Override
    public Result<PageQueryVO<AccountWithSanVO>> querySubUserByAccount(Long id, Integer current, Integer size, String keyword) {
        var data = accountMapper.selectPage(
                new Page<>(current, size),
                Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getAgent, id)
                        .eq(AccountEntity::getAccount, keyword)
        );
        return Result.success(accountService.getAccountWithSanVOPageQueryVO(data), "????????????");
    }

    @Override
    public void updateSubAccount(Long id, AccountDTO accountDTO) {
        var oldSubAccountEntity = accountMapper.selectById(accountDTO.getId());

        if (oldSubAccountEntity != null) {
            if (oldSubAccountEntity.getAgent().equals(id)) {
                var newSubAccountEntity = AccountConvert.INSTANCE.toAccountEntity(accountDTO);
                newSubAccountEntity.setExpireTime(oldSubAccountEntity.getExpireTime());
                newSubAccountEntity.setRefresh(oldSubAccountEntity.getRefresh());
                newSubAccountEntity.setTaskType(oldSubAccountEntity.getTaskType());
                accountMapper.updateById(newSubAccountEntity);
            }
        }
    }

    @Override
    public Result<PageQueryVO<LogDTO>> querySubUserLogByAccount(Long id, Long userID, Integer current, Integer size) {
        var subUser = accountMapper.selectById(userID);
        if (subUser == null) {
            return Result.notFound("??????????????????");
        }
        if (!subUser.getAgent().equals(id)) {
            return Result.forbidden("????????????");
        }
        var data = logMapper.selectPage(
                new Page<>(current, size),
                Wrappers.<LogEntity>lambdaQuery()
                        .eq(LogEntity::getAccount, subUser.getAccount())
        );
        return Result.success(logService.getLogPageQueryVO(data), "????????????");
    }

    @Override
    public Result<String> forceFightSubUser(Long id, Long userID) {
        var preCheckResult = preCheckSubUser(id, userID);
        if (preCheckResult.getCode() != ResponseCodeConstants.SUCCESS) {
            return preCheckResult;
        }
        dynamicInfo.getFreezeTaskList().remove(userID);
        for (LockTask lockTask : dynamicInfo.getLockTaskList()) {
            if (lockTask.getAccount().getId().equals(userID)) {
                return Result.success("???????????????????????????");
            }
        }
        for (AccountEntity freeTask : dynamicInfo.getFreeTaskList()) {
            if (freeTask.getId().equals(userID)) {
                dynamicInfo.getFreeTaskList().remove(freeTask);
                dynamicInfo.getFreeTaskList().add(0, freeTask);
                return Result.success("????????????");
            }
        }
        dynamicInfo.getFreeTaskList().add(0, accountMapper.selectById(userID));
        dynamicInfo.getUserSanList().put(userID, 0);
        return Result.success("??????????????????");
    }

    @Override
    public Result<String> forceStopSubUser(Long id, Long userID) {
        var preCheckResult = preCheckSubUser(id, userID);
        if (preCheckResult.getCode() != ResponseCodeConstants.SUCCESS) {
            return preCheckResult;
        }
        taskService.forceHaltTask(userID);
        return Result.success("????????????");
    }

    @Override
    public Result<String> activateSubUserCdk(Long userID, String cdk) {
        return cdkService.activateCDK(userID, cdk);
    }

    @Override
    public Result<CDKListVO> queryProUserCDKList(Long id) {
        return cdkService.queryCDKList(CDKWrapper.AGENT, id.toString());
    }

    @Override
    public Result<String> createCdkByProUser(Long id, CreateCDKDTO createCDKDTO) {
        var proUser = proUserMapper.selectById(id);

        if (proUser == null) {
            return Result.notFound("??????????????????");
        }

        //????????????
        if (proUser.getBalance() < createCDKDTO.getCount() * dailyPrice * createCDKDTO.getParam() * proUser.getDiscount()) {
            return Result.forbidden("????????????");
        }

        //????????????
        proUser.setBalance(proUser.getBalance() - createCDKDTO.getCount() * dailyPrice * createCDKDTO.getParam() * proUser.getDiscount());
        proUserMapper.updateById(proUser);

        cdkService.createCDK(createCDKDTO);
        return Result.success("????????????");
    }

    @Override
    public Result<String> renewSubUserDaily(Long id, Long userID, Integer mo) {
        var proUser = proUserMapper.selectById(id);
        var subUser = accountMapper.selectById(userID);

        if (subUser == null) {
            return Result.notFound("??????????????????");
        }

        if (proUser == null) {
            return Result.notFound("??????????????????");
        }

        //????????????
        if (proUser.getBalance() < mo * 30 * dailyPrice * proUser.getDiscount()) {
            return Result.forbidden("????????????");
        }

        //????????????
        proUser.setBalance(proUser.getBalance() - mo * 30 * dailyPrice * proUser.getDiscount());
        proUserMapper.updateById(proUser);

        if (subUser.getExpireTime().isAfter(LocalDateTime.now())) {
            subUser.setExpireTime(subUser.getExpireTime().plusDays(mo * 30));
        } else {
            subUser.setExpireTime(LocalDateTime.now().plusDays(mo * 30));
        }
        accountMapper.updateById(subUser);
        return Result.success("????????????");
    }

    @NotNull
    private Result<String> preCheckSubUser(Long id, Long userID) {
        var subUser = accountMapper.selectById(userID);
        if (subUser == null) {
            return Result.notFound("??????????????????");
        }
        if (!subUser.getAgent().equals(id)) {
            return Result.forbidden("????????????");
        }
        if (subUser.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.forbidden("??????????????????");
        }
        return Result.success("????????????");
    }
}
