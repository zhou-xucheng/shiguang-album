# 拾光相册

Android 本地照片与视频相册，把回忆整理成故事，制作视频分享给亲友。

## 下载

[下载最新安卓安装包](https://github.com/zhou-xucheng/shiguang-album/releases/latest/download/shiguang-album.apk) · [版本说明](https://github.com/zhou-xucheng/shiguang-album/releases/latest)

支持 Android 8.0 及以上。已安装旧版请直接覆盖升级，不要先卸载。无需账号；微信里无法下载时，请用手机浏览器打开。

## 使用

- 故事 → ＋ 新故事 → 在应用内相册中轻点多选照片和视频 → 添加到故事 → 完成。
- 首页故事卡片「分享给亲友」，或故事详情底部「分享」 → 选择样式 → 生成分享视频 → 分享到微信 → 选择联系人或群并发送。
- 相册支持照片视频管理、图片编辑、收藏和回收站；设置中可以选择四种配色。
- 视频相册提供纸质相册、光影故事、简洁纪实三种样式，保留视频原声，不添加背景音乐。

照片和故事保存在手机，应用不会自动上传相册。0.9.0 已移除备份与恢复入口，没有云同步。卸载或清除应用数据会清除故事及内部副本，公共相册原图不受影响。视频分享用于观看，不能还原可编辑故事。

视频生成期间需留在页面。微信内发送及压缩效果取决于实际手机与微信客户端。

## 构建

JDK 17、Android SDK 36。在 `android-app` 配置自己的 `local.properties` 后执行：

```sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest
```

Windows 使用 `gradlew.bat`。Release 签名配置示例为 `android-app/keystore.properties_sample`；发布者的签名私钥不会公开。不同签名的自建安装包不能覆盖官方发布包。

## 来源与许可证

基于 [Fossify Gallery](https://github.com/FossifyOrg/Gallery) 独立维护，遵守 GPL-3.0，保留上游版权和许可证。包名 `org.fossify.shiguang`，与上游应用独立安装。本仓库不包含家庭私人照片，测试素材为人工生成。
