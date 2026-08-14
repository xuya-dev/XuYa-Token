package dev.xuya.token.core.spi;

import java.util.Set;

/**
 * SPI:部门层级来源。{@code DEPT_AND_CHILD} 数据权限需要通过它
 * 解析部门的全部子部门;无实现时退化为仅本部门。
 *
 * @author 青衣
 */
public interface DeptProvider {

    /**
     * 加载给定部门的全部后代部门 ID(递归,不含自身)。
     *
     * @param deptId 部门 ID
     * @return 后代部门 ID 集合;无层级信息返回空集
     */
    Set<String> loadDescendantDeptIds(String deptId);
}
