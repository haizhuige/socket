package com.liuhu.socket.domain.output;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Map;

@Setter
@Getter
public class MarketRateTheeOutPutDTO {

    //花费总额
    private Double amount;

    //收益
    private Double income;

    //当前收益最大金额
    private Double currentAmount;

    //计算天数
    private Integer days;

    //最大仓位数
    private Integer maxCount;

    private Date startTime;

    private Map<Integer,Double> allIncomeMap;


}
