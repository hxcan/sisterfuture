package com.stupidbeauty.sisterfuture.bean;

// 在StringListConverter.java顶部添加
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import butterknife.OnClick;
import com.iflytek.cloud.SpeechRecognizer;
import io.objectbox.converter.PropertyConverter;
import java.util.List;
import java.util.Arrays;


// 新增StringListConverter.java
public class StringListConverter implements PropertyConverter<List<String>, String> {
    @Override
    public List<String> convertToEntityProperty(String databaseValue) {
        if (databaseValue == null || databaseValue.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = new JSONArray(databaseValue);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
            return list;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public String convertToDatabaseValue(List<String> entityProperty) {
        if (entityProperty == null) {
            return "[]";
        }
            JSONArray array = new JSONArray(entityProperty);
            return array.toString();
    }
}
