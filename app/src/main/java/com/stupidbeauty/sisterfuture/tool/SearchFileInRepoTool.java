package com.stupidbeauty.sisterfuture.tool;
import java.util.List;
public class SearchFileInRepoTool implements Tool{
@Override public String getName(){return"search_file_in_repo";}
@Override public String getDescription(){return"Search files in GitHub repo";}
@Override public List<String>getParameterNames(){return List.of();}
@Override public String callTool(List<String>args){return"";}
}