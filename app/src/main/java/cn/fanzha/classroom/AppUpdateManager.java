package cn.fanzha.classroom;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
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
        public final boolean updateAvailable, requiresReinstall;
        public final String message, apkSha256, versionName;
        public final String[] apkUrls;
        Result(boolean available, boolean requiresReinstall, String message, String[] urls, String sha, String versionName) {
            updateAvailable=available; this.requiresReinstall=requiresReinstall; this.message=message;
            apkUrls=urls; apkSha256=sha; this.versionName=versionName;
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
                if(code<=BuildConfig.VERSION_CODE) {
                    result=new Result(false,false,"当前已是最新 App 版本 "+BuildConfig.VERSION_NAME,new String[0],"",BuildConfig.VERSION_NAME);
                } else {
                    String name=manifest.getString("latestAppVersionName");
                    String expectedCertificate=manifest.optString("apkCertificateSha256","").trim().toLowerCase(Locale.ROOT);
                    boolean requiresReinstall=!expectedCertificate.isEmpty()
                            && !expectedCertificate.equals(installedCertificateSha256(context));
                    String message="发现 App "+name+"\n\n"+manifest.optString("appChangelog","包含功能与兼容性更新。");
                    if(requiresReinstall) {
                        message+="\n\n此版本使用了固定发布签名。你当前安装的是早期测试签名版本，Android 为保护应用数据不会允许直接覆盖安装。请在发布页下载新版后，先卸载旧版，再重新安装一次；之后的版本可正常应用内更新。";
                    }
                    result=new Result(true,requiresReinstall,message,
                            UpdateDownloadClient.sources(manifest,"apkUrls","apkUrl"),manifest.getString("apkSha256"),name);
                }
            } catch(Exception e) { result=new Result(false,false,"检查失败，稍后可在更新中心重试",new String[0],"",""); }
            Result finalResult=result; new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResult(finalResult));
        });
    }

    public static void download(Context context, Result release) {
        WashiDialog.ProgressHandle progress=WashiDialog.progress(context,"下载 App "+release.versionName,
                "加速节点 + GitHub Releases","正在连接可用下载源。");
        EXECUTOR.execute(() -> {
            File apk=null; String failure=null;
            try {
                byte[] bytes=UpdateDownloadClient.downloadWithFallback(release.apkUrls,150*1024*1024,
                        "application/vnd.android.package-archive, application/octet-stream",
                        new UpdateDownloadClient.DownloadProgressListener() {
                            @Override public void onAttempt(int attempt, int totalAttempts) {
                                progress.setIndeterminateMessage("正在连接下载源（"+attempt+"/"+totalAttempts+"）");
                            }
                            @Override public void onProgress(long downloadedBytes, long totalBytes) {
                                progress.setDownloadProgress(downloadedBytes,totalBytes);
                            }
                        });
                progress.setIndeterminateMessage("下载完成，正在核对完整性。");
                if(!sha256(bytes).equals(release.apkSha256.toLowerCase(Locale.ROOT)))throw new SecurityException("APK 校验失败");
                File dir=new File(context.getFilesDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("无法创建更新目录");
                apk=new File(dir,"fanzha-"+release.versionName+".apk");try(FileOutputStream out=new FileOutputStream(apk)){out.write(bytes);out.getFD().sync();}
            }catch(Exception e){failure=UpdateDownloadClient.describeFailure(e);}
            File finalApk=apk;String finalFailure=failure;new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                if(finalApk==null){
                    WashiDialog.message(context,"下载暂未完成","已保护当前可用版本",
                            finalFailure+"。\n\n已依次尝试加速节点和备用源。未通过校验的文件不会安装；检查网络后可直接再次尝试。",true,
                            WashiDialog.Action.primary("再次尝试",() -> download(context,release)),
                            WashiDialog.Action.secondary("暂不更新",null));
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

    public static void openReleasePage(Context context, String versionName) {
        Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yidoer/fanzha-classroom/releases/tag/app-v"+versionName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static void launchInstaller(Context context,File apk){Uri uri=FileProvider.getUriForFile(context,BuildConfig.APPLICATION_ID+".files",apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
    private static byte[] downloadManifest()throws Exception{return UpdateDownloadClient.downloadWithFallback(new String[]{BuildConfig.STORY_MANIFEST_URL,BuildConfig.STORY_FALLBACK_MANIFEST_URL},2*1024*1024,"application/json");}
    private static String installedCertificateSha256(Context context)throws Exception {
        PackageManager manager=context.getPackageManager();
        Signature[] signatures;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
            PackageInfo info=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
            signatures=info.signingInfo.hasMultipleSigners()?info.signingInfo.getApkContentsSigners():info.signingInfo.getSigningCertificateHistory();
        }else{
            signatures=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES).signatures;
        }
        if(signatures==null||signatures.length==0)throw new SecurityException("无法读取当前应用签名");
        return sha256(signatures[0].toByteArray());
    }
    private static String sha256(byte[] bytes)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
}