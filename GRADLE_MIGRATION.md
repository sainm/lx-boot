# Maven 到 Gradle 迁移说明

本项目已从 Maven 迁移到 Gradle (Kotlin DSL)。

## 迁移内容

### 已完成的工作

1. ✅ 创建了 `build.gradle.kts` 构建文件
2. ✅ 创建了 `settings.gradle.kts` 设置文件
3. ✅ 创建了 `gradle.properties` 配置文件
4. ✅ 创建了 Gradle Wrapper（`gradlew`、`gradlew.bat`、`gradle/wrapper/`）
5. ✅ 迁移了所有依赖项
6. ✅ 配置了注解处理器（Lombok、MapStruct）
7. ✅ 更新了 `.gitignore` 文件

## 使用方法

### 首次使用

如果是首次使用，需要下载 Gradle Wrapper JAR 文件：

**Windows:**
```bash
.\gradlew.bat wrapper
```

**Linux/Mac:**
```bash
chmod +x gradlew
./gradlew wrapper
```

### 常用命令

**构建项目:**
```bash
./gradlew build
```

**运行应用:**
```bash
./gradlew bootRun
```

**清理构建:**
```bash
./gradlew clean
```

**打包 JAR:**
```bash
./gradlew bootJar
```

**运行测试:**
```bash
./gradlew test
```

**查看依赖树:**
```bash
./gradlew dependencies
```

## 依赖对比

所有依赖都已从 `pom.xml` 迁移到 `build.gradle.kts`，版本保持一致。

## 注意事项

1. **保留 pom.xml**: `pom.xml` 文件已保留用于参考，可以在确认 Gradle 构建正常后删除。

2. **构建输出目录**: 
   - Maven 使用 `target/` 目录
   - Gradle 使用 `build/` 目录

3. **IDE 支持**:
   - IntelliJ IDEA: 会自动识别 Gradle 项目
   - Eclipse: 需要安装 Gradle 插件
   - VS Code: 需要安装 Gradle 扩展

4. **注解处理器**:
   - Lombok 和 MapStruct 的注解处理器已正确配置
   - 顺序已优化：Lombok -> MapStruct

5. **Spring Boot 插件**:
   - 使用 Spring Boot Gradle 插件，版本与 Maven 一致（3.5.6）

## 故障排除

如果遇到问题：

1. 清理构建缓存：`./gradlew clean`
2. 刷新依赖：`./gradlew --refresh-dependencies build`
3. 删除 `.gradle` 目录后重新构建

## 迁移日期

迁移完成日期：2024年

