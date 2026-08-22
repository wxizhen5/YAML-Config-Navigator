package com.example.yamlconfig;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python 配置类字段的行号标记提供者
 * 在字段行号栏显示图标，点击跳转到 application.yml 对应位置
 * 支持顶层配置类和嵌套配置类（如 Tra）的字段
 */
public class PythonFieldLineMarkerProvider implements LineMarkerProvider {

    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PyTargetExpression targetExpr)) return null;

        PyClass pyClass = targetExpr.getContainingClass();
        if (pyClass == null) return null;

        String fieldName = targetExpr.getName();
        if (fieldName == null) return null;

        Project project = element.getProject();
        List<ConfigClassInfo> configClasses = ConfigClassScanner.scanProject(project);

        // 尝试构建 yml key 路径
        String keyPath = buildYamlKeyPath(pyClass, fieldName, configClasses);
        if (keyPath == null) return null;

        // 查找 application.yml 中对应的 key
        YAMLKeyValue yamlKey = findYamlKey(project, keyPath);
        if (yamlKey == null) return null;

        Icon icon = AllIcons.Nodes.Property;
        Function<PsiElement, String> tooltipProvider = e -> "跳转到 application.yml: " + keyPath;

        return new LineMarkerInfo<>(
                targetExpr,
                new TextRange(0, targetExpr.getTextLength()),
                icon,
                tooltipProvider,
                (e, elt) -> yamlKey.navigate(true),
                GutterIconRenderer.Alignment.LEFT
        );
    }

    /**
     * 构建字段在 yml 中的完整 key 路径
     * 支持顶层配置类（prefix.fieldName）和嵌套配置类（prefix.nestedField.fieldName）
     */
    @Nullable
    private String buildYamlKeyPath(
            @NotNull PyClass pyClass,
            @NotNull String fieldName,
            @NotNull List<ConfigClassInfo> configClasses) {

        // 情况1：当前类是顶层配置类（有 @configuration_properties）
        String prefix = extractPrefix(pyClass);
        if (prefix != null) {
            return prefix + "." + fieldName;
        }

        // 情况2：当前类是嵌套配置类（如 Tra），需要找到哪个配置类引用了它
        for (ConfigClassInfo config : configClasses) {
            for (Map.Entry<String, PyTargetExpression> entry : config.getFields().entrySet()) {
                String configFieldName = entry.getKey();
                PyTargetExpression configField = entry.getValue();
                String fieldTypeName = getFieldTypeName(configField);

                if (fieldTypeName != null && fieldTypeName.equals(pyClass.getName())) {
                    // 找到嵌套关系：config.prefix.configFieldName.currentFieldName
                    return config.getPrefix() + "." + configFieldName + "." + fieldName;
                }
            }
        }

        return null;
    }

    /**
     * 获取字段的类型名（从注解中提取）
     */
    @Nullable
    private String getFieldTypeName(@NotNull PyTargetExpression field) {
        PyAnnotation annotation = field.getAnnotation();
        if (annotation == null) return null;
        PyExpression value = annotation.getValue();
        if (value == null) return null;
        String typeText = value.getText().trim();

        // 去掉泛型包装，取最内层类型名
        while (typeText.contains("[")) {
            int start = typeText.indexOf('[');
            int end = typeText.lastIndexOf(']');
            if (start < 0 || end <= start) break;
            typeText = typeText.substring(start + 1, end).trim();
        }
        // 处理 Union[Tra, None] 等情况
        if (typeText.contains(",")) {
            for (String part : typeText.split(",")) {
                String trimmed = part.trim();
                if (!"None".equals(trimmed) && !trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return typeText.isEmpty() ? null : typeText;
    }

    /**
     * 从 @configuration_properties(prefix="xxx") 提取 prefix
     */
    @Nullable
    private String extractPrefix(@NotNull PyClass pyClass) {
        var decoratorList = pyClass.getDecoratorList();
        if (decoratorList == null) return null;

        for (PyDecorator decorator : decoratorList.getDecorators()) {
            String name = decorator.getName();
            if (name == null) continue;
            if (!name.equals("configuration_properties") && !name.endsWith(".configuration_properties")) {
                continue;
            }

            PyExpression[] args = decorator.getArguments();
            if (args == null || args.length == 0) continue;

            for (PyExpression arg : args) {
                if (arg instanceof PyKeywordArgument keywordArg) {
                    if ("prefix".equals(keywordArg.getName())) {
                        PyExpression value = keywordArg.getValueExpression();
                        if (value != null) {
                            return trimQuotes(value.getText());
                        }
                    }
                }
            }

            if (!(args[0] instanceof PyKeywordArgument)) {
                PyExpression value = args[0];
                if (value != null) {
                    return trimQuotes(value.getText());
                }
            }
        }
        return null;
    }

    /**
     * 在 application.yml 中查找指定 key 路径对应的 YAMLKeyValue
     */
    @Nullable
    private YAMLKeyValue findYamlKey(@NotNull Project project, @NotNull String keyPath) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        List<com.intellij.openapi.vfs.VirtualFile> files = new ArrayList<>();
        files.addAll(FilenameIndex.getAllFilesByExt(project, "yml", scope));
        files.addAll(FilenameIndex.getAllFilesByExt(project, "yaml", scope));

        PsiManager psiManager = PsiManager.getInstance(project);
        String[] keys = keyPath.split("\\.");

        for (var virtualFile : files) {
            if (!virtualFile.getName().startsWith("application")) continue;

            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile == null) continue;

            YAMLDocument document = null;
            for (var child : psiFile.getChildren()) {
                if (child instanceof YAMLDocument doc) {
                    document = doc;
                    break;
                }
            }
            if (document == null) continue;

            YAMLMapping currentMapping = null;
            for (var child : document.getChildren()) {
                if (child instanceof YAMLMapping mapping) {
                    currentMapping = mapping;
                    break;
                }
            }

            YAMLKeyValue result = null;
            for (String key : keys) {
                if (currentMapping == null) break;

                YAMLKeyValue found = null;
                for (var child : currentMapping.getChildren()) {
                    if (child instanceof YAMLKeyValue kv && key.equals(kv.getName())) {
                        found = kv;
                        break;
                    }
                }
                if (found == null) break;

                result = found;
                currentMapping = null;
                for (var child : found.getChildren()) {
                    if (child instanceof YAMLMapping mapping) {
                        currentMapping = mapping;
                        break;
                    }
                }
            }

            if (result != null) return result;
        }
        return null;
    }

    private String trimQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' || first == '\'') && (last == '"' || last == '\'')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
