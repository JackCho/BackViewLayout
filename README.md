
------

仿微信网页下拉显示支持信息，不止支持WebView，支持所有View.

------
支持下拉功能的开关；支持自定义back_view

```xml
  <me.ele.backviewlayout.BackViewLayout
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:id="@+id/swipe_refresh_widget"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:enabled="true"
        app:backViewLayout="@layout/back_view"
        android:background="#282b2d">

        <WebView
            android:id="@+id/webview"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </me.ele.backviewlayout.BackViewLayout>
```

详情见demo