package com.stupidbeauty.lanime.network.volley;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;

public class MapUtils 
{
	public static String toUrlGetString(Map<String, String> map) 
	{
		String ret = "";
		if (map != null) 
		{
			Set<String> keys = map.keySet();
			for (String key : keys) 
			{
				String value = map.get(key);
				if("".equals(value))
				{
				}
				else
				{
					try 
					{
						value = URLEncoder.encode(value, "utf-8");
						ret = ret + "&" + key +"="+value;
					}
					catch (UnsupportedEncodingException e) 
					{
						e.printStackTrace();
					}
				}
			}
		}

		return ret;
	}
}
