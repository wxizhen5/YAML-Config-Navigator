package com.example.yamlconfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Python 引用贡献者：为 @configuration_properties(prefix="xxx") 中的 prefix 值注册引用
 * 点击 prefix 字符串跳转到 application.yml 中对应的顶级 key
 */
public class PythonPrefixReferenceContributor extends PsiReferenceContributor {

    private static final Logger LOG = Logger.getInstance(PythonPrefixReferenceContributor.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        LOG.info("[YamlConfigNavigator] PythonPrefixReferenceContributor.registerReferenceProviders 被调用");
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PyStringLiteralExpression.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        if (!(element instanceof PyStringLiteralExpression stringLit)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        LOG.info("[YamlConfigNavigator] PythonPrefix getReferencesByElement: text=" + stringLit.getText());

                        // 检查父节点是否是 PyKeywordArgument，且关键字是 prefix
                        PsiElement parent = stringLit.getParent();
                        LOG.info("[YamlConfigNavigator]   parent type=" + (parent != null ? parent.getClass().getSimpleName() : "null"));
                        if (!(parent instanceof PyKeywordArgument keywordArg)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        String keyword = keywordArg.getKeyword();
                        LOG.info("[YamlConfigNavigator]   keyword=" + keyword);
                        if (!"prefix".equals(keyword)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 检查父节点是否是装饰器调用，且名称包含 configuration_properties
                        // PSI 结构: PyDecorator -> PyCallExpression -> PyArgumentList -> PyKeywordArgument -> PyStringLiteralExpression
                        PsiElement grandParent = keywordArg.getParent();
                        LOG.info("[YamlConfigNavigator]   grandParent type=" + (grandParent != null ? grandParent.getClass().getSimpleName() : "null"));
                        PsiElement callElement = grandParent;
                        if (grandParent != null && !(grandParent instanceof PyCallExpression)) {
                            callElement = grandParent.getParent();
                        }
                        LOG.info("[YamlConfigNavigator]   callElement type=" + (callElement != null ? callElement.getClass().getSimpleName() : "null"));
                        if (!(callElement instanceof PyCallExpression callExpr)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 获取被调用函数名称（callee）
                        String decoratorName = null;
                        PyExpression callee = callExpr.getCallee();
                        if (callee instanceof PyReferenceExpression refExpr) {
                            decoratorName = refExpr.getReferencedName();
                        }
                        if (decoratorName == null) {
                            decoratorName = callee != null ? callee.getText() : null;
                        }
                        LOG.info("[YamlConfigNavigator]   decoratorName=" + decoratorName);
                        if (decoratorName == null || !decoratorName.contains("configuration_properties")) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 获取 prefix 值（去掉引号）
                        String prefixValue = stringLit.getStringValue();
                        LOG.info("[YamlConfigNavigator]   prefixValue=" + prefixValue);
                        if (prefixValue == null || prefixValue.isEmpty()) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        LOG.info("[YamlConfigNavigator] 找到 @configuration_properties(prefix=\"" + prefixValue + "\")，创建引用");
                        PythonPrefixReference reference = new PythonPrefixReference(stringLit, prefixValue);
                        return new PsiReference[]{reference};
                    }
                }
        );
    }
}
