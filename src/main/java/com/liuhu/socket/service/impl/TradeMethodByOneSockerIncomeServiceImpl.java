package com.liuhu.socket.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.liuhu.socket.common.MathConstants;
import com.liuhu.socket.dao.SerialTempMapper;
import com.liuhu.socket.domain.input.GetRateThreeIncomeInputDTO;
import com.liuhu.socket.domain.input.QueryRecentSerialRedConditionDTO;
import com.liuhu.socket.domain.output.MarketRateTheeOutPutDTO;
import com.liuhu.socket.domain.output.QueryRecentSerialRedOutPutDTO;
import com.liuhu.socket.service.TradeMethodService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("oneSockerIncome")
@Slf4j
public class TradeMethodByOneSockerIncomeServiceImpl implements TradeMethodService {

    @Autowired
    SerialTempMapper serialTempMapper;



    @Override
    public List<QueryRecentSerialRedOutPutDTO> getRecentFinalRatioStrategy(QueryRecentSerialRedConditionDTO marketInput2Domain) {
        return null;
    }

    @Override
    public List<QueryRecentSerialRedOutPutDTO> queryVRatioFromDownStartPoint(QueryRecentSerialRedConditionDTO input2Domain) throws Exception {
        return null;
    }

    @Override
    public List<QueryRecentSerialRedOutPutDTO> queryThreeDownRatioByDate(QueryRecentSerialRedConditionDTO input2Domain) throws Exception {
        return null;
    }

    /**
     * 根据单倍收益阶梯式的翻倍操作 每个阶梯 回本且盈利4%则回撤当前阶梯
     * @param getRateThreeIncomeInputDTO
     * @return
     */
    @Override
    public MarketRateTheeOutPutDTO getRateThreeIncome(GetRateThreeIncomeInputDTO getRateThreeIncomeInputDTO) {
        MarketRateTheeOutPutDTO returnRateDTO = new MarketRateTheeOutPutDTO();
        List<QueryRecentSerialRedOutPutDTO> minRateThreeList = serialTempMapper.getMinRateThree(getRateThreeIncomeInputDTO);
        if (minRateThreeList.size() == 0) {
            return returnRateDTO;
        }
        Double tIncome = 0.0;
        //记录每一个倍数对应的 收益率
        Map<Integer,Double> allIncomeMap = new HashMap<>();
        Map<Integer,Integer> floorMap = new HashMap<>();
        //到结束的收益率
        Double  passAllIncome = 0.0;
        for (QueryRecentSerialRedOutPutDTO queryRecentSerialRedOutPutDTO : minRateThreeList) {

            Double maxRatio = queryRecentSerialRedOutPutDTO.getMaxRatio();
            Double finalRatio = queryRecentSerialRedOutPutDTO.getFinalRatio();
            passAllIncome = finalRatio + passAllIncome;
          
            if (allIncomeMap.isEmpty()){
                if (maxRatio>getRateThreeIncomeInputDTO.getProfit()){
                    tIncome = tIncome+getRateThreeIncomeInputDTO.getProfit()-getRateThreeIncomeInputDTO.getFee();
                    continue;
                }else if (finalRatio>0){
                    tIncome = tIncome+finalRatio-getRateThreeIncomeInputDTO.getFee();
                    continue;
                }
                allIncomeMap.put(1,finalRatio-getRateThreeIncomeInputDTO.getFee());
              //  floorMap.put(1,getRateThreeIncomeInputDTO.getProfit()-getRateThreeIncomeInputDTO.getFee());
                continue;
            }
            /*if (cycleSumAmount+tIncome>100){
                returnRateDTO.setIncome(tIncome);
                returnRateDTO.setStartTime(queryRecentSerialRedOutPutDTO.getStartTime());
                returnRateDTO.setHandleFinalRatio(passAllIncome);
                return returnRateDTO;
            }*/
            Set<Integer> keySet = allIncomeMap.keySet();

            //最大倍数 也可以当size来使用
            Integer maxDouble = keySet.stream().reduce(0, Integer::max);
            Collection<Double> valuess = allIncomeMap.values();
            Double allSubIncome = valuess.stream().reduce(0.0, Double::sum);
            //最大翻倍 对应的收益
            Map<Integer, Double> sortedMap = new TreeMap<>(Collections.reverseOrder());
            sortedMap.putAll(allIncomeMap);

            Double  firstValue = sortedMap.get(1);

            //当次运行的层级数
            Integer    runDoubleUnit =Math.abs((int) (firstValue/ getRateThreeIncomeInputDTO.getDoubleSize()))+1;
            log.info("第一层ks的收益率:{}，最大值:{}，最后值为：{},当次层级：{}，每次收益率预览：{}", MathConstants.Pointkeep(firstValue,2),MathConstants.Pointkeep(maxRatio,2), MathConstants.Pointkeep(finalRatio,2),runDoubleUnit, JSONObject.toJSONString(allIncomeMap));
            //设置每次遍历的下标
            int k = 0;
            //遍历每一层
           for (Map.Entry entry:sortedMap.entrySet()){
               Integer key = (Integer) entry.getKey();
               Double income = (Double)entry.getValue();
               int baseDouble = runDoubleUnit - maxDouble + 1;
               int m;
               k = k+1;
               if (k==1&&income<-1*getRateThreeIncomeInputDTO.getDoubleSize()){
                   m = baseDouble;
               }else{
                   m = 1;
               }
               if (m*maxRatio+income>=getRateThreeIncomeInputDTO.getDoubleProfit()){
                   allIncomeMap.remove(key);

                   runDoubleUnit = runDoubleUnit - baseDouble;
                   tIncome = tIncome +getRateThreeIncomeInputDTO.getDoubleProfit()-getRateThreeIncomeInputDTO.getFee();
                   continue;
               }
               income = income + finalRatio;
               tIncome = tIncome-getRateThreeIncomeInputDTO.getFee();
               allIncomeMap.put(key,MathConstants.Pointkeep(income,2));
           }
           if (runDoubleUnit>maxDouble){
               for (int i = runDoubleUnit;i>maxDouble;i--){
                   allIncomeMap.put(i,finalRatio);
                   if (finalRatio>0){
                       tIncome = tIncome +finalRatio-getRateThreeIncomeInputDTO.getFee();
                   }else {
                       tIncome = tIncome -getRateThreeIncomeInputDTO.getFee();
                       allIncomeMap.put(i,MathConstants.Pointkeep(finalRatio,2));
                   }

               }
           }

        }
        returnRateDTO.setIncome(MathConstants.Pointkeep(tIncome,2));
        returnRateDTO.setAllIncomeMap(allIncomeMap);
        returnRateDTO.setHandleFinalRatio(passAllIncome);
        returnRateDTO.setAllIncomeMap(allIncomeMap);
        return returnRateDTO;
    }

    @Override
    public List<QueryRecentSerialRedOutPutDTO> queryThreeUpThenAndPreDownRegular(QueryRecentSerialRedConditionDTO input) {
        return null;
    }
}
