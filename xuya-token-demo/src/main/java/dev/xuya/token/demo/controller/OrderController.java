package dev.xuya.token.demo.controller;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.spring.boot.starter.DataScopeContext;
import dev.xuya.token.spring.boot.starter.annotation.RequiresDataScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示控制器:数据权限(行级过滤)。
 * admin(ALL)看全部订单,alice(DEPT,d2)只见 d2 订单,
 * carol(SELF)级别不足直接 403。
 *
 * @author 青衣
 */
@RestController
public class OrderController {

    /** 模拟订单数据,每条归属一个部门。 */
    private static final List<Map<String, String>> ORDERS = List.of(
            Map.of("orderId", "1001", "deptId", "d1", "amount", "500.00"),
            Map.of("orderId", "1002", "deptId", "d2", "amount", "320.50"),
            Map.of("orderId", "1003", "deptId", "d3", "amount", "880.00"));

    /**
     * 订单查询接口,演示 {@code @RequiresDataScope}:要求至少 DEPT 级别,
     * 校验通过后按 {@code DataScopeContext} 中的可见部门过滤模拟数据。
     *
     * @return 数据权限级别与过滤后的订单列表
     */
    @RequiresDataScope(DataScopeType.DEPT)
    @GetMapping("/orders")
    public Map<String, Object> orders() {
        DataScope scope = DataScopeContext.get();
        List<Map<String, String>> visible = scope.getType() == DataScopeType.ALL
                ? ORDERS
                : ORDERS.stream()
                        .filter(order -> scope.getVisibleDeptIds().contains(order.get("deptId")))
                        .toList();
        return Map.of(
                "scope", scope.getType().name(),
                "visibleDeptIds", scope.getVisibleDeptIds(),
                "orders", visible);
    }
}
