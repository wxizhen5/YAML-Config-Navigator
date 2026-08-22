# YAML Config Navigator

PyCharm 插件，实现 YAML 配置文件与 Python 配置类之间的**双向跳转**。

支持 `@configuration_properties(prefix="xxx")` 注解的配置类。

**纯 Java 实现，Maven 构建。**

## 功能

- **YAML → Python**：在 `application.yml` 中 `Ctrl+Click` 或 `Ctrl+B` 跳转到对应 Python 字段
- **Python → YAML**：在 Python 配置类字段行号栏点击图标跳转到 yml 对应位置
- **代码补全**：yml 中输入 key 时提示对应 Python 字段名

## 环境要求

- JDK 17+
- Maven 3.8+
- IntelliJ IDEA 2024.1+（用于调试插件，也可用命令行编译）

## 编译运行

### 第一步：安装 Python / YAML 插件依赖到本地 Maven 仓库

Python 和 YAML 插件的 jar 不在公共 Maven 仓库，需要从 PyCharm 安装目录获取并安装到本地仓库。

找到 PyCharm 安装目录下的插件 jar：
- **Python 插件**：`PyCharm安装目录/plugins/python/lib/python.jar`（或 `python-community.jar`）
- **YAML 插件**：`PyCharm安装目录/plugins/yaml/lib/yaml.jar`

执行安装命令（替换为你实际的 jar 路径）：

```bash
mvn install:install-file ^
  -Dfile="C:\Program Files\JetBrains\PyCharm 2024.1\plugins\python\lib\python.jar" ^
  -DgroupId=com.jetbrains.plugins -DartifactId=python ^
  -Dversion=2024.1.4 -Dpackaging=jar

mvn install:install-file ^
  -Dfile="C:\Program Files\JetBrains\PyCharm 2024.1\plugins\yaml\lib\yaml.jar" ^
  -DgroupId=com.jetbrains.plugins -DartifactId=yaml ^
  -Dversion=2024.1.4 -Dpackaging=jar
```

然后编辑 `pom.xml`，取消 Python 和 YAML 依赖的注释。

### 第二步：编译

```bash
cd E:\onlineAgentEndFeb\zzyyFour\yaml-config-navigator
mvn clean package
```

生成的插件 jar 在 `target/yaml-config-navigator-1.0.0.jar`。

### 第三步：安装到 PyCharm

1. 打开 PyCharm → `Settings → Plugins → ⚙（齿轮图标）→ Install Plugin from Disk...`
2. 选择 `target/yaml-config-navigator-1.0.0.jar`
3. 重启 PyCharm

### 调试方式（IntelliJ IDEA）

如果需要断点调试插件：

1. 用 **IntelliJ IDEA**（不是 PyCharm）打开项目
2. 安装 **Plugin DevKit** 插件
3. `Run → Edit Configurations → + → Plugin`
4. 配置 JRE 为 PyCharm 的 JDK
5. 运行后会启动带插件的 PyCharm 沙箱

## 使用示例

### Python 配置类

```java
// 这是 Python 代码，不是 Java
from dataclasses import dataclass
from typing import Optional
from config_loader import component, configuration_properties

@dataclass
class Address:
    city: str = None
    street: str = None

@component
@configuration_properties(prefix="user")
@dataclass
class UserConfig:
    name: str = None
    age: int = None
    address: Optional[Address] = None
```

### application.yml

```yaml
user:
  name: 张三
  age: 25
  address:
    city: 西安
    street: 科技路
```

### 跳转效果

- 在 yml 中 `Ctrl+Click` `name` → 跳转到 `UserConfig.name` 字段
- 在 yml 中 `Ctrl+Click` `city` → 跳转到 `Address.city` 字段
- 在 Python `name` 字段行号栏点击图标 → 跳转到 yml 中 `user.name`

## 项目结构

```
yaml-config-navigator/
├── pom.xml                            # Maven 构建配置
├── README.md
└── src/main/
    ├── java/com/example/yamlconfig/
    │   ├── ConfigClassInfo.java               # 配置类信息（prefix + 字段映射）
    │   ├── ConfigClassScanner.java            # 扫描 @configuration_properties 配置类
    │   ├── YamlConfigReference.java           # YAML key → Python 字段 引用实现
    │   ├── YamlConfigReferenceContributor.java # 引用贡献者（yml 侧入口）
    │   └── PythonFieldLineMarkerProvider.java  # Python 字段行号标记（反向跳转）
    └── resources/META-INF/
        └── plugin.xml                       # 插件描述符，注册扩展点
```

## 核心 API 说明

### YAML PSI（来自 yaml 插件）
- `YAMLKeyValue`：yml 中的 key-value 对（如 `name: 张三`）
- `YAMLMapping`：yml 中的映射对象（如 `user:` 下的所有 key）
- `YAMLDocument`：yml 文档根节点

### Python PSI（来自 Python 插件）
- `PyClass`：Python 类
- `PyTargetExpression`：类级别赋值表达式（如 `name: str = None`）
- `PyDecorator`：装饰器（如 `@configuration_properties`）

### IntelliJ Platform
- `PsiReferenceContributor`：为特定 PSI 元素注册引用提供者
- `PsiReferenceProvider`：提供引用的工厂
- `PsiReferenceBase`：引用基类，实现 `resolve()` 返回目标元素
- `LineMarkerProvider`：行号标记提供者，在编辑器行号栏显示图标

## 已知限制

- 只处理文件名以 `application` 开头的 yml 文件
- 嵌套 dataclass 字段（如 `address.city`）目前只跳转到第一层字段
- 配置类扫描是全项目扫描，大项目可能有性能影响（可优化为缓存）
- 不支持 `prefix` 中包含变量或表达式的情况
- Maven 方式需要手动安装 Python/YAML 插件 jar 到本地仓库

## 扩展方向

- 使用 `CachedValue` 缓存配置类扫描结果，提升性能
- 支持嵌套 dataclass 的深层字段跳转
- 支持 `@Value` 注解的单个字段跳转
- 支持多 profile（application-dev.yml 等）
- 支持重命名重构（Rename Refactoring）
