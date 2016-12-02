package com.liyu.suzhoubus.ui.weather;

import android.Manifest;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.liyu.suzhoubus.R;
import com.liyu.suzhoubus.http.ApiFactory;
import com.liyu.suzhoubus.http.BaseWeatherResponse;
import com.liyu.suzhoubus.model.HeWeather5;
import com.liyu.suzhoubus.ui.MainActivity;
import com.liyu.suzhoubus.ui.base.BaseContentFragment;
import com.liyu.suzhoubus.ui.weather.adapter.WeatherAdapter;
import com.liyu.suzhoubus.utils.ACache;
import com.liyu.suzhoubus.utils.SettingsUtil;
import com.liyu.suzhoubus.utils.ShareUtils;
import com.liyu.suzhoubus.utils.TimeUtils;
import com.liyu.suzhoubus.utils.WeatherUtil;
import com.tbruyelle.rxpermissions.RxPermissions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by liyu on 2016/10/31.
 */

public class WeatherFragment extends BaseContentFragment {

    private static final String CACHE_WEAHTHER_NAME = "weather_cache";

    private Toolbar mToolbar;
    private TextView tvCityName;
    private TextView tvNowWeatherString;
    private TextView tvNowTemp;
    private TextView tvUpdateTime;
    private TextView tvAqi;

    private RecyclerView recyclerView;
    private WeatherAdapter adapter;

    private ACache mCache;

    private HeWeather5 currentWeather;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_weather;
    }

    @Override
    protected void initViews() {
        super.initViews();
        mCache = ACache.get(getActivity());
        mToolbar = findView(R.id.toolbar);
        mToolbar.setTitle("苏州天气");
        ((MainActivity) getActivity()).initDrawer(mToolbar);
        mToolbar.inflateMenu(R.menu.menu_weather);
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_share) {
                    new RxPermissions(getActivity()).request(Manifest.permission.WRITE_EXTERNAL_STORAGE).subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean result) {
                            if (result) {
                                shareWeather();
                            }
                        }
                    });
                    return true;
                }
                return false;
            }
        });
        tvCityName = findView(R.id.tv_city_name);
        tvNowWeatherString = findView(R.id.tv_weather_string);
        tvNowTemp = findView(R.id.tv_temp);
        tvCityName.setText("苏州");
        tvUpdateTime = findView(R.id.tv_update_time);
        tvAqi = findView(R.id.tv_weather_aqi);


        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView = findView(R.id.rv_weather);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new WeatherAdapter(null);
        adapter.openLoadAnimation();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void lazyFetchData() {
        showRefreshing(true);
        HeWeather5 cacheWeather = (HeWeather5) mCache.getAsObject(CACHE_WEAHTHER_NAME);
        if (cacheWeather != null) {
            showWeather(cacheWeather);
            showRefreshing(false);
            return;
        }

        ApiFactory
                .getWeatherController()
                .getWeather()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BaseWeatherResponse<HeWeather5>>() {
                    @Override
                    public void onCompleted() {
                        showRefreshing(false);
                    }

                    @Override
                    public void onError(Throwable e) {
                        showRefreshing(false);
                        Snackbar.make(getView(), "获取天气失败!", Snackbar.LENGTH_INDEFINITE).setAction("重试", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                lazyFetchData();
                            }
                        }).setActionTextColor(getActivity().getResources().getColor(R.color.actionColor)).show();
                    }

                    @Override
                    public void onNext(BaseWeatherResponse<HeWeather5> listBaseWeatherResponse) {
                        if (listBaseWeatherResponse == null || listBaseWeatherResponse.HeWeather5.size() == 0) {
                            return;
                        }
                        showWeather(listBaseWeatherResponse.HeWeather5.get(0));
                        mCache.put(CACHE_WEAHTHER_NAME, listBaseWeatherResponse.HeWeather5.get(0), 10 * 60);
                        WeatherUtil.saveDailyHistory(listBaseWeatherResponse.HeWeather5.get(0));
                    }
                });
    }

    private void showWeather(HeWeather5 weather) {
        if (weather == null || !weather.getStatus().equals("ok")) {
            return;
        }
        currentWeather = weather;
        tvNowWeatherString.setText(weather.getNow().getCond().getTxt());
        tvAqi.setText(weather.getAqi() == null ? "" : weather.getAqi().getCity().getQlty());
        tvNowTemp.setText(String.format("%s℃", weather.getNow().getTmp()));
        String updateTime = TimeUtils.string2String(weather.getBasic().getUpdate().getLoc(), new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()), new SimpleDateFormat("HH:mm", Locale.getDefault()));
        tvUpdateTime.setText(String.format("截止 %s", updateTime));
        List<MultiItemEntity> weather5s = new ArrayList<>();
        HeWeather5 nowWeather = (HeWeather5) weather.clone();
        nowWeather.setItemType(HeWeather5.TYPE_NOW);
        weather5s.add(nowWeather);
        weather5s.add(weather.getSuggestion());
        HeWeather5 dailyWeather = (HeWeather5) weather.clone();
        dailyWeather.setItemType(HeWeather5.TYPE_DAILYFORECAST);
        weather5s.add(dailyWeather);
        adapter.setNewData(weather5s);
    }

    private void shareWeather() {
        if (currentWeather == null)
            return;
        String shareType = SettingsUtil.getWeatherShareType();
        if (shareType.equals("纯文本"))
            ShareUtils.shareText(getActivity(), WeatherUtil.getShareMessage(currentWeather));
        else if (shareType.equals("仿锤子便签"))
            ShareActivity.start(getActivity(), WeatherUtil.getShareMessage(currentWeather));
    }

}