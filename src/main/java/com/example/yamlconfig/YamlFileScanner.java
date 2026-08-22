package com.example.yamlconfig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * YAML 文件扫描工具：查找项目中的 application.yml 并定位指定 key
 */
public class YamlFileScanner {

    /**
     * 查找项目中所有 application.yml / application.yaml 文件
     */
    @NotNull
    public static List<YAMLFile> findApplicationYamlFiles(@NotNull Project project) {
        List<YAMLFile> result = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        for (String name : new String[]{"application.yml", "application.yaml"}) {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(name, scope);
            for (VirtualFile vf : files) {
                var psiFile = psiManager.findFile(vf);
                if (psiFile instanceof YAMLFile yamlFile) {
                    result.add(yamlFile);
                }
            }
        }
        return result;
    }

    /**
     * 在所有 application.yml 中查找指定顶级 key（如 person、user、server）
     * @return 匹配的 YAMLKeyValue，找不到返回 null
     */
    @Nullable
    public static YAMLKeyValue findTopLevelKey(@NotNull Project project, @NotNull String keyName) {
        List<YAMLFile> yamlFiles = findApplicationYamlFiles(project);
        for (YAMLFile yamlFile : yamlFiles) {
            YAMLKeyValue found = findTopLevelKeyInFile(yamlFile, keyName);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 在单个 YAML 文件中查找指定顶级 key
     */
    @Nullable
    private static YAMLKeyValue findTopLevelKeyInFile(@NotNull YAMLFile yamlFile, @NotNull String keyName) {
        for (YAMLDocument document : yamlFile.getDocuments()) {
            YAMLValue topValue = document.getTopLevelValue();
            if (topValue instanceof YAMLMapping mapping) {
                YAMLKeyValue found = mapping.getKeyValueByKey(keyName);
                if (found != null) return found;
            }
        }
        return null;
    }
}
