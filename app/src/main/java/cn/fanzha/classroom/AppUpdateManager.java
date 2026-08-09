package cn.fanzha.classroom;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppUpdateManager {
    public interface Callback { void onResult(Result result); }
    public static final class Result {
        public final boolean updateAvailable; public final String message, apkUrl, apkSha256, versionName;
        Result(boolean available, String message, String url, String sha, String versionName) {
            updateAvailable=available; this.message=message; apkUrl=url; apkSha256=sha; this.versionName=versionName;
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
                if(code<=BuildConfig.VERSION_CODE) result=new Result(false,"当前已是最新 App 版本 "+BuildConfig.VERSION_NAME,"","",BuildConfig.VERSION_NAME);
                else {
                    String name=manifest.getString("latestAppVersionName");
                    result=new Result(true,"发现 App "+name+"\n\n"+manifest.optString("appChangelog","包含功能与兼容性更新。"),manifest.getString("apkUrl"),manifest.getString("apkSha256"),name);
                }
            } catch(Exception e) { result=new Result(false,"检查失败，稍后可在更新中心重试","","",""); }
            Result finalResult=result; new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResult(finalResult));
        });
    }

    public static void download(Context context, Result release) {
        ProgressDialog progress=new ProgressDialog(context); progress.setTitle("下载 App "+release.versionName); progress.setMessage("正在从 GitHub Release 下载并校验…"); progress.setIndeterminate(true); progress.setCancelable(false); progress.show();
        EXECUTOR.execute(() -> {
            File apk=null; String error=null;
            try {
                byte[] bytes=downloadWithRetry(release.apkUrl);
                if(!sha256(bytes).equals(release.apkSha256.toLowerCase(Locale.ROOT)))throw new SecurityException("APK 校验失败");
                File dir=new File(context.getFilesDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("无法创建更新目录");
                apk=new File(dir,"fanzha-"+release.versionName+".apk");try(FileOutputStream out=new FileOutputStream(apk)){out.write(bytes);out.getFD().sync();}
            }catch(Exception e){error=e.getMessage()==null?"下载或校验失败":e.getMessage();}
            File finalApk=apk;String finalError=error;new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                progress.dismiss();if(finalApk==null)Toast.makeText(context,finalError+"，当前版本不受影响",Toast.LENGTH_LONG).show();else launchInstaller(context,finalApk);
            });
        });
    }

    private static void launchInstaller(Context context,File apk){Uri uri=FileProvider.getUriForFile(context,BuildConfig.APPLICATION_ID+".files",apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}
    private static byte[] downloadManifest()throws Exception{Exception first;try{return downloadWithRetry(BuildConfig.STORY_MANIFEST_URL);}catch(Exception e){first=e;}if(!BuildConfig.STORY_FALLBACK_MANIFEST_URL.trim().isEmpty())return downloadWithRetry(BuildConfig.STORY_FALLBACK_MANIFEST_URL);throw first;}
    private static byte[] downloadWithRetry(String url)throws Exception{if(url==null||url.trim().isEmpty())throw new IllegalStateException("尚未配置更新地址");Exception last=null;for(int i=0;i<4;i++){try{return download(url);}catch(Exception e){last=e;if(i<3)Thread.sleep((long)(700*Math.pow(2,i))+(long)(Math.random()*350));}}throw last;}
    private static byte[] download(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","FanZha-Classroom/"+BuildConfig.VERSION_NAME);int status=c.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("HTTP "+status);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0){out.write(b,0,n);if(out.size()>150*1024*1024)throw new IllegalArgumentException("安装包过大");}return out.toByteArray();}finally{c.disconnect();}}
    private static String sha256(byte[] bytes)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
}
