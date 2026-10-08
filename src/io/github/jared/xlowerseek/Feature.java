package io.github.jared.xlowerseek;

/** Feature definitions; translation engines use one mutually exclusive mode. New features reuse the same persistence and UI path. */
enum Feature {
    AD_BLOCK("ad_block","去广告","过滤时间线推广内容，关闭即可恢复。"),
    LOWER_SEEK("lower_seek","下半区双击快进","双击后继续连点可累加，关闭保留 X 原操作。"),
    SEEK_STEP("seek_step","自定义快进时长","关闭后使用 X 原有时长。"),
    SWIPE_SEEK("swipe_seek","滑动调进度","全屏左右滑动，进度条同步更新。"),
    HOLD_SPEED("hold_speed","长按 2×","长按加速，松手恢复原速。"),
    DOWNLOAD("video_download","视频下载","全屏显示下载图标，保存到相册。"),
    LOCAL_CONTEXT("local_context","结合帖子上下文","参考已加载的上级回复和引用内容。"),
    NATIVE_TRANSLATE("native_translate","X 原生自动翻译","备用模式，使用 X 自带的联网翻译。"),
    LOCAL_TEXT("local_text","本地翻译","选择离线引擎，与 X 原生翻译互斥。");
    final String key,title,description;
    Feature(String key,String title,String description){this.key=key;this.title=title;this.description=description;}
}
