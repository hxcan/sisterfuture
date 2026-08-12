package com.stupidbeauty.dynamicwallpaper.service;

// import com.stupidbeauty.hxlauncher.service.DownloadNotificationService;
// import com.stupidbeauty.farmingbookapp.PreferenceManagerUtil;
import com.stupidbeauty.hxlauncher.scanner.AppInfoScanner;
// import com.stupidbeauty.comgooglewidevinesoftwaredrmremover.app.LanImeUncaughtExceptionHandler;
// import com.stupidbeauty.grebe.DownloadRequestor;
// import com.andexert.library.RippleView;
// import com.stupidbeauty.hxlauncher.manager.PackageInformationManager;
// import com.stupidbeauty.hxlauncher.manager.NotificationControlManager;
import com.stupidbeauty.hxlauncher.interfaces.ShutDownAt2100LogicInterface;
// import com.stupidbeauty.hxlauncher.asynctask.LoadBuiltinVoicePackageNameMapTask;
// import com.stupidbeauty.hxlauncher.asynctask.BuildActivityLabelPackageItemInfoMapTask;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.content.SharedPreferences;
import android.util.Log;
// import com.mikhaellopez.circularprogressbar.CircularProgressBar;
// import com.stupidbeauty.voiceui.VoiceUi;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.widget.TextView;
import android.widget.Toast;
// import com.stupidbeauty.hxlauncher.asynctask.BuildInternationalizationDataPackageNameMapTask;
// import com.stupidbeauty.hxlauncher.activity.ApplicationInformationActivity;
// import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import android.app.usage.UsageStats;
import android.content.pm.ApplicationInfo;
// import com.stupidbeauty.grebe.DownloadRequestor;
// import com.stupidbeauty.hxlauncher.application.HxLauncherApplication;
// import com.stupidbeauty.hxlauncher.asynctask.VoiceAssociationDataSendTask;
// import com.stupidbeauty.hxlauncher.asynctask.VoiceShortcutAssociationDataSendTask;
import com.stupidbeauty.hxlauncher.bean.ApplicationNameInternationalizationData;
import com.stupidbeauty.sisterfuture.R;
// import com.stupidbeauty.voiceui.VoiceUi;
import android.content.pm.PackageInfo;
// import com.stupidbeauty.voiceui.VoiceUi;
import android.content.pm.PackageInfo;
// import com.stupidbeauty.hxlauncher.application.HxLauncherApplication;
// import com.stupidbeauty.hxlauncher.asynctask.VoiceAssociationDataSendTask;
// import com.stupidbeauty.hxlauncher.asynctask.VoiceShortcutAssociationDataSendTask;
import com.stupidbeauty.hxlauncher.bean.ApplicationNameInternationalizationData;
import com.stupidbeauty.hxlauncher.bean.ApplicationNamePair;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import butterknife.BindView;
import com.stupidbeauty.hxlauncher.interfaces.ShutDownAt2100LogicInterface;
// import com.stupidbeauty.hxlauncher.asynctask.LoadBuiltinVoicePackageNameMapTask;
// import com.stupidbeauty.hxlauncher.asynctask.BuildActivityLabelPackageItemInfoMapTask;
// import com.stupidbeauty.hxlauncher.asynctask.LoadBuiltinShortcutsTask;
// import com.stupidbeauty.hxlauncher.asynctask.LoadPreferenceTask;
import com.stupidbeauty.hxlauncher.logic.ShutDownAt2100Logic;
// import com.android.volley.RequestQueue;
// import com.android.volley.Response;
// import com.android.volley.VolleyError;
// import com.google.protobuf.InvalidProtocolBufferException;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import android.graphics.Matrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import com.bumptech.glide.Glide;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import java.util.List;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.KeyEvent;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import androidx.annotation.Nullable;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import com.bumptech.glide.Glide;
import java.util.Random;
import android.util.Log;
import android.view.KeyEvent;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import com.bumptech.glide.Glide;
import java.util.Random;
import android.widget.ImageView;
import java.util.Random;
import android.widget.ImageView;
import android.view.SurfaceHolder;
import android.app.WallpaperManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;

public class MyLiveWallpaperService extends WallpaperService implements ShutDownAt2100LogicInterface
{
  // @BindView(R.id.launchRipple) RippleView launchRipple; //!<用于转场动画的视图对象
  // @BindView(R.id.circularProgressBar) CircularProgressBar circularProgressBar; //!< The circular progrewss bar.
  @BindView(R.id.applicationIconrightimageView2) ImageView applicationIconrightimageView2; //!<应用程序图标
  @BindView(R.id.rightTextoperationMethodactTitletextView2) TextView rightTextoperationMethodactTitletextView2; //!<应用程序名字
    // private final DownloadRequestor downloadRequestor = new DownloadRequestor();
  private static final String TAG="MyLiveWallpaperService"; //!< 输出调试信息时使用的标记。

  private static final String PIN_PREF_NAME = "dynamic_wallpaper"; //!< 与 SisterFutureActivity 共享
  private static final String PIN_PREF_KEY = "wallpaper_pinned"; //!< 钉住状态的 key

      @Override
    /**
    * Request to download and install package apk.
    */
    public boolean requestDownloadApk(String foundPackageName)
    {
      boolean foundUrl=false;

      return foundUrl;
    } // public boolean requestDownloadApk(String shutDownAt2100PackageName)


  @Override
  public Engine onCreateEngine()
  {
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "=== onCreateEngine START ===");
    MyEngine engine = new MyEngine();
    ((SisterFutureApplication) getApplication()).setMyEngine(engine);
    return engine;
  }

  private static List<Uri> queryImages(Context context)
  {
    List<Uri> imageUris = new ArrayList<>();

    String[] projection =
    {
      MediaStore.Images.Media._ID,
      MediaStore.Images.Media.DISPLAY_NAME,
      MediaStore.Images.Media.DATA,
      MediaStore.Images.Media.DATE_ADDED
    };

    String selection = MediaStore.Images.Media.MIME_TYPE + " = ? OR " +
                      MediaStore.Images.Media.MIME_TYPE + " = ? OR " +
                      MediaStore.Images.Media.MIME_TYPE + " = ?";

    String[] selectionArgs = {"image/jpeg", "image/png", "image/webp"};

    String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

    try (Cursor cursor = context.getContentResolver().query(
      MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
      projection,
      selection,
      selectionArgs,
      sortOrder))
      {
        if (cursor != null && cursor.moveToFirst())
        {
          do
          {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
            Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            imageUris.add(uri);
          }
          while (cursor.moveToNext());
        }
      }
      catch (Exception e)
      {
        Log.e("Wallpaper", "Error querying images: " + e.getMessage(), e);
      }

      Log.i("Wallpaper", "Found " + imageUris.size() + " images");
      return imageUris;
  }


  /**
   * 从候选列表中选择一张尺寸合格的壁纸
   * @param imageUris 候选图片 URI 列表
   * @return 选中的图片 URI，如果都太小则返回 null
   */
  private Uri selectValidWallpaper(List<Uri> imageUris) {
    if (imageUris == null || imageUris.isEmpty()) {
      return null;
    }
    
    // 写死阈值：短边 >= 540，长边 >= 1200
    final int MIN_SHORT_EDGE = 540;
    final int MIN_LONG_EDGE = 1200;
    
    // 打乱顺序，随机选择
    List<Uri> shuffled = new ArrayList<>(imageUris);
    java.util.Collections.shuffle(shuffled);
    
    for (Uri uri : shuffled) {
      int[] size = getImageSize(uri);
      if (size != null) {
        int width = size[0];
        int height = size[1];
        int shortEdge = Math.min(width, height);
        int longEdge = Math.max(width, height);
        
        // 短边 >= 540 且 长边 >= 1200
        if (shortEdge >= MIN_SHORT_EDGE && longEdge >= MIN_LONG_EDGE) {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Wallpaper", "Selected valid wallpaper: " + uri + ", size: " + width + "x" + height);
          return uri;
        } else {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Wallpaper", "Skipped small image: " + uri + ", size: " + width + "x" + height + ", shortEdge: " + shortEdge + ", longEdge: " + longEdge);
        }
      }
    }
    
    // 所有图片都太小，返回最大的那一张（不做过滤）
    com.stupidbeauty.dynamicwallpaper.utils.FileLogger.w("Wallpaper", "All images too small, using largest one");
    return findLargestImage(imageUris);
  }
  
  /**
   * 获取图片尺寸
   */
  private int[] getImageSize(Uri uri) {
    try {
      ParcelFileDescriptor pfd = MyLiveWallpaperService.this.getContentResolver().openFileDescriptor(uri, "r");
      if (pfd != null) {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);
        pfd.close();
        return new int[] { options.outWidth, options.outHeight };
      }
    } catch (Exception e) {
      Log.e("Wallpaper", "Error getting image size: " + e.getMessage());
    }
    return null;
  }
  
  /**
   * 找到尺寸最大的图片
   */
  private Uri findLargestImage(List<Uri> imageUris) {
    Uri largest = null;
    int maxSize = 0;
    
    for (Uri uri : imageUris) {
      int[] size = getImageSize(uri);
      if (size != null) {
        int area = size[0] * size[1];
        if (area > maxSize) {
          maxSize = area;
          largest = uri;
        }
      }
    }
    
    return largest;
  }

  // ✅ 移除 static
  public class MyEngine extends Engine
  {
    private Bitmap wallpaperBitmap;
    private long lastReloadTime = 0; // 上次 reloadWallpaper 的时间（防抖用）
    private boolean reloadInProgress = false; // 是否正在加载中（防抖用）
    private long lastDebugLogTime = 0; // 上次输出 [FALLBACK_DEBUG] 的时间（节流用）
    private long reloadStartTime = 0; // reloadWallpaper 开始时间（超时重置用）
    private final Handler handler = new Handler();
    private ShutDownAt2100Logic shutDownAt2100Logic;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rectF = new RectF(0, 0, 100, 100);

    private final Runnable autoRefreshRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        // 🔴 钉住壁纸判断（新增）：如果钉住，跳过换图
        SharedPreferences pinPrefs = getSharedPreferences(PIN_PREF_NAME, MODE_PRIVATE);
        boolean isPinned = pinPrefs.getBoolean(PIN_PREF_KEY, false);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[AUTO_REFRESH] isPinned=" + isPinned);
        if (isPinned) {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[PINNED] 钉住中，跳过自动换图");
          // 继续循环（不取消 autoRefreshRunnable），但不换图
        } else {
          // ✅ 无论是否可见，都换图
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] autoRefreshRunnable calling reloadWallpaper(false)");
          reloadWallpaper(false);
        }

        //31分钟 = 31 * 60 * 1000 毫秒
        handler.postDelayed(this, 31 * 60 * 1000);
      }
    };

    @Override
    public void onCreate(SurfaceHolder surfaceHolder)
    {
        super.onCreate(surfaceHolder);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] Engine.onCreate super called");
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.init(MyLiveWallpaperService.this);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "=== Engine.onCreate END ===");
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] Engine.onCreate setTouchEventsEnabled done");
        setTouchEventsEnabled(true);

        // ✅ 初始化关机逻辑
        shutDownAt2100Logic = new ShutDownAt2100Logic(MyLiveWallpaperService.this);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] Engine.onCreate init ShutDownAt2100Logic done");

        // ✅ 启动自动换图（延迟31分钟）
        handler.postDelayed(autoRefreshRunnable, 31 * 60 * 1000);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] Engine.onCreate scheduled autoRefreshRunnable in 31min");
    }

    @Override
    public void onVisibilityChanged(boolean visible)
    {
      super.onVisibilityChanged(visible);
      com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "onVisibilityChanged: visible=" + visible);
      if (visible)
      {
        handler.post(drawRunnable);
      }
      else
      {
        handler.removeCallbacks(drawRunnable);
      }
    }

    @Override
    public void onDestroy()
    {
      super.onDestroy();
      handler.removeCallbacks(autoRefreshRunnable);
      handler.removeCallbacks(drawRunnable);
    }

    public void reloadWallpaper(boolean restoreFromSaved)
    {
      com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "=== reloadWallpaper START, restoreFromSaved=" + restoreFromSaved + " ===");
      reloadStartTime = System.currentTimeMillis(); // 记录开始时间
      
      // 🔍 子任务-4 优化：第一时间检查缓存（在 handler.post 之前，避免等 queryImages）
      if (restoreFromSaved) {
        try {
          SharedPreferences prefsEarly = getSharedPreferences("dynamic_wallpaper", MODE_PRIVATE);
          String savedUriEarly = prefsEarly.getString("current_wallpaper_uri", null);
          if (savedUriEarly != null) {
            com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[CACHE_CHECK_EARLY] savedUri=" + savedUriEarly);
            final java.io.File cacheDirEarly = new java.io.File(MyLiveWallpaperService.this.getCacheDir(), "wallpapers");
            if (!cacheDirEarly.exists()) cacheDirEarly.mkdirs();
            final String fileNameEarly = String.valueOf(savedUriEarly.hashCode()) + ".jpg";
            final java.io.File cacheFileEarly = new java.io.File(cacheDirEarly, fileNameEarly);
            if (cacheFileEarly.exists()) {
              com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[CACHE_HIT_EARLY] " + cacheFileEarly.getAbsolutePath() + " size=" + cacheFileEarly.length());
              final android.net.Uri cachedUriEarly = android.net.Uri.fromFile(cacheFileEarly);
              lastReloadTime = System.currentTimeMillis();
              reloadInProgress = true;
              Glide.with(MyLiveWallpaperService.this)
                .asBitmap()
                .load(cachedUriEarly)
                .centerCrop()
                .into(new SimpleTarget<Bitmap>()
                {
                  @Override
                  public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition)
                  {
                    wallpaperBitmap = resource;
                    com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[CACHE_LOADED_EARLY] from cache, size=" + resource.getWidth() + "x" + resource.getHeight());
                    reloadInProgress = false;
                    handler.post(drawRunnable);
                  }

                  @Override
                  public void onLoadFailed(@Nullable Drawable errorDrawable)
                  {
                    com.stupidbeauty.dynamicwallpaper.utils.FileLogger.e("Wallpaper", "[CACHE_LOAD_FAILED_EARLY]");
                    reloadInProgress = false;
                  }
                });
              return; // 缓存命中并已启动加载，不再走下面的 queryImages 流程
            } else {
              com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[CACHE_MISS_EARLY] " + cacheFileEarly.getAbsolutePath());
            }
          }
        } catch (Exception e) {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.e("Wallpaper", "[CACHE_CHECK_EARLY_ERROR] " + e.getMessage());
        }
      }
      
      handler.post(() ->
      {
        List<Uri> imageUris = MyLiveWallpaperService.queryImages(MyLiveWallpaperService.this);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Wallpaper", "reloadWallpaper: restoreFromSaved=" + restoreFromSaved + ", imageUris.size=" + imageUris.size());

        if (imageUris.isEmpty())
        {
          Log.w("Wallpaper", "No images found in media store");
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.w("Wallpaper", "No images found in media store");
          return;
        }

        // ✅ 过滤小尺寸图片，选择尺寸合格的壁纸
        Uri randomUri = selectValidWallpaper(imageUris);
        if (randomUri == null) {
            Log.w("Wallpaper", "No valid wallpaper found (all images too small)");
            com.stupidbeauty.dynamicwallpaper.utils.FileLogger.w("Wallpaper", "No valid wallpaper found (all images too small)");
            return;
        }

        // ✅ 优先使用上次保存的 URI（开机恢复）
        SharedPreferences prefs = getSharedPreferences("dynamic_wallpaper", MODE_PRIVATE);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Wallpaper", "[DEBUG] prefs object created, all keys=" + prefs.getAll().keySet());
        String savedUri = prefs.getString("current_wallpaper_uri", null);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Wallpaper", "savedUri=" + savedUri);
        if (restoreFromSaved && savedUri != null) {
            randomUri = Uri.parse(savedUri);
        }
        final android.net.Uri finalRandomUriForCache = randomUri; // final 副本，避免内部类访问问题

        // 保存当前壁纸 URI 到 SharedPreferences，供主界面读取保持一致
        // ✅ 只在用户主动切换时保存新 URI，开机恢复时不保存
        if (!restoreFromSaved) {
            prefs.edit().putString("current_wallpaper_uri", randomUri.toString()).apply();
        }


        // 发送广播通知主界面更新预览
        Intent refreshIntent = new Intent("com.stupidbeauty.dynamicwallpaper.WALLPAPER_CHANGED");
        refreshIntent.putExtra("wallpaper_uri", randomUri.toString());
        sendBroadcast(refreshIntent);

        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] About to Glide.load URI=" + randomUri + ", restoreFromSaved=" + restoreFromSaved);

        // 🔍 早期缓存检查（reloadWallpaper 开头）已覆盖缓存读取
        // 这里直接加载 randomUri（用户切换的新图片）
        Glide.with(MyLiveWallpaperService.this)
          .asBitmap()
          .load(randomUri)
          .centerCrop()
          .into(new SimpleTarget<Bitmap>()
          {
            @Override
            public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition)
            {
              wallpaperBitmap = resource;
              com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] onResourceReady: bitmap=" + (resource != null ? "NOT_NULL(" + resource.getWidth() + "x" + resource.getHeight() + ", recycled=" + resource.isRecycled() + ")" : "NULL"));
              com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "onResourceReady: bitmap loaded, posting drawRunnable");
              reloadInProgress = false; // 加载完成，重置防抖标志
              handler.post(drawRunnable);
              
              // 🔍 子任务-4：保存到本地缓存（异步，不阻塞 UI）
              MyLiveWallpaperService.saveBitmapToCache(MyLiveWallpaperService.this, finalRandomUriForCache, resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable)
            {
              Log.e("Wallpaper", "Failed to load wallpaper image");
            com.stupidbeauty.dynamicwallpaper.utils.FileLogger.e("Wallpaper", "[DEBUG] onLoadFailed: errorDrawable=" + (errorDrawable != null ? errorDrawable.toString() : "NULL") + ", restoreFromSaved=" + restoreFromSaved);
            com.stupidbeauty.dynamicwallpaper.utils.FileLogger.e("Wallpaper", "Failed to load wallpaper image");
            reloadInProgress = false; // 加载失败也重置，避免卡住
            }
          });

        // ✅ 触发 21点关机逻辑
        if (shutDownAt2100Logic != null)
        {
          shutDownAt2100Logic.checkShutDownTime();
        }
      });
    }

    @Override
    public void onSurfaceCreated(SurfaceHolder holder)
    {
      super.onSurfaceCreated(holder);
      com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "onSurfaceCreated called");
      com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[DEBUG] onSurfaceCreated calling reloadWallpaper(true)");
      reloadWallpaper(true);
    }

    @Override
    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
      super.onSurfaceChanged(holder, format, width, height);
      rectF.set(0, 0, width, height);
    }

    @Override
    public void onSurfaceDestroyed(SurfaceHolder holder)
    {
      super.onSurfaceDestroyed(holder);
      handler.removeCallbacks(drawRunnable);
    }

    private Matrix calculateScaleMatrix(int bitmapWidth, int bitmapHeight, RectF targetRect)
    {
      float bitmapRatio = (float) bitmapWidth / bitmapHeight;
      float screenRatio = targetRect.width() / targetRect.height();

      Matrix matrix = new Matrix();

      if (bitmapRatio > screenRatio)
      {
        // 图片更宽 → 高度填满，宽度居中裁剪
        float scale = targetRect.height() / (float) bitmapHeight;
        float dx = (targetRect.width() - bitmapWidth * scale) / 2;
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, 0);
      }
      else
      {
        // 图片更高 → 宽度填满，高度居中裁剪
        float scale = targetRect.width() / (float) bitmapWidth;
        float dy = (targetRect.height() - bitmapHeight * scale) / 2;
        matrix.setScale(scale, scale);
        matrix.postTranslate(0, dy);
      }

      return matrix;
    }

    private final Runnable drawRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Draw", "[DEBUG] drawRunnable.run start");
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Draw", "drawRunnable.run, bitmap=" + (wallpaperBitmap != null ? "set" : "NULL"));
        SurfaceHolder holder = getSurfaceHolder();
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Draw", "[DEBUG] drawRunnable surfaceValid=" + (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) + ", bitmap=" + (wallpaperBitmap != null ? "set(" + wallpaperBitmap.getWidth() + "x" + wallpaperBitmap.getHeight() + ", recycled=" + wallpaperBitmap.isRecycled() + ")" : "NULL"));
        // 🔍 兜底：如果 bitmap 为空且 surface 有效，尝试恢复上次壁纸
        long now = System.currentTimeMillis();
        // 🔍 超时检测：如果 reloadInProgress 超过 5 秒，强制重置
        if (reloadInProgress && reloadStartTime > 0 && (now - reloadStartTime) > 5000) {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.w("Wallpaper", "[FALLBACK_TIMEOUT] reloadInProgress stuck for " + (now - reloadStartTime) + "ms, forcing reset");
          reloadInProgress = false;
          reloadStartTime = 0;
        }
        boolean cond_bitmapNull = (wallpaperBitmap == null);
        boolean cond_surfaceValid = (holder != null && holder.getSurface() != null && holder.getSurface().isValid());
        boolean cond_notInProgress = !reloadInProgress;
        boolean cond_timeOk = ((now - lastReloadTime) > 1000);
        boolean allOk = cond_bitmapNull && cond_surfaceValid && cond_notInProgress && cond_timeOk;
        // 🔍 节流：每 1 秒输出一次条件状态
        if ((now - lastDebugLogTime) > 1000) {
          lastDebugLogTime = now;
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.d("Wallpaper", "[FALLBACK_DEBUG] bitmapNull=" + cond_bitmapNull + ", surfaceValid=" + cond_surfaceValid + ", notInProgress=" + cond_notInProgress + ", timeOk=" + cond_timeOk + ", lastReloadTime=" + lastReloadTime + ", allOk=" + allOk);
        }
        if (allOk) {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[FALLBACK] drawRunnable detected bitmap=NULL, calling reloadWallpaper(true)");
          lastReloadTime = now;
          reloadInProgress = true;
          reloadWallpaper(true);
        }
        if (holder.getSurface().isValid())
        {
          Canvas canvas = holder.lockCanvas();
          if (canvas != null)
          {
            try
            {
              if (wallpaperBitmap != null)
              {
                // ✅ 智能裁剪
                Matrix matrix = calculateScaleMatrix
                (
                  wallpaperBitmap.getWidth(),
                  wallpaperBitmap.getHeight(),
                  rectF
                );
                // 🔍 抗锯齿处理：使用 Paint 设置 ANTI_ALIAS_FLAG 和 FILTER_BITMAP_FLAG
                Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(wallpaperBitmap, matrix, drawPaint);
              }
              else
              {
                  canvas.drawColor(Color.BLACK);
              }
            }
            finally
            {
              holder.unlockCanvasAndPost(canvas);
            }
          }
        }
        handler.postDelayed(this, 1000 / 60);
      }
    };

    @Override
    public void onTouchEvent(MotionEvent event)
    {
      super.onTouchEvent(event);
    }
  };

  public static void saveBitmapToCache(final android.content.Context context, final android.net.Uri uri, final Bitmap bitmap) {
    try {
      final java.io.File cacheDir = new java.io.File(context.getCacheDir(), "wallpapers");
      if (!cacheDir.exists()) cacheDir.mkdirs();
      final String fileName = String.valueOf(uri.toString().hashCode()) + ".jpg";
      final java.io.File cacheFile = new java.io.File(cacheDir, fileName);
      new Thread(() -> {
        try {
          java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
          bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
          fos.close();
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("Wallpaper", "[CACHE_SAVED] " + cacheFile.getAbsolutePath() + " size=" + cacheFile.length());
        } catch (Exception e) {
          com.stupidbeauty.dynamicwallpaper.utils.FileLogger.e("Wallpaper", "[CACHE_SAVE_FAILED] " + e.getMessage());
        }
      }).start();
    } catch (Exception e) {
      com.stupidbeauty.dynamicwallpaper.utils.FileLogger.e("Wallpaper", "[CACHE_SAVE_ERROR] " + e.getMessage());
    }
  }
}