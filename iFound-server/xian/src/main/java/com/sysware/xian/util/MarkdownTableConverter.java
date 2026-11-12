package com.sysware.xian.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sysware.xian.util.domain.MarkdownTableColumn;
import lombok.NoArgsConstructor;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Markdown 表格转换工具，支持将 Map、JsonNode 或普通 Java 对象集合转换为表格。
 *
 * @Author: LiYanRu
 * @date: 2026-08-05
 */
@NoArgsConstructor
public final class MarkdownTableConverter {
    private static final String EMPTY_CELL = "-";
    /**
     * 属性访问器缓存：key 为“对象类型 + 字段名”，value 为解析好的属性访问器。
     * <p>反射解析（getBeanInfo / getMethod / getDeclaredField）开销较大，且同一类型同一字段会被多行重复读取，
     * 因此解析一次后缓存复用，避免每行数据都做一次反射。
     * <p>ConcurrentMap 保证多线程并发调用（如多个 SSE 请求同时转表格）下线程安全。
     */
    private static final ConcurrentMap<AccessorKey, ValueAccessor> ACCESSOR_CACHE = new ConcurrentHashMap<>();

    /**
     * 使用字段与表头映射将数据集合转换为 Markdown 表格。<p>
     * 映射的 key 是 Map 键名或 Java 对象属性名，value 是表格表头。<p>
     * 列顺序与映射的迭代顺序一致，需要固定顺序时应传入 LinkedHashMap。
     * <h3>只会显示headerMapping里面有的字段，没有的字段会被过滤</h3>
     *
     * @param rows          数据行集合，元素可以是 Map、JsonNode 或普通 Java 对象
     * @param headerMapping 字段名称与表头名称映射
     * @return Markdown 表格；数据行或表头映射为空时返回空字符串
     */
    public static String toMarkdownTable(Collection<?> rows, Map<String, String> headerMapping) {
        Objects.requireNonNull(headerMapping, "Markdown 表格表头映射不能为 null");

        List<MarkdownTableColumn> columns = headerMapping.entrySet().stream()
                .map(entry -> MarkdownTableColumn.of(entry.getKey(), entry.getValue()))
                .toList();
        return toMarkdownTable(rows, columns);
    }

    /**
     * 使用有序列定义将数据集合转换为 Markdown 表格。
     * <p>
     * 每一列按照 columns 中的声明顺序读取 Map 键值或 Java 对象属性值。
     * <h3>只会显示columns里面有的字段，没有的字段会被过滤</h3>
     *
     * @param rows    数据行集合，元素可以是 Map、JsonNode 或普通 Java 对象
     * @param columns 有序列定义
     * @return Markdown 表格；数据行或列定义为空时返回空字符串
     */
    public static String toMarkdownTable(Collection<?> rows, List<MarkdownTableColumn> columns) {
        Objects.requireNonNull(rows, "Markdown 表格数据集合不能为 null");
        Objects.requireNonNull(columns, "Markdown 表格列定义不能为 null");
        if (rows.isEmpty() || columns.isEmpty()) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();

        // 1. 按列定义生成表头和分隔行
        appendHeader(markdown, columns);
        appendSeparator(markdown, columns.size());

        // 2. 按相同列顺序读取并生成每一行数据
        for (Object row : rows) {
            appendRow(markdown, row, columns);
        }
        return markdown.toString();
    }

    /**
     * 追加 Markdown 表头。
     *
     * @param markdown Markdown 内容容器
     * @param columns  有序列定义
     */
    private static void appendHeader(StringBuilder markdown, List<MarkdownTableColumn> columns) {
        markdown.append('|');
        for (MarkdownTableColumn column : columns) {
            markdown.append(' ')
                    .append(escapeCell(column.getHeader()))
                    .append(" |");
        }
        markdown.append('\n');
    }

    /**
     * 追加 Markdown 表头分隔行。
     *
     * @param markdown    Markdown 内容容器
     * @param columnCount 表格列数
     */
    private static void appendSeparator(StringBuilder markdown, int columnCount) {
        markdown.append('|');
        markdown.append(" --- |".repeat(Math.max(0, columnCount)));
        markdown.append('\n');
    }

    /**
     * 追加一行 Markdown 表格数据。
     *
     * @param markdown Markdown 内容容器
     * @param row      当前行数据
     * @param columns  有序列定义
     */
    private static void appendRow(
            StringBuilder markdown,
            Object row,
            List<MarkdownTableColumn> columns
    ) {
        // 1. 按列定义逐个生成单元格
        markdown.append('|');
        for (MarkdownTableColumn column : columns) {
            // 1.1 读取该列字段值
            Object value = readValue(row, column.getFieldName());
            // 1.2 转义后写入单元格
            markdown.append(' ')
                    .append(escapeCell(value))
                    .append(" |");
        }
        // 2. 行尾换行
        markdown.append('\n');
    }

    /**
     * 根据字段路径读取数据值。
     * Map 和 JsonNode 优先读取完整 key，未命中时再按照点号逐级读取。
     *
     * @param source    数据源
     * @param fieldPath 字段路径
     * @return 字段值，不存在或数据源为空时返回 null
     */
    private static Object readValue(Object source, String fieldPath) {
        if (source == null) {
            return null;
        }

        // 1. Map 和 JsonNode 可能直接包含带点号的完整 key
        if (source instanceof Map<?, ?> || source instanceof JsonNode) {
            DirectValue directValue = readDirectValue(source, fieldPath);
            if (directValue.found()) {
                return directValue.value();
            }
        }

        // 2. 未命中时按点号逐级读取嵌套 Map、JsonNode 或对象属性
        Object current = source;
        for (String fieldName : fieldPath.split("\\.")) {
            if (current == null) {
                return null;
            }
            DirectValue nestedValue = readDirectValue(current, fieldName);
            if (!nestedValue.found()) {
                return null;
            }
            current = nestedValue.value();
        }
        return current;
    }

    /**
     * 从当前层级读取指定字段。
     *
     * @param source    当前层级数据源
     * @param fieldName 字段名称
     * @return 字段读取结果
     */
    private static DirectValue readDirectValue(Object source, String fieldName) {
        // 1. Map：直接按 key 读取
        if (source instanceof Map<?, ?> map) {
            return map.containsKey(fieldName)
                    ? DirectValue.found(map.get(fieldName))
                    : DirectValue.notFound();
        }
        // 2. JsonNode：直接按字段名读取
        if (source instanceof JsonNode node) {
            JsonNode value = node.get(fieldName);
            return value == null
                    ? DirectValue.notFound()
                    : DirectValue.found(value);
        }

        // 3. 普通 Java 对象：走缓存属性访问器（解析一次后复用）
        ValueAccessor accessor = ACCESSOR_CACHE.computeIfAbsent(
                new AccessorKey(source.getClass(), fieldName),
                MarkdownTableConverter::resolveAccessor
        );
        return DirectValue.found(accessor.read(source));
    }

    /**
     * 解析 Java 对象属性的读取访问器，按优先级依次尝试三种方式 <p>
     * 缓存中未命中时才调用本方法，解析成功后由 computeIfAbsent 自动写入缓存。 <p>
     * 三种方式都失败时抛出 IllegalArgumentException，提示调用方检查字段名或类型。
     *
     * @param accessorKey 对象类型和属性名称
     * @return 属性访问器
     * @throws IllegalArgumentException 类型中不存在可读属性时抛出
     */
    private static ValueAccessor resolveAccessor(AccessorKey accessorKey) {
        // 1. 优先查找 JavaBean getter（getXxx/isXxx）
        Method getter = findGetter(accessorKey.type(), accessorKey.fieldName());
        if (getter != null) {
            getter.trySetAccessible();
            return source -> invoke(getter, source, accessorKey.fieldName());
        }

        // 2. 其次查找与字段同名的公共无参方法（兼容 record 访问器）
        Method accessorMethod = findAccessorMethod(accessorKey.type(), accessorKey.fieldName());
        if (accessorMethod != null) {
            accessorMethod.trySetAccessible();
            return source -> invoke(accessorMethod, source, accessorKey.fieldName());
        }

        // 3. 最后兜底：直接反射读取字段（private 字段先打开访问权限）
        Field field = findField(accessorKey.type(), accessorKey.fieldName());
        if (field != null && field.trySetAccessible()) {
            return source -> readField(field, source, accessorKey.fieldName());
        }

        // 4. 三种方式均未命中，抛出异常提示调用方检查字段名或类型
        throw new IllegalArgumentException(
                "类型 " + accessorKey.type().getName() + " 中不存在可读属性：" + accessorKey.fieldName()
        );
    }

    /**
     * 查找 Java Bean 规范定义的 getter 读方法。
     * <p>通过 {@link Introspector#getBeanInfo(Class)} 获取类型的全部属性描述符（PropertyDescriptor），
     * 从中找出属性名与目标字段一致、且存在读方法（getXxx/isXxx）的项，返回该读方法。
     * <p>注意：JavaBean 规范要求属性名是 getter 方法名的去前缀小写化（如 getDeviceName → deviceName），
     * 因此下划线命名（如 device_name）无法命中 getter，会落到后续的字段反射读取。
     *
     * @param type      对象类型
     * @param fieldName 属性名称
     * @return getter 方法，类型中不存在匹配的 JavaBean 属性时返回 null
     * @throws IllegalArgumentException 类型无法被内省（Introspection）解析时抛出
     */
    private static Method findGetter(Class<?> type, String fieldName) {
        try {
            // 1. 内省类型，获取全部 JavaBean 属性描述符
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                // 1.1 属性名与目标字段一致，且存在读方法（getXxx/isXxx）即命中
                if (descriptor.getName().equals(fieldName) && descriptor.getReadMethod() != null) {
                    return descriptor.getReadMethod();
                }
            }
            // 2. 没有匹配的 JavaBean 属性，返回 null 交给下一级解析策略
            return null;
        } catch (IntrospectionException exception) {
            // 3. 类型无法内省时，包装为业务异常向上抛出
            throw new IllegalArgumentException("解析类型属性失败：" + type.getName(), exception);
        }
    }

    /**
     * 查找与属性同名的公共无参方法，兼容 Java record 访问器。
     * <p>record 的组件访问器（如 {@code String fieldName()}）不是 getXxx 形式，Introspector 无法识别为属性，
     * 但访问器方法与字段完全同名且是公共无参方法，这里用 {@link Class#getMethod(String, Class[])} 直接按名称查找。
     * <p>只接受公共方法（getMethod 天然只返回 public，含从父类继承的）、无参数、非静态的方法，
     * 避免误命中静态工具方法或带参方法。
     *
     * @param type      对象类型
     * @param fieldName 属性名称
     * @return 属性访问方法，不存在或不符合条件时返回 null
     */
    private static Method findAccessorMethod(Class<?> type, String fieldName) {
        try {
            // 1. 按名称查找公共方法（含从父类继承的 public 方法）
            Method method = type.getMethod(fieldName);
            // 2. 校验：必须无参且非静态，避免误命中带参方法或静态工具方法
            return method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers())
                    ? method
                    : null;
        } catch (NoSuchMethodException exception) {
            // 3. 没有同名方法，返回 null 交给下一级解析策略
            return null;
        }
    }

    /**
     * 在当前类型及其父类中查找指定名称的字段（含 private 字段）。
     * <p>从当前类开始，沿继承链逐级向上（父类 → 祖父类 → …）直到 Object 为止，
     * 用 {@link Class#getDeclaredField(String)} 查找，因此能命中 private/protected/包私有字段；
     * 每级查不到就向上一级继续，保证继承来的字段也能被找到。
     * <p>注意：本方法只负责“找到”字段，是否能实际读取（private 需先 trySetAccessible）由调用方决定。
     *
     * @param type      对象类型
     * @param fieldName 字段名称
     * @return 字段对象，当前类型及所有父类中都不存在时返回 null
     */
    private static Field findField(Class<?> type, String fieldName) {
        // 1. 从当前类型开始，沿继承链逐级向上查找
        Class<?> currentType = type;
        while (currentType != null && currentType != Object.class) {
            try {
                // 1.1 在当前层级查找声明字段（含 private/protected）
                return currentType.getDeclaredField(fieldName);
            } catch (NoSuchFieldException exception) {
                // 1.2 当前层级没有，继续向父类查找
                currentType = currentType.getSuperclass();
            }
        }
        // 2. 整条继承链都没有该字段，返回 null 交给下一级解析策略
        return null;
    }

    /**
     * 调用属性访问方法。
     *
     * @param method    属性访问方法
     * @param source    数据对象
     * @param fieldName 属性名称
     * @return 属性值
     */
    private static Object invoke(Method method, Object source, String fieldName) {
        try {
            // 1. 反射调用访问方法读取属性值
            return method.invoke(source);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            // 2. 权限不足或方法内部异常时，包装为业务异常向上抛出
            throw new IllegalArgumentException("读取属性失败：" + fieldName, exception);
        }
    }

    /**
     * 读取对象字段值。
     *
     * @param field     对象字段
     * @param source    数据对象
     * @param fieldName 字段名称
     * @return 字段值
     */
    private static Object readField(Field field, Object source, String fieldName) {
        try {
            // 1. 反射读取字段值
            return field.get(source);
        } catch (IllegalAccessException exception) {
            // 2. 字段不可访问时，包装为业务异常向上抛出
            throw new IllegalArgumentException("读取字段失败：" + fieldName, exception);
        }
    }

    /**
     * 转换并转义 Markdown 单元格内容。
     *
     * @param value 原始单元格值
     * @return 可安全写入 Markdown 表格的内容
     */
    private static String escapeCell(Object value) {
        // 1. 先将单元格值统一转换为文本
        String text = valueToText(value);
        // 2. 转义表格特殊字符：竖线防止破坏表格结构，换行替换为 <br>
        return text.replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\r", "<br>")
                .replace("\n", "<br>");
    }

    /**
     * 将单元格值转换为文本。
     *
     * @param value 单元格值
     * @return 文本值，空值返回占位符
     */
    private static String valueToText(Object value) {
        // 1. 空值返回占位符
        if (value == null) {
            return EMPTY_CELL;
        }
        // 2. JsonNode 特殊处理
        if (value instanceof JsonNode node) {
            // 2.1 null 节点 / 缺失节点返回占位符
            if (node.isNull() || node.isMissingNode()) {
                return EMPTY_CELL;
            }
            // 2.2 值节点（文本/数字/布尔）转文本，对象/数组节点输出 JSON 字符串
            return node.isValueNode() ? node.asText() : node.toString();
        }
        // 3. 普通对象直接调用 toString
        return String.valueOf(value);
    }

    /**
     * Java 对象属性访问器。
     */
    @FunctionalInterface
    private interface ValueAccessor {

        /**
         * 读取属性值。
         *
         * @param source 数据对象
         * @return 属性值
         */
        Object read(Object source);
    }

    /**
     * 属性访问器缓存键。
     *
     * @param type      对象类型
     * @param fieldName 属性名称
     */
    private record AccessorKey(Class<?> type, String fieldName) {
    }

    /**
     * 当前层级字段读取结果。
     *
     * @param found 是否找到字段
     * @param value 字段值
     */
    private record DirectValue(boolean found, Object value) {

        /**
         * 创建已找到的字段结果。
         *
         * @param value 字段值
         * @return 字段读取结果
         */
        private static DirectValue found(Object value) {
            return new DirectValue(true, value);
        }

        /**
         * 创建未找到的字段结果。
         *
         * @return 字段读取结果
         */
        private static DirectValue notFound() {
            return new DirectValue(false, null);
        }
    }
}
