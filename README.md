# 拾光相册

一个给日常生活使用的 Android 本地相册：收藏照片和视频，把回忆整理成故事，再做成视频分享给亲友。

## 下载

[下载最新安卓安装包](https://github.com/zhou-xucheng/shiguang-album/releases/latest/download/shiguang-album.apk) · [查看版本说明](https://github.com/zhou-xucheng/shiguang-album/releases/latest)

下载 `shiguang-album.apk` 后在安卓手机打开。已安装旧版时直接覆盖安装，不要先卸载。
最低 Android 8.0。手机安装需完成安卓系统确认；若微信中无法下载，请用手机浏览器打开链接。

## 功能

- 照片与视频管理、图片编辑、收藏、回收站和四种界面主题。
- 大封面故事首页、搜索、草稿、自适应图文时间线、排序与封面设置。
- 本地生成 MP4 视频相册：纸质相册、光影故事、简洁纪实三种样式，保留视频原声，可预览、保存和分享。
- 完整 ZIP 故事备份与恢复。没有云同步；卸载前请导出备份并保存在应用之外。

应用不会自动上传相册。GitHub 托管的是安装包和源码，不是使用者的照片。
视频导出期间需要留在页面。背景音乐已移除；微信内的发送效果取决于实际手机与客户端。

## 构建

JDK 17、Android SDK 36。进入 `android-app`，配置自己的 `local.properties`，运行：

```sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest
```

Windows 使用 `gradlew.bat`。Release 需配置自己的签名，示例见 `android-app/keystore.properties_sample`。
发布者的签名私钥不在本仓库中。自己构建并签名的 APK 无法直接覆盖另一签名的安装包。

## 来源与许可证

基于 [Fossify Gallery](https://github.com/FossifyOrg/Gallery) 二次开发，遵守 GPL-3.0，保留上游版权及许可证。
这是独立维护的衍生应用。包名为 `org.fossify.shiguang`，与上游应用独立安装。

本仓库是应用源码发布快照；界面演示使用人工测试素材，不包含家庭私人照片。
