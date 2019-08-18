package com.liuhu.socket.service.impl;

import com.liuhu.socket.common.DateUtils;
import com.liuhu.socket.common.MathConstants;
import com.liuhu.socket.dao.MarketInfoMapper;
import com.liuhu.socket.dao.PersonalDetailInfoMapper;
import com.liuhu.socket.dao.PersonalInfoMapper;
import com.liuhu.socket.dao.TradeInfoMapper;
import com.liuhu.socket.domain.MarketInputDomain;
import com.liuhu.socket.domain.TradeInputDomain;
import com.liuhu.socket.entity.MarketInfo;
import com.liuhu.socket.entity.PersonalDetailInfo;
import com.liuhu.socket.entity.PersonalInfo;
import com.liuhu.socket.entity.TradeInfo;
import com.liuhu.socket.enums.PersonalStatusEnum;
import com.liuhu.socket.enums.TradeStatusEnum;
import com.liuhu.socket.service.TradeInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author liuhu-jk
 * @Date 2019/8/15 17:44
 * @Description
 **/
@Service
@Slf4j
public class TradeInfoServiceImpl implements TradeInfoService {
    @Resource
    TradeInfoMapper tradeInfoMapper;
    @Resource
    PersonalInfoMapper personalInfoMapper;
    @Resource
    MarketInfoMapper marketInfoMapper;
    @Resource
    PersonalDetailInfoMapper personalDetailInfoMapper;
    @Override
    @Transactional
    public int operateTrade(TradeInputDomain input) {
        int returnValue =-1;
        /**
         * 校验账户信息
         */
        Map map = this.checkAccountRule(input,TradeStatusEnum.SALE.getCode());
        if(map.containsKey("back")){
            return -1;
        }
        /**
         * 校验行情信息是否符合规范
         */
        int checkMarket = checkMarketInfo(input);
        if(checkMarket==-1){
            return checkMarket;
        }
        TradeInfo tradeInfo = new TradeInfo();
        tradeInfo.setHandNum((double) input.getHandNum());
        tradeInfo.setPersonId(input.getPersonId());
        tradeInfo.setShareCode(input.getShareCode());
        tradeInfo.setTradeDate(DateUtils.parse(input.getInvestTime(),DateUtils.DateFormat.YYYY_MM_DD));
        tradeInfo.setTradePrice(input.getUnitValue());
        tradeInfo.setType(String.valueOf(TradeStatusEnum.BUY.getCode()));
        tradeInfo.setUpdateDate(new Date());
        int i =  tradeInfoMapper.insertSelective(tradeInfo);
        PersonalInfo  backPersonInfo = (PersonalInfo) map.get("personalInfo");
        if(i>0){
            /**
             * 购买费率
             */
            double commission = MathConstants.computerCommission(input.getUnitValue(),input.getHandNum(),TradeStatusEnum.BUY.getCode());
            double newTotalAmount = backPersonInfo.getTotalAmount() - commission;
            double newMarketAmount = backPersonInfo.getTotalMarketValue() +  input.getUnitValue()*input.getHandNum();
            double newTotalShare = backPersonInfo.getTotalShare()-commission;
            backPersonInfo.setTotalAmount(newTotalAmount);
            backPersonInfo.setTotalMarketValue(newMarketAmount);
            backPersonInfo.setTotalShare(newTotalShare);
            backPersonInfo.setUpdateDate(new Date());
            personalInfoMapper.updateAmountByPersonId(backPersonInfo);
            /**
             * 查询投资详情
             */
            PersonalDetailInfo detailInfo =new  PersonalDetailInfo();
            detailInfo.setPersonId(input.getPersonId());
            detailInfo.setShareCode(input.getShareCode());
            List<PersonalDetailInfo> detailInfoList = personalDetailInfoMapper.queryDetailInfo(detailInfo);
            /**
             * 更新个人账户详情信息，如果有则更新  无则添加
             */
            double investAmount = input.getUnitValue()*input.getHandNum();
            if(detailInfoList!=null&&detailInfoList.size()>0){
                detailInfo = detailInfoList.get(0);
                detailInfo.setCurrentPrice(input.getUnitValue());
                detailInfo.setHandNum(detailInfo.getHandNum()+input.getHandNum());
                double ownAmount = detailInfo.getTradePrice()*detailInfo.getHandNum();
                double tradePrice = (ownAmount+investAmount)/(input.getHandNum()+detailInfo.getHandNum());
                detailInfo.setTradePrice(tradePrice);
                detailInfo.setHandPrice(ownAmount+investAmount);
                double shareAmount = detailInfo.getHandNum()*detailInfo.getCurrentPrice()-detailInfo.getHandNum()*detailInfo.getTradePrice();
                detailInfo.setShareAmount(shareAmount);
                double sharePer = shareAmount/(detailInfo.getHandNum()*detailInfo.getTradePrice());
                String sharePerStr = MathConstants.Pointkeep(sharePer*100,4)+"%";
                detailInfo.setSharePer(sharePerStr);
                detailInfo.setUpdateDate(new Date());
                returnValue = personalDetailInfoMapper.updatePersonalDetailByEntity(detailInfo);
            }else{
                detailInfo.setHandNum(input.getHandNum());
                detailInfo.setUpdateDate(new Date());
                detailInfo.setHandPrice(investAmount);
                detailInfo.setTradePrice(input.getUnitValue());
                detailInfo.setCurrentPrice(input.getUnitValue());
                returnValue = personalDetailInfoMapper.insertSelective(detailInfo);
            }
        }
        return returnValue;
    }

    @Override
    public int saleTrade(TradeInputDomain input) {
        /**
         * 校验账户信息
         */
        Map map = this.checkAccountRule(input,TradeStatusEnum.BUY.getCode());
        if(map.containsKey("back")){
            return -1;
        }
        /**
         * 校验行情信息是否符合规范
         */
        int checkMarket = checkMarketInfo(input);
        if(checkMarket==-1){
            return checkMarket;
        }
        /**
         * 查询投资详情
         */
        PersonalDetailInfo detailInfo =new  PersonalDetailInfo();
        detailInfo.setPersonId(input.getPersonId());
        detailInfo.setShareCode(input.getShareCode());
        detailInfo.setStatus(PersonalStatusEnum.VALID.getCode());
        List<PersonalDetailInfo> detailInfoList = personalDetailInfoMapper.queryDetailInfo(detailInfo);
        if(detailInfoList==null||detailInfoList.size()<=0){
            log.warn("投资详情为空，不能进行赎回");
            return -1;
        }
        PersonalDetailInfo personalDetailInfo = detailInfoList.get(0);
        int ownValue = personalDetailInfo.getHandNum();
        int saleHandNum = input.getHandNum();
        if(ownValue<saleHandNum){
            log.warn("卖出的股票数大于当前持有的股票数");
            return -1;
        }
        TradeInfo tradeInfo = new TradeInfo();
        tradeInfo.setHandNum((double) input.getHandNum());
        tradeInfo.setPersonId(input.getPersonId());
        tradeInfo.setShareCode(input.getShareCode());
        tradeInfo.setTradeDate(DateUtils.parse(input.getInvestTime(),DateUtils.DateFormat.YYYY_MM_DD));
        tradeInfo.setTradePrice(input.getUnitValue());
        tradeInfo.setType(String.valueOf(TradeStatusEnum.SALE.getCode()));
        tradeInfo.setUpdateDate(new Date());
        int i =  tradeInfoMapper.insertSelective(tradeInfo);
        if (i>0){
            /**
             * 购买费率
             */
            PersonalInfo  backPersonInfo = (PersonalInfo) map.get("personalInfo");
            double commission = MathConstants.computerCommission(input.getUnitValue(),input.getHandNum(),TradeStatusEnum.SALE.getCode());
            double newTotalAmount = backPersonInfo.getTotalAmount() - commission;
            double newMarketAmount = backPersonInfo.getTotalMarketValue() -  input.getUnitValue()*input.getHandNum();
            double newTotalShare = backPersonInfo.getTotalShare()-commission;
            backPersonInfo.setTotalAmount(newTotalAmount);
            backPersonInfo.setTotalMarketValue(newMarketAmount);
            backPersonInfo.setUpdateDate(new Date());
            backPersonInfo.setTotalShare(newTotalShare);
            personalInfoMapper.updateAmountByPersonId(backPersonInfo);
            /**
             * 更新账户详情信息
             */
            double investAmount = input.getUnitValue()*input.getHandNum();
            detailInfo = detailInfoList.get(0);
            detailInfo.setCurrentPrice(input.getUnitValue());
            detailInfo.setHandNum(detailInfo.getHandNum()-input.getHandNum());
            double ownAmount = detailInfo.getTradePrice()*detailInfo.getHandNum();
            double tradePrice = (ownAmount-investAmount)/(input.getHandNum()-detailInfo.getHandNum());
            detailInfo.setTradePrice(tradePrice);
            if(detailInfo.getHandNum()==input.getHandNum()){
                detailInfo.setHandPrice(0.0);
                detailInfo.setStatus(PersonalStatusEnum.INVALID.getCode());
            }else{
                detailInfo.setHandPrice(ownAmount-investAmount);
            }
            double shareAmount = detailInfo.getHandNum()*detailInfo.getCurrentPrice()-detailInfo.getHandNum()*detailInfo.getTradePrice();
            detailInfo.setShareAmount(shareAmount);
            double sharePer = (detailInfo.getCurrentPrice()-detailInfo.getTradePrice())/detailInfo.getTradePrice();
            String sharePerStr = MathConstants.Pointkeep(sharePer*100,4)+"%";
            detailInfo.setSharePer(sharePerStr);
            detailInfo.setUpdateDate(new Date());
            personalDetailInfoMapper.updatePersonalDetailByEntity(detailInfo);
        }
        return 0;
    }

    /**
     * 校验账户信息
     * @param input
     * @return
     */
    private Map checkAccountRule(TradeInputDomain input,int type){
        Map<String,Object> map = new HashMap<>();
        String personId = input.getPersonId();
        double investAmount = input.getUnitValue()*input.getHandNum();
        PersonalInfo personalInfo = new PersonalInfo();
        personalInfo.setPersonId(personId);
        List<PersonalInfo> list =  personalInfoMapper.queryByEntity(personalInfo);
        if(list==null){
            log.info("查询个人账户不存在");
            map.put("back",-1);
            return map;
        }
        PersonalInfo backPersonInfo = list.get(0);
        double totalAmount = backPersonInfo.getTotalAmount();
        double totalMarket = backPersonInfo.getTotalMarketValue();
        double freeMoney =totalAmount -totalMarket;
        if(TradeStatusEnum.BUY.getCode()==type){
            if(investAmount>=freeMoney){
                log.info("购买金额大于可用金额");
                map.put("back",-1);
                return map;
            }
        }
        map.put("personalInfo",backPersonInfo);
        return map;
    }

    /**
     * 校验是否符合市场行情规范
     * @param input
     * @return
     */
    private int checkMarketInfo(TradeInputDomain input){
        MarketInputDomain marketInputDomain = new MarketInputDomain();
        marketInputDomain.setShareCode(input.getShareCode());
        marketInputDomain.setStartTime(input.getInvestTime());
        marketInputDomain.setEndTime(input.getInvestTime());

        List<MarketInfo> marketInfoList = marketInfoMapper.getShareInfo(marketInputDomain);
        if(marketInfoList==null || marketInfoList.size()<=0){
            log.info("没有当天的行情");
            return -1;
        }
        MarketInfo  marketInfo = marketInfoList.get(0);
        double highest = marketInfo.getHighest();
        double lowest = marketInfo.getLowest();
        if(input.getUnitValue()<lowest || input.getUnitValue()>highest){
            log.info("交易的价格不符合当天行情不能交易");
            return -1;
        }
        return 0;
    }
}
