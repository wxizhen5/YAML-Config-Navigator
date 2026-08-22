package com.example.yamlconfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

/**
 * Python @configuration_properties(prefix="xxx") 中 prefix 值的引用
 * 点击 prefix 字符串跳转到 application.yml 中对应的顶级 key
 */
public class PythonPrefixReference extends PsiReferenceBase<PsiElement> {

    private static final Logger LOG = Logger.getInstance(PythonPrefixReference.class);

    private final String prefixValue;

    public PythonPrefixReference(@NotNull PsiElement element, @NotNull String prefixValue) {
        super(element, getStringValueRange(element), true);
        this.prefixValue = prefixValue;
    }

    @Override
    public @Nullable PsiElement resolve() {
        LOG.info("[YamlConfigNavigator] PythonPrefixReference.resolve() 被调用，prefix=" + prefixValue);
        var project = getElement().getProject();
        YAMLKeyValue found = YamlFileScanner.findTopLevelKey(project, prefixValue);
        if (found != null) {
            LOG.info("[YamlConfigNavigator] 找到 yml key: " + found.getName() + " in " + found.getContainingFile().getName());
            // 返回 key 的名字部分（YAMLKey），这样跳转到 key 名称而不是整个键值对
            return found.getKey();
        }
        LOG.info("[YamlConfigNavigator] 未找到 yml key: " + prefixValue);
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return new Object[0];
    }

    /**
     * 计算字符串字面量中值的 TextRange（去掉引号）
     */
    private static TextRange getStringValueRange(@NotNull PsiElement element) {
        String text = element.getText();
        int start = 0;
        int end = text.length();
        // 去掉引号
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' || first == '\'') && (last == '"' || last == '\'')) {
                start = 1;
                end = text.length() - 1;
            }
        }
        return new TextRange(start, end);
    }
}
