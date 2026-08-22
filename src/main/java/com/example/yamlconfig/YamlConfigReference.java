package com.example.yamlconfig;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML key → Python 字段 的引用实现
 * resolve() 返回对应的 PyTargetExpression
 */
public class YamlConfigReference extends PsiReferenceBase<YAMLKeyValue> {

    private final String keyPath;
    private final List<ConfigClassInfo> configClasses;

    public YamlConfigReference(
            @NotNull YAMLKeyValue element,
            @NotNull String keyPath,
            @NotNull List<ConfigClassInfo> configClasses) {
        super(element);
        this.keyPath = keyPath;
        this.configClasses = configClasses;
    }

    @Override
    public PsiElement resolve() {
        return ConfigClassScanner.resolveField(keyPath, configClasses);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        // 代码补全：返回当前 prefix 下的所有字段名
        List<String> result = new ArrayList<>();
        for (ConfigClassInfo config : configClasses) {
            boolean matches = keyPath.equals(config.getPrefix())
                    || keyPath.startsWith(config.getPrefix() + ".");
            if (!matches) continue;

            String remaining = keyPath.equals(config.getPrefix())
                    ? ""
                    : keyPath.substring(config.getPrefix().length() + 1);

            if (remaining.isEmpty() || !remaining.contains(".")) {
                result.addAll(config.getFields().keySet());
            }
        }
        return result.toArray();
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
        // 引用范围只覆盖 key 部分，不包括 value 和缩进
        PsiElement key = getElement().getKey();
        if (key != null) {
            int start = key.getStartOffsetInParent();
            return new TextRange(start, start + key.getTextLength());
        }
        return TextRange.EMPTY_RANGE;
    }
}
