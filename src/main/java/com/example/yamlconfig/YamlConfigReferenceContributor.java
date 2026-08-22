package com.example.yamlconfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.List;

/**
 * YAML 引用贡献者：为 application.yml 中的 key 注册指向 Python 字段的引用
 */
public class YamlConfigReferenceContributor extends PsiReferenceContributor {

    private static final Logger LOG = Logger.getInstance(YamlConfigReferenceContributor.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        LOG.info("[YamlConfigNavigator] registerReferenceProviders 被调用，注册 YAMLKeyValue pattern");
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(YAMLKeyValue.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        if (!(element instanceof YAMLKeyValue keyValue)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 只处理 application.yml / application.yaml 文件
                        var containingFile = element.getContainingFile();
                        if (containingFile == null) return PsiReference.EMPTY_ARRAY;
                        String fileName = containingFile.getName();
                        LOG.info("[YamlConfigNavigator] getReferencesByElement 被调用，文件: " + fileName + ", key: " + keyValue.getName());

                        if (fileName == null || !fileName.startsWith("application")) {
                            LOG.info("[YamlConfigNavigator] 文件名不是 application 开头，跳过");
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 构建当前 key 的完整路径
                        String keyPath = buildKeyPath(keyValue);
                        LOG.info("[YamlConfigNavigator] 构建 keyPath: " + keyPath);
                        if (keyPath == null) return PsiReference.EMPTY_ARRAY;

                        // 扫描项目中的配置类
                        var project = element.getProject();
                        List<ConfigClassInfo> configClasses = ConfigClassScanner.scanProject(project);
                        LOG.info("[YamlConfigNavigator] 扫描到配置类数量: " + configClasses.size());
                        for (ConfigClassInfo c : configClasses) {
                            LOG.info("[YamlConfigNavigator]   配置类: prefix=" + c.getPrefix() + ", fields=" + c.getFields().keySet());
                        }
                        if (configClasses.isEmpty()) return PsiReference.EMPTY_ARRAY;

                        // 检查当前 keyPath 是否匹配某个配置类的 prefix
                        boolean matches = configClasses.stream().anyMatch(c ->
                                keyPath.equals(c.getPrefix()) || keyPath.startsWith(c.getPrefix() + "."));
                        LOG.info("[YamlConfigNavigator] keyPath=" + keyPath + " 是否匹配: " + matches);
                        if (!matches) return PsiReference.EMPTY_ARRAY;

                        // 创建引用
                        YamlConfigReference reference = new YamlConfigReference(keyValue, keyPath, configClasses);
                        LOG.info("[YamlConfigNavigator] 创建引用成功，keyPath=" + keyPath);
                        return new PsiReference[]{reference};
                    }
                }
        );
    }

    /**
     * 从当前 YAMLKeyValue 向上遍历父节点，构建完整的 key 路径
     */
    private String buildKeyPath(@NotNull YAMLKeyValue keyValue) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        PsiElement current = keyValue;

        while (current != null) {
            if (current instanceof YAMLKeyValue kv) {
                String name = kv.getName();
                if (name == null) break;
                parts.add(0, name);
            }
            current = current.getParent();
        }

        return parts.isEmpty() ? null : String.join(".", parts);
    }
}
