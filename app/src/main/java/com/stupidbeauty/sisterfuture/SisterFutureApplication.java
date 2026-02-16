package com.stupidbeauty.sisterfuture;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import androidx.multidex.MultiDex;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;

/**
 * 应用程序对象。
 * @author root 蔡火胜。
 *
 */
public class SisterFutureApplication extends Application
{
	@SuppressLint("StaticFieldLeak")
	private static Context mContext;
	
	@Override
	protected void attachBaseContext(Context base)
	{
    super.attachBaseContext(base);

    MultiDex.install(this); //启动MultiDex.
	}

	@Override
	/*
	  程序被创建。
	 */
	public void onCreate() 
	{
		super.onCreate(); //创建超类。
		mContext = getApplicationContext(); //获取应用程序上下文。 
	} //public void onCreate()

	/**
	 * 获取应用程序上下文。
	 * @return 应用程序上下文。
	 */
	public static Context getAppContext() 
	{ 
		return mContext; 
	}  //public static Context getAppContext()
	
  public static void handleQuestion(Context context, String question) {
      // 启动主Activity并传递问题
      Intent intent = new Intent(context, SisterFutureActivity.class);
      intent.putExtra("question", question);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intent);
  }

} //public class SisterFutureApplication extends Application

