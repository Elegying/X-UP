package io.github.jared.xlowerseek;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

public final class DownloadSettingsActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);SettingsUi ui=new SettingsUi(this,"视频下载","在全屏视频页点击下载图标",true);
        LinearLayout card=ui.section("保存");ui.info(card,"保存位置","相册 · Movies/X");ui.divider(card);ui.info(card,"画质","自动选择可用的最高画质 MP4。");
        ui.divider(card);ui.info(card,"下载进度","图标显示进度，通知中可以取消下载。");
        LinearLayout notice=ui.section("通知");status=ui.status(notice,"");
        ui.button(notice,"管理下载通知",true).setOnClickListener(v->{
            if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        ui.navigation(ui.section("帮助"),"下载限制","",()->ui.details("下载限制","仅保存可直接获取的 MP4，暂不支持直播或只有分片的来源。单个视频上限 2 GB，一次下载一个视频。失败或取消会清理未完成文件。"));
    }
    @Override protected void onResume(){super.onResume();refresh();}
    private void refresh(){status.setText(getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()?"通知已开启":"通知已关闭 · 仍可在视频图标上查看进度");}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);refresh();}
}
