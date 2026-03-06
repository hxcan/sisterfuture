package com.stupidbeauty.sisterfuture.tool;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
public class SearchFileInRepoTool implements Tool{
private static final String TAG="SearchFileInRepo";
private static final String API="https://api.github.com/search/code";
@Override public String getName(){return"search_file_in_repo";}
@Override public String getDescription(){return"Search files via GitHub Code Search API";}
@Override public List<String>getParameterNames(){return List.of("owner","repo","fileNamePattern","pathPattern","branch","limit");}
@Override public String callTool(List<String>args)throws Exception{
if(args.size()<3)throw new IllegalArgumentException("Need:owner,repo,pattern");
String o=args.get(0),r=args.get(1),p=args.get(2);
String path=args.size()>3?args.get(3):"";
String branch=args.size()>4?args.get(4):"master";
int lim=args.size()>5?Integer.parseInt(args.get(5)):10;
String q="filename:"+p+(path.isEmpty()?"":" path:"+path.replaceFirst("^/",""));
URL u=new URL(API+"?q="+java.net.URLEncoder.encode(q,"UTF-8")+"&per_page="+lim);
HttpURLConnection c=(HttpURLConnection)u.openConnection();
String tk=getToolRemark("github_token");
c.setRequestProperty("Authorization","token "+(tk!=null?tk:""));
c.setRequestProperty("User-Agent","SisterFuture");
BufferedReader rd=new BufferedReader(new InputStreamReader(c.getInputStream()));
StringBuilder sb=new StringBuilder();String ln;
while((ln=rd.readLine())!=null)sb.append(ln);
rd.close();c.disconnect();
JSONObject j=new JSONObject(sb.toString());
JSONArray it=j.getJSONArray("items");
List<String>res=new ArrayList<>();
for(int i=0;i<Math.min(it.length(),lim);i++){
JSONObject x=it.getJSONObject(i);
res.add(x.getString("name")+"|"+x.getString("path"));
}
return String.join("\n",res);
}
}