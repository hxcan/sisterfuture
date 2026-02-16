package com.stupidbeauty.lanime.network.volley;


import com.android.volley.toolbox.HttpStack;
import com.squareup.okhttp.OkHttpClient;

public class GsonHttpManager
{
	public static GsonHttpManager mGsonHttpManager;
	private HttpStack httpStack; //!<The http stack object to be used.
	
	public GsonHttpManager()
	{
		
		
		OkHttpClient okHttpClt=new OkHttpClient(); //Create the ok http client.
		
		
		
		
		
		httpStack = new OkHttpStack(okHttpClt); //Use an OkHttpStack.
	}
	
	public static GsonHttpManager shareInstance()
	{
		if(mGsonHttpManager == null)
		{
			mGsonHttpManager = new GsonHttpManager();
		}
		return mGsonHttpManager;
	}
	
	public HttpStack getHttpStack()
	{
		return httpStack;
	}
}
