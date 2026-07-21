# 🌙 深眠白噪音 (Deep Sleep White Noise)

一款基于 **Android Jetpack Compose** 构建的高品质睡眠、冥想与专注辅助应用。结合多轨道自然音效合成与双耳节律（Binaural Beats）脑波诱导技术，助你快速入睡、提升专注力与缓解焦虑。

---

## 🌟 核心功能

* **🎵 多音轨实时自然音效合成**：
  * 支持多种高质量自然环境音效混音（细雨、潮汐、松风、篝火、虫鸣、颂钵、溪流、煮雪等）。
  * 独立音量滑块调节，自由打造专属你的助眠声音空间。

* **🧠 脑波诱导 (双耳节律 Binaural Beats)**：
  * 利用左右耳差频技术（Alpha 专注波、Theta 深度放松波、Delta 深度睡眠波）。
  * 引导大脑快速进入睡眠或高效率专注状态。

* **💾 预设混音与自定义保存**：
  * 内置多种经典推荐预设（深沉睡眠、雨夜读书、静心冥想等）。
  * 支持将自定义音效组合与音量参数保存至本地数据库（Room Database）。

* **⏱️ 智能睡眠定时器**：
  * 支持 15 分钟、30 分钟、60 分钟及自定义倒计时。
  * 到期自动平滑淡出并关闭音频，无需担心睡着后耗电。

* **🔊 后台持续播放服务**：
  * 基于 Android 前台服务（Foreground Service）构建，锁屏或切换后台依然流畅播放。

* **🎨 现代暗色助眠 UI**：
  * 基于 Jetpack Compose & Material 3 设计，极致暗黑夜间视觉体验，护眼不刺眼。

---

## 🛠️ 技术栈与架构

* **语言**：Kotlin
* **UI 框架**：Jetpack Compose + Material 3
* **音频引擎**：原生 `AudioTrack` 实时 PCM 算法合成（音效混合 + 动态双耳生成器）
* **后台服务**：Android Foreground Service (`SleepNoiseService`)
* **本地存储**：Room Database (存储自定义预设与配置)
* **架构模式**：MVVM / Repository 模式

---

## 🚀 本地开发与构建

### 开发环境要求
* **Android Studio**: Ladybug / Koala 或更新版本
* **JDK**: 17 或以上
* **Compile SDK**: 34+ (Android 14)

### 快速开始

1. **克隆项目到本地**
   ```bash
   git clone https://github.com/sscc9/White-Noise.git
   cd White-Noise
   ```

2. **配置环境变量**
   在项目根目录下创建 `.env` 文件，可参考 `.env.example`：
   ```env
   GEMINI_API_KEY=YOUR_GEMINI_API_KEY
   ```

3. **导入与运行**
   * 使用 **Android Studio** 打开该项目文件夹。
   * 等待 Gradle Sync 完成后，选择真机或模拟器点击 **Run (Shift + F10)** 运行。

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源许可证。

