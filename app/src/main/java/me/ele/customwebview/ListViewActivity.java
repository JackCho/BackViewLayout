package me.ele.customwebview;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by caoyubin on 15/2/11.
 */
public class ListViewActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listview);

        ListView listView = (ListView) findViewById(R.id.listview);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, getData()));
    }

    private List<String> getData() {

        List<String> data = new ArrayList<String>();
        for (int i = 0; i < 20; i++) {
            data.add("测试数据" + i);
        }
        return data;
    }

}