package cn.fanzha.classroom;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppUpdateManager {
    public interface Callback { void onResult(Result result); }
    public static final class Result {
        public final boolean updateAvailable; public final String message, apkSha256, versionName;
        public final String[] apkUrls;
        Result(boolean available, String message, String[] urls, String sha, String versionName) {
            updateAvailable=available; this.message=message; apkUrls=urls; apkSha256=sha; this.versionName=versionName;
        }
    }
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private AppUpdateManager() {}

    public static void check(Context context, Callback callback) {
        EXECUTOR.execute(() -> {
            Result result;
            try {
                JSONObject manifest=new JSONObject(new String(downloadManifest(),StandardCharsets.UTF_8));
                int code=manifest.optInt("latestAppVersionCode",BuildConfig.VERSION_CODE);
                if(code<=BuildConfig.VERSION_CODE) result=new Result(false,"当前已是最新 App 版本 "+BuildConfig.VERSION_NAME,new String[0],"",BuildConfig.VERSION_NAME);
                else {
                    String name=manifest.getString("latestAppVersionName");
                    result=new Result(true,"发现 App "+name+"\n\n"+manifest.optString("appChangelog","包含功能与兼容性更新。"),UpdateDownloadClient.sources(manifest,"apkUrls","apkUrl"),manifest.getString("apkSha256"),name);
                }
            } catch(Exception e) { result=new Result(false,"检查失败，稍后可在更新中心重试",new String[0],"",""); }
            Result finalResult=result; new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResult(finalResult));
        });
    }

    public static void download(Context context, Result release) {
        WashiDialog.ProgressHandle progress=WashiDialog.progress(context,"下载 App "+release.versionName,
                "加速节点 + GitHub Releases","正在尝试可用下载源。下载完成后会先核对 SHA-256，再交给系统安装器。");
        EXECUTOR.execute(() -> {
            File apk=null; String error=null;
            try {
                byte[] bytes=UpdateDownloadClient.downloadWithFallback(release.apkUrls,150*1024*1024,"application/vnd.android.package-archive, application/octet-stream");
                if(!sha256(bytes).equals(release.apkSha256.toLowerCase(Locale.ROOT)))throw new SecurityException("APK 校验失败");
                File dir=new File(context.getFilesDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("无法创建更新目录");
                apk=new File(dir,"fanzha-"+release.versionName+".apk");try(FileOutputStream out=new FileOutputStream(apk)){out.write(bytes);out.getFD().sync();}
            }catch(Exception e){error=e.getMessage()==null?"下载或校验失败":e.getMessage();}
            File finalApk=apk;String finalError=error;new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if(finalApk==null){
                    WashiDialog.message(context,"下载未完成","当前版本保持可用",
                            finalError+"。已完成自动重试，你可以稍后回到更新中心再次尝试。",true,
                            WashiDialog.Action.primary("知道了",null));
                }else{
                    try{launchInstaller(context,finalApk);}
                    catch(Exception e){
                        WashiDialog.message(context,"无法打开安装器","APK 已完成校验",
                                "安装包已经安全下载，但系统未能打开安装界面。请确认已允许此应用安装未知来源应用。",true,
                                WashiDialog.Action.primary("知道了",null));
                    }
                }
            });
        });
    }

    private static void launchInstaller(Context context,File apk){Uri uri=FileProvider.getUriForFile(context,BuildConfig.APPLICATION_ID+".files",apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
    private static byte[] downloadManifest()throws Exception{return UpdateDownloadClient.downloadWithFallback(new String[]{BuildConfig.STORY_MANIFEST_URL,BuildConfig.STORY_FALLBACK_MANIFEST_URL},2*1024*1024,"application/json");}
    private static String sha256(byte[] bytes)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
}
