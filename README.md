# 拾光相册

Android 本地照片与视频相册，把回忆整理成可以自由翻阅的故事相册。

## 下载

[下载最新安卓安装包](https://github.com/zhou-xucheng/shiguang-album/releases/latest/download/shiguang-album.apk) · [版本说明](https://github.com/zhou-xucheng/shiguang-album/releases/latest)

支持 Android 8.0 及以上。已安装旧版请直接覆盖升级，不要先卸载。无需账号；微信里无法下载时，请用手机浏览器打开。

## 使用

- 故事 → ＋ 新故事 → 选择照片和视频 → 网格整理 → 下一步填写相册名称、文字和封面 → 完成。
- 完成的故事直接进入左右滑动浏览；照片和视频保持同一顺序，支持底部小缩略图与高密度缩略图总览。
- 自动播放与手动浏览在同一页面，播放中仍可左右滑动；手动滑动会暂停自动前进。
- 首页默认每行三本小封面，名称在下方；支持常驻搜索、年份筛选、收藏、置顶和网格/大封面切换。
- 长按拖动排序；批量移动、改日期；排序面板支持撤销，异常的 1904 年等默认日期不会直接当成拍摄日期。
- 封面可直接单指拖动、双指缩放；照片自动播放停留时间可自定义 1—60 秒。
- 相册支持照片视频管理、图片编辑和回收站；外观提供五套配色、可关闭纸纹和减少动效。
- 当前没有能完整传递交互式故事册的在线服务，因此不提供单媒体、多媒体或视频故事分享入口。

照片和故事保存在手机，应用不会自动上传相册。0.9.0 已移除备份与恢复入口，没有云同步。卸载或清除应用数据会清除故事及内部副本，公共相册原图不受影响。

## 构建

JDK 17、Android SDK 36。在 `android-app` 配置自己的 `local.properties` 后执行：

```sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest
```

Windows 使用 `gradlew.bat`。Release 签名配置示例为 `android-app/keystore.properties_sample`；发布者的签名私钥不会公开。不同签名的自建安装包不能覆盖官方发布包。

## 来源与许可证

基于 [Fossify Gallery](https://github.com/FossifyOrg/Gallery) 独立维护，遵守 GPL-3.0，保留上游版权和许可证。包名 `org.fossify.shiguang`，与上游应用独立安装。本仓库不包含家庭私人照片，测试素材为人工生成。
