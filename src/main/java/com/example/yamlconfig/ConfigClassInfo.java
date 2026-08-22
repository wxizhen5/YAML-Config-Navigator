package com.example.yamlconfig;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;

import java.util.Map;

/**
 * 配置类信息：prefix → PyClass，以及字段名 → PyTargetExpression 映射
 */
public class ConfigClassInfo {
    private final String prefix;
    private final PsiElement prefixElement;  // 装饰器中 prefix 字符串的 PSI 元素
    private final PyClass pyClass;
    private final Map<String, PyTargetExpression> fields;

    public ConfigClassInfo(String prefix, PsiElement prefixElement, PyClass pyClass,
                           Map<String, PyTargetExpression> fields) {
        this.prefix = prefix;
        this.prefixElement = prefixElement;
        this.pyClass = pyClass;
        this.fields = fields;
    }

    public String getPrefix() {
        return prefix;
    }

    public PsiElement getPrefixElement() {
        return prefixElement;
    }

    public PyClass getPyClass() {
        return pyClass;
    }

    public Map<String, PyTargetExpression> getFields() {
        return fields;
    }
}
