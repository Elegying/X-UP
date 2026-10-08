package io.github.jared.xlowerseek;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

public final class ModuleStatusActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);SettingsUi ui=new SettingsUi(this,"状态与帮助","检查配置连接和 X 版本",true);
        LinearLayout card=ui.section("当前环境");
        ui.info(card,"X-UP",BuildConfig.VERSION_NAME+" · Xposed API 102");
        ui.info(card,"设置服务",SettingsStore.isShared()?"已连接 LSPosed":"未连接，请检查模块是否启用。");
        try{android.content.pm.PackageInfo x=getPackageManager().getPackageInfo("com.twitter.android",0);ui.info(card,"X 版本",x.versionName+(x.getLongVersionCode()==312310001L?" · 已适配":" · 未适配，保持原有行为"));}
        catch(android.content.pm.PackageManager.NameNotFoundException e){ui.info(card,"X 版本","未安装 X");}
        ui.info(ui.section("启用步骤"),"安装或更新后","在 LSPosed 启用 X-UP 并勾选 X，然后完全退出并重新打开 X。");
        LinearLayout help=ui.section("使用说明");
        ui.navigation(help,"设置何时生效","",()->ui.details("生效时间","设置修改后自动保存。功能开关实时同步；最新评论会调用 X 的原生排序动作。关闭下载图标不取消正在保存的视频。设置服务连接不代表每个注入功能已成功加载。"));
        ui.navigation(help,"翻译范围与隐私","",()->ui.details("翻译范围与隐私","默认启用腾讯本地翻译，需要单独下载模型。X 原生自动翻译为可选联网备用，两者不能同时开启。原生模式覆盖范围由 X 决定。\n\n切换到本地翻译后，可参考已加载的上级回复和引用，在手机处理帖子、简介、通知和私信等可见文字；不上传到翻译接口。输入框、图片不处理；特殊自绘内容可能无法识别。\n\n模型约 462 MB，安装后单独下载。日志不记录正文，译文仅在内存缓存；图片和特殊自绘内容不在当前覆盖保证内。"));
        ui.navigation(help,"手势说明","",()->ui.details("手势说明","下半区双击和原右侧双击只快进一次，左上区域保持 X 原行为。滑动与长按仅针对全屏视频；长按 2× 不显示中央提示，松手恢复之前速度。"));
    }
}
