package com.example.yamlconfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描项目中所有带 @configuration_properties(prefix="xxx") 的 Python 配置类
 */
public class ConfigClassScanner {

    private static final Logger LOG = Logger.getInstance(ConfigClassScanner.class);
    private static final String ANNOTATION_NAME = "configuration_properties";

    private ConfigClassScanner() {
    }

    /**
     * 扫描整个项目，返回所有配置类信息
     */
    @NotNull
    public static List<ConfigClassInfo> scanProject(@NotNull Project project) {
        List<ConfigClassInfo> result = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiManager psiManager = PsiManager.getInstance(project);

        var files = FilenameIndex.getAllFilesByExt(project, "py", scope);
        for (var virtualFile : files) {
            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile instanceof PyFile pyFile) {
                result.addAll(scanFile(pyFile));
            }
        }
        LOG.info("[YamlConfigNavigator] scanProject: 共找到 " + result.size() + " 个配置类");
        return result;
    }

    /**
     * 扫描单个 Python 文件
     */
    @NotNull
    public static List<ConfigClassInfo> scanFile(@NotNull PyFile pyFile) {
        List<ConfigClassInfo> result = new ArrayList<>();
        for (PyClass pyClass : pyFile.getTopLevelClasses()) {
            PrefixResult prefixResult = extractPrefix(pyClass);
            if (prefixResult != null) {
                Map<String, PyTargetExpression> fields = extractFields(pyClass);
                result.add(new ConfigClassInfo(prefixResult.value, prefixResult.element, pyClass, fields));
            }
        }
        return result;
    }

    /**
     * prefix 提取结果：值 + 对应的 PSI 元素
     */
    private static class PrefixResult {
        final String value;
        final PsiElement element;

        PrefixResult(String value, PsiElement element) {
            this.value = value;
            this.element = element;
        }
    }

    /**
     * 从 @configuration_properties(prefix="user") 装饰器中提取 prefix 值和元素
     */
    @Nullable
    private static PrefixResult extractPrefix(@NotNull PyClass pyClass) {
        var decoratorList = pyClass.getDecoratorList();
        if (decoratorList == null) return null;

        for (PyDecorator decorator : decoratorList.getDecorators()) {
            if (!matchesAnnotation(decorator)) continue;

            PyExpression[] args = decorator.getArguments();
            if (args == null || args.length == 0) continue;

            // 尝试获取 prefix 关键字参数
            for (PyExpression arg : args) {
                if (arg instanceof PyKeywordArgument keywordArg) {
                    if ("prefix".equals(keywordArg.getName())) {
                        PyExpression value = keywordArg.getValueExpression();
                        if (value instanceof PyStringLiteralExpression stringLit) {
                            String prefix = stringLit.getStringValue();
                            LOG.info("[YamlConfigNavigator]     提取到 prefix: " + prefix);
                            return new PrefixResult(prefix, stringLit);
                        }
                    }
                }
            }

            // 如果没有关键字参数，尝试第一个位置参数
            if (!(args[0] instanceof PyKeywordArgument)) {
                if (args[0] instanceof PyStringLiteralExpression stringLit) {
                    String prefix = stringLit.getStringValue();
                    LOG.info("[YamlConfigNavigator]     从位置参数提取到 prefix: " + prefix);
                    return new PrefixResult(prefix, stringLit);
                }
            }
        }
        return null;
    }

    /**
     * 判断装饰器是否是 @configuration_properties
     */
    private static boolean matchesAnnotation(@NotNull PyDecorator decorator) {
        String name = decorator.getName();
        if (name == null) return false;
        return name.equals(ANNOTATION_NAME) || name.endsWith("." + ANNOTATION_NAME);
    }

    /**
     * 提取类中所有字段（类级别赋值语句，如 name: str = None）
     */
    @NotNull
    private static Map<String, PyTargetExpression> extractFields(@NotNull PyClass pyClass) {
        Map<String, PyTargetExpression> fields = new HashMap<>();
        for (var statement : pyClass.getStatementList().getStatements()) {
            PyTargetExpression targetExpr = null;
            if (statement instanceof PyTargetExpression) {
                targetExpr = (PyTargetExpression) statement;
            } else if (statement instanceof PyAssignmentStatement assignment) {
                PyExpression[] targets = assignment.getTargets();
                if (targets.length > 0 && targets[0] instanceof PyTargetExpression target) {
                    targetExpr = target;
                }
            }
            if (targetExpr != null) {
                String name = targetExpr.getName();
                if (name != null) {
                    fields.put(name, targetExpr);
                }
            }
        }
        return fields;
    }

    /**
     * 根据 yml 中的完整 key 路径（如 user.address.city）找到对应的 Python 字段
     * 支持嵌套配置类（如 person.tra.name → Tra 类的 name 字段）
     */
    @Nullable
    public static PsiElement resolveField(
            @NotNull String keyPath,
            @NotNull List<ConfigClassInfo> configClasses) {

        for (ConfigClassInfo config : configClasses) {
            boolean matches = keyPath.equals(config.getPrefix())
                    || keyPath.startsWith(config.getPrefix() + ".");
            if (!matches) continue;

            // keyPath 等于 prefix 时，跳转到装饰器的 prefix 字符串
            if (keyPath.equals(config.getPrefix())) {
                LOG.info("[YamlConfigNavigator] resolveField: keyPath=prefix，返回 prefixElement");
                return config.getPrefixElement();
            }

            String remaining = keyPath.substring(config.getPrefix().length() + 1);
            return resolveNestedField(config.getPyClass(), remaining, configClasses);
        }
        return null;
    }

    /**
     * 递归解析嵌套字段路径
     * @param pyClass 当前类
     * @param path 剩余路径（如 tra.name 或 name）
     * @param configClasses 所有配置类（用于查找嵌套类）
     */
    @Nullable
    private static PsiElement resolveNestedField(
            @NotNull PyClass pyClass,
            @NotNull String path,
            @NotNull List<ConfigClassInfo> configClasses) {

        String fieldName = path.contains(".")
                ? path.substring(0, path.indexOf('.'))
                : path;
        String remaining = path.contains(".")
                ? path.substring(path.indexOf('.') + 1)
                : "";

        Map<String, PyTargetExpression> fields = extractFields(pyClass);
        PyTargetExpression field = fields.get(fieldName);
        if (field == null) {
            LOG.info("[YamlConfigNavigator] resolveNestedField: 类 " + pyClass.getName()
                    + " 中找不到字段 " + fieldName);
            return null;
        }

        // 如果没有剩余路径，返回当前字段
        if (remaining.isEmpty()) {
            LOG.info("[YamlConfigNavigator] resolveNestedField: 返回字段 " + fieldName);
            return field;
        }

        // 有剩余路径，需要解析字段类型，进入嵌套类
        PyClass nestedClass = resolveFieldType(field, configClasses);
        if (nestedClass == null) {
            LOG.info("[YamlConfigNavigator] resolveNestedField: 字段 " + fieldName + " 的类型无法解析");
            return field; // 类型解析失败时返回当前字段
        }

        LOG.info("[YamlConfigNavigator] resolveNestedField: 进入嵌套类 " + nestedClass.getName()
                + "，剩余路径: " + remaining);
        return resolveNestedField(nestedClass, remaining, configClasses);
    }

    /**
     * 解析字段的类型注解，找到对应的 PyClass
     * 支持 Optional[Tra]、List[Tra]、Tra 等形式
     */
    @Nullable
    private static PyClass resolveFieldType(
            @NotNull PyTargetExpression field,
            @NotNull List<ConfigClassInfo> configClasses) {

        PyAnnotation annotation = field.getAnnotation();
        if (annotation == null) return null;

        PyExpression annotationValue = annotation.getValue();
        if (annotationValue == null) return null;

        String typeText = annotationValue.getText();
        LOG.info("[YamlConfigNavigator] resolveFieldType: 字段 " + field.getName()
                + " 的类型注解: " + typeText);

        // 提取类型名：去掉 Optional[]、List[]、Dict[] 等包装
        String typeName = extractTypeName(typeText);
        if (typeName == null || typeName.isEmpty()) return null;

        LOG.info("[YamlConfigNavigator] resolveFieldType: 提取到类型名: " + typeName);

        // 在所有配置类和项目中的类中查找
        for (ConfigClassInfo config : configClasses) {
            if (typeName.equals(config.getPyClass().getName())) {
                return config.getPyClass();
            }
        }

        // 在项目中按类名查找（包括非配置类，如 Tra）
        Project project = field.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        PsiManager psiManager = PsiManager.getInstance(project);
        var files = FilenameIndex.getAllFilesByExt(project, "py", scope);
        for (var virtualFile : files) {
            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile instanceof PyFile pyFile) {
                for (PyClass pyClass : pyFile.getTopLevelClasses()) {
                    if (typeName.equals(pyClass.getName())) {
                        return pyClass;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 从类型注解文本中提取实际类型名
     * Optional[Tra] → Tra
     * List[Tra] → Tra
     * Tra → Tra
     * Optional[List[Tra]] → Tra
     */
    @Nullable
    private static String extractTypeName(@NotNull String typeText) {
        String result = typeText.trim();
        // 去掉泛型包装，取最内层的类型名
        while (result.contains("[")) {
            int start = result.indexOf('[');
            int end = result.lastIndexOf(']');
            if (start < 0 || end <= start) break;
            result = result.substring(start + 1, end).trim();
        }
        // 去掉可能的逗号（如 Union[Tra, None]）
        if (result.contains(",")) {
            String[] parts = result.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!"None".equals(trimmed) && !trimmed.isEmpty()) {
                    result = trimmed;
                    break;
                }
            }
        }
        return result.isEmpty() ? null : result;
    }
}
