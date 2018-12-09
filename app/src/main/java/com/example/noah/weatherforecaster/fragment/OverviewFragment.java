package com.example.noah.weatherforecaster.fragment;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.noah.weatherforecaster.R;
import com.example.noah.weatherforecaster.activity.DetailActivity;
import com.example.noah.weatherforecaster.activity.SettingActivity;
import com.example.noah.weatherforecaster.entity.CityEntity;
import com.example.noah.weatherforecaster.entity.WeatherEntity;
import com.example.noah.weatherforecaster.service.NotificationService;
import com.example.noah.weatherforecaster.utils.ForecastDBHelper;
import com.example.noah.weatherforecaster.utils.RIdManager;
import com.example.noah.weatherforecaster.utils.TimeUtils;
import com.example.noah.weatherforecaster.utils.WeatherInfoFetcher;

import java.util.Date;

public class OverviewFragment extends Fragment {
    private TextView dateToday; //今天的日期
    private TextView curTemToday; //当前温度
    private TextView rangeTemToday; //今天的最高最低温度
    private TextView location; //当前位置
    private TextView updateTime; //当前天气更新时间
    private TextView[] dateNext; //预报日期（月-日）数组
    private TextView[] weekNext; //预报日期（星期）数组
    private TextView[] minTemNext; //预报日期最低温度数组
    private TextView[] maxTemNext; //预报日期最高温度数组
    private TextView[] weatherText; //天气描述数组
    private ImageView[] weatherIcon; //天气图标数组
    private LinearLayout[] dayLayout; //各日期的主体Layout，用于点击进入详细信息视图

    private WeatherEntity today; //当前天气
    private WeatherEntity[] forecast; //预报信息
    private String curTempUnit; //当前温度单位，默认为摄氏
    private boolean notificationState; //通知是否开启

    //-------------------------异步请求类-------------------------
    private class FetchItemsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {//params变长；参数1必选，表示请求位置；参数2可选，表示目标温度单位
            String type = "摄氏";
            try {
                today = WeatherInfoFetcher.getToday(params[0]);
                forecast = WeatherInfoFetcher.getForecast(params[0]);
                curTempUnit = "摄氏";
                if (params.length > 1)
                    type = params[1];
            } catch (Exception e) {
                e.printStackTrace();
            }
            return type;
        }

        @Override
        protected void onPostExecute(String param) {
            super.onPostExecute(param);
            updateTempVal(param);
            updateWeatherInfo();
        }
    }

    //-------------------------生命周期函数-------------------------
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_overview, container, false);
        initView(v);
        loadState();
        updateWeatherInfo();
        requestLocatingPrivilege();
        updateLocationInfo();
        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        saveState();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_overview, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.map_location:
                launchMapApp();
                return true;
            case R.id.settings:
                Intent intent = new Intent(getContext(), SettingActivity.class);
                intent.putExtra("setLocation", new CityEntity(today.getLocation(), today.getLatitude(), today.getLongitude()));
                intent.putExtra("setUnit", curTempUnit);
                intent.putExtra("setNotification", notificationState);
                startActivityForResult(intent, SettingActivity.activityReqCode);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SettingActivity.activityReqCode || requestCode == DetailActivity.activityReqCode) {
            notificationState = data.getBooleanExtra("notification", true);
            Intent contentIntent = new Intent();
            contentIntent.putExtra("text", createNotificationStr());
            NotificationService.setServiceAlarm(getActivity(), notificationState, contentIntent);

            CityEntity curLocation  = (CityEntity) data.getSerializableExtra("curLocation");
            String unit = data.getStringExtra("unit");

            if (!curLocation.getLocation().equals(today.getLocation())) {//当设置的位置变化时，需要重新发送网络请求
                today.setLatitude(curLocation.getLatitude());
                today.setLongitude(curLocation.getLongitude());
                new FetchItemsTask().execute(curLocation.getLocation(), unit);
            }
            else {
                updateTempVal(unit);
                updateWeatherInfo();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == 111) {
            //处理位置信息授权结果
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                updateLocationInfo();
            else {
                Toast.makeText(getContext(), "权限不足，无法拉取天气信息，请打开位置权限后再试", Toast.LENGTH_LONG).show();
                getActivity().finish();
            }
        }
    }

    //-------------------------其他函数-------------------------

    /**
     * 初始化私有成员
     * @param v 当前fragment的View对象
     */
    private void initView(View v) {
        View.OnClickListener layoutClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int clickerId = 0;
                for (int i = 0; i < 7; i++)
                    if (RIdManager.getRes("id", "day" + i) == v.getId()) {
                        clickerId = i;
                        break;
                    }

                //带参数启动承载详细视图的Activity
                Intent intent = new Intent(getContext(), DetailActivity.class);
                intent.putExtra("detail", forecast[clickerId]);
                intent.putExtra("unit", curTempUnit);
                intent.putExtra("notification", notificationState);
                startActivityForResult(intent, DetailActivity.activityReqCode);
            }
        };//Layout的点击监听器，用于启动详细视图Fragment

        dateNext = new TextView[7];
        weekNext = new TextView[7];
        minTemNext = new TextView[7];
        maxTemNext = new TextView[7];
        weatherText = new TextView[7];
        weatherIcon = new ImageView[7];
        dayLayout = new LinearLayout[7];

        dateToday = v.findViewById(R.id.date_t);
        curTemToday = v.findViewById(R.id.cur_t);
        rangeTemToday = v.findViewById(R.id.ran_t);
        location = v.findViewById(R.id.location);
        updateTime = v.findViewById(R.id.update_time);
        for (int i = 0; i < 7; i++) {
            if (i != 0) {
                dateNext[i] = v.findViewById(RIdManager.getRes("id", "md" + i));
                weekNext[i] = v.findViewById(RIdManager.getRes("id", "week" + i));
                minTemNext[i] = v.findViewById(RIdManager.getRes("id", "min_t" + i));
                maxTemNext[i] = v.findViewById(RIdManager.getRes("id", "max_t" + i));
            }
            weatherIcon[i] = v.findViewById(RIdManager.getRes("id", "w_icon" + i));
            weatherText[i] = v.findViewById(RIdManager.getRes("id", "w_text" + i));
            dayLayout[i] = v.findViewById(RIdManager.getRes("id", "day" + i));
            dayLayout[i].setOnClickListener(layoutClickListener);
        }
    }

    /**
     * 刷新天气信息
     */
    private void updateWeatherInfo() {
        //设置今日日期
        String dateTodayStr = TimeUtils.mdFromDate(forecast[0].getDate()) + TimeUtils.weekFromDate(forecast[0].getDate());
        dateToday.setText(dateTodayStr);

        //设置当前温度
        String curTemTodayStr = " " + today.getCurrentDegree() + "°";
        curTemToday.setText(curTemTodayStr);

        //设置当前的位置
        location.setText(today.getLocation());

        //设置更新时间
        String updateTimeStr = "   更新时间: " + TimeUtils.hmFromDate(today.getDate());
        updateTime.setText(updateTimeStr);

        //设置今天的最高最低温度
        String rangeTemTodayStr = forecast[0].getMaxDegree() + "°/" + forecast[0].getMinDegree() + "°";
        rangeTemToday.setText(rangeTemTodayStr);

        //设置实时天气图标、天气描述
        weatherIcon[0].setImageResource(RIdManager.getRes("drawable", "i" + today.getWeatherCode()));
        weatherText[0].setText(today.getWeatherName());

        //设置预报信息
        String minTem, maxTem;
        for (int i = 1; i < 7; i++) {
            minTem = forecast[i].getMinDegree() + "°";
            maxTem = forecast[i].getMaxDegree() + "°";
            dateNext[i].setText(TimeUtils.mdFromDate(forecast[i].getDate()));
            weekNext[i].setText(TimeUtils.weekFromDate(forecast[i].getDate()));
            minTemNext[i].setText(minTem);
            maxTemNext[i].setText(maxTem);
            weatherIcon[i].setImageResource(RIdManager.getRes("drawable", "i" + forecast[i].getWeatherCode()));
            weatherText[i].setText(forecast[i].getWeatherName());
        }
    }

    /**
     * 温度单位转换
     * @param toType 目标类型 ("摄氏"/"华氏")
     * @param value 原始温度
     * @return 转化结果
     */
    private int transformTemperature(String toType, int value) {
        int res = 0;
        if (toType.equals("摄氏"))
            res = Double.valueOf((value - 32) / 1.8).intValue();
        if (toType.equals("华氏"))
            res = Double.valueOf(value * 1.8 + 32).intValue();
        return res;
    }

    /**
     * 更新温度单位
     * @param toType 目标类型 ("摄氏"/"华氏")
     */
    private void updateTempVal(String toType) {
        if (toType.equals(curTempUnit))
            return;
        curTempUnit = toType;
        today.setCurrentDegree(transformTemperature(toType, today.getCurrentDegree()));
        for (int i = 0; i < 7; i++) {
            forecast[i].setMaxDegree(transformTemperature(toType, forecast[i].getMaxDegree()));
            forecast[i].setMinDegree(transformTemperature(toType, forecast[i].getMinDegree()));
        }
    }

    /**
     * 请求定位权限
     */
    private void requestLocatingPrivilege() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    111);
        }
    }

    /**
     * 获取当前位置，更新today中的经纬度
     */
    private void updateLocationInfo() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        final LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        //Network Listener
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 8, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.i("updateLocationInfo", "Changed");
                new FetchItemsTask().execute(location.getLongitude() + "," + location.getLatitude());
                locationManager.removeUpdates(this);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }
            @Override
            public void onProviderEnabled(String provider) {

            }
            @Override
            public void onProviderDisabled(String provider) {

            }
        });
    }

    /**
     * 使用隐式Intent启动高德地图
     */
    private void launchMapApp() {
        String uri = "androidamap://viewMap?sourceApplication=appname"
                + "&poiname=" + today.getLocation()
                + "&lat=" + today.getLatitude()
                + "&lon=" + today.getLongitude()
                + "&dev=1";
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setPackage("com.autonavi.minimap");
        intent.setData(Uri.parse(uri));
        Log.i("launchMapApp", "start");
        startActivity(intent);
    }

    /**
     * 创建通知消息字符串
     * @return 拼接后的消息字符串
     */
    private String createNotificationStr() {
        return today.getLocation()  + ": " + today.getWeatherName() + "    " + forecast[0].getMaxDegree() + "°/" + forecast[0].getMinDegree() + "°";
    }

    /**
     * 启动应用时，调用该方法，从SharedPreferences和数据库中取得应用的部分状态
     */
    private void loadState() {
        SharedPreferences pref = getActivity().getSharedPreferences("state_data", Context.MODE_PRIVATE);

        //加载设置信息
        curTempUnit = pref.getString("curTempUnit", "摄氏");//缺省值为摄氏
        notificationState = pref.getBoolean("notificationState", true);//缺省值为真

        //加载当前天气信息
        today = new WeatherEntity();
        today.setLocation(pref.getString("location", "北京"));
        today.setLatitude(Double.valueOf(pref.getString("latitude", "39.90498734")));
        today.setLongitude(Double.valueOf(pref.getString("longitude", "116.40528870")));
        today.setWeatherCode(pref.getInt("weatherCode", 101));
        today.setWeatherName(pref.getString("weatherName", "多云"));
        today.setCurrentDegree(pref.getInt("currentDegree", 14));
        today.setDate(new Date(pref.getLong("updateTime", System.currentTimeMillis())));

        //从数据库中加载预报信息
        ForecastDBHelper dbHelper = new ForecastDBHelper(getContext(), "ForecastInfo.db", null, 1);
        forecast = dbHelper.loadSavedForecastInfo();
    }

    /**
     * 关闭应用时，调用该方法，利用SharedPreferences和数据库保存当前应用的一些状态
     */
    private void saveState() {
        SharedPreferences.Editor editor = getActivity().getSharedPreferences("state_data", Context.MODE_PRIVATE).edit();

        //保存设置状态
        editor.putBoolean("notificationState", notificationState);
        editor.putString("curTempUnit", curTempUnit);

        //保存today对象中的信息
        editor.putString("location", today.getLocation());
        editor.putString("latitude", String.valueOf(today.getLatitude()));
        editor.putString("longitude", String.valueOf(today.getLongitude()));
        editor.putInt("weatherCode", today.getWeatherCode());
        editor.putString("weatherName", today.getWeatherName());
        editor.putInt("currentDegree", today.getCurrentDegree());
        editor.putLong("updateTime", today.getDate().getTime());
        editor.apply();

        //将预报信息存于数据库中
        ForecastDBHelper dbHelper = new ForecastDBHelper(getContext(), "ForecastInfo.db", null, 1);
        dbHelper.updateForecastInfo(forecast);
    }
}
