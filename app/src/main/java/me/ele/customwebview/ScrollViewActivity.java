package me.ele.customwebview;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by caoyubin on 15/2/11.
 */
public class ScrollViewActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrollview);

        LinearLayout scrollView = (LinearLayout) findViewById(R.id.scrollview);
        for (int i = 0; i < 20; i++) {
            TextView textView = new TextView(this);
            textView.setText("测试数据" + i);
            textView.setTextSize(20);
            textView.setPadding(20, 20, 10, 20);
            textView.setGravity(Gravity.CENTER);
            scrollView.addView(textView);
        }
    }


}