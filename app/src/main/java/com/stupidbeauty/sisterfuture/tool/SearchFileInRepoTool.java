package com.stupidbeauty.sisterfuture.tool;
import android.util.Log;
import java.util.List;
public class SearchFileInRepoTool implements Tool{
private static final String TAG="SearchFileInRepo";
private static final String API="https://api.github.com/search/code";
@Override public String getName(){return"search_file_in_repo";}
@Override public String getDescription(){return"Search files in GitHub repo";}
@Override public List<String>getParameterNames(){return List.of();}
@Override public String callTool(List<String>args){return"";}
}