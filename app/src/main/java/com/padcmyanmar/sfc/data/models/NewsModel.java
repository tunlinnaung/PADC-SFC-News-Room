package com.padcmyanmar.sfc.data.models;

import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import com.padcmyanmar.sfc.SFCNewsApp;
import com.padcmyanmar.sfc.data.db.AppDatabase;
import com.padcmyanmar.sfc.data.vo.CommentActionVO;
import com.padcmyanmar.sfc.data.vo.FavoriteActionVO;
import com.padcmyanmar.sfc.data.vo.NewsVO;
import com.padcmyanmar.sfc.data.vo.SentToVO;
import com.padcmyanmar.sfc.events.RestApiEvents;
import com.padcmyanmar.sfc.network.MMNewsAPI;
import com.padcmyanmar.sfc.network.reponses.GetNewsResponse;
import com.padcmyanmar.sfc.utils.AppConstants;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by aung on 12/3/17.
 */

public class NewsModel extends ViewModel {

    private AppDatabase mAppDatabase;

    MMNewsAPI mmNewsAPI;

    private List<NewsVO> mNews;

    private int mmNewsPageIndex = 1;

    public NewsModel() {
        EventBus.getDefault().register(this);
        mNews = new ArrayList<>();
        initMMNewsAPI();
    }

    public void initDatabase(Context context) {
        mAppDatabase = AppDatabase.getNewsDatabase(context);
    }

    public MMNewsAPI getMMNewsApi() {
        return mmNewsAPI;
    }

    private void initMMNewsAPI() {
        final OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://padcmyanmar.com/padc-3/mm-news/apis/")
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(okHttpClient)
                .build();

        mmNewsAPI = retrofit.create(MMNewsAPI.class);
    }

//    public LiveData<List<NewsVO>> getNews() {
//        //return mAppDatabase.newsDao().getAllNews();
//    }

    public void startLoadingMMNews(final PublishSubject<List<NewsVO>> mNewsSubject) {
        //MMNewsDataAgentImpl.getInstance().loadMMNews(AppConstants.ACCESS_TOKEN, mmNewsPageIndex);

        Single<GetNewsResponse> getNewsResponseObservable = getMMNewsApi().loadMMNews(mmNewsPageIndex, AppConstants.ACCESS_TOKEN);

        getNewsResponseObservable
                .subscribeOn(Schedulers.io()) //run value creation code on a specific thread (non-UI thread)
                .map(new Function<GetNewsResponse, List<NewsVO>>() {
                    @Override
                    public List<NewsVO> apply(@NonNull GetNewsResponse getNewsResponse) {
                        return getNewsResponse.getNewsList();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread()) //observe the emitted value of the Observable on an appropriate thread
                .subscribeWith(new DisposableSingleObserver<List<NewsVO>>() {

                    @Override
                    public void onSuccess(List<NewsVO> newsVOs) {
                        Log.d(SFCNewsApp.LOG_TAG, "onSuccess: " + newsVOs.size());
                        mNewsSubject.onNext(newsVOs);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(SFCNewsApp.LOG_TAG, "onError: " + e.getMessage());
                    }
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        AppDatabase.destroyInstance();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onNewsDataLoaded(RestApiEvents.NewsDataLoadedEvent event) {
        mNews.addAll(event.getLoadNews());
        mmNewsPageIndex = event.getLoadedPageIndex() + 1;

        mAppDatabase.actedUserDao().deleteAll();
        mAppDatabase.commentActionDao().deleteAll();
        mAppDatabase.favoriteActionDao().deleteAll();
        mAppDatabase.sendToDao().deleteAll();
        mAppDatabase.newsInImageDao().deleteAll();
        mAppDatabase.publicationDao().deleteAll();
        mAppDatabase.newsDao().deleteAll();

        List<NewsVO> newsVOs = event.getLoadNews();
        for (NewsVO newsVO : newsVOs) {
            mAppDatabase.publicationDao().insertPublication(newsVO.getPublication());

            mAppDatabase.newsDao().insertNewsWithPubId(newsVO.getPublication().getPublicationId(), newsVO);

            mAppDatabase.newsInImageDao().insertImageWithNews(newsVO);

            if (newsVO.getCommentActions() != null) {
                for (CommentActionVO commentActionVO : newsVO.getCommentActions()) {
                    mAppDatabase.actedUserDao().insertActedUser(commentActionVO.getActedUser());
                    mAppDatabase.commentActionDao().insertCommentById(newsVO.getNewsId(),
                            commentActionVO.getActedUser().getUserId(),
                            commentActionVO);
                }
            }

            if (newsVO.getFavoriteActions() != null) {
                for (FavoriteActionVO favoriteActionVO : newsVO.getFavoriteActions()) {
                    mAppDatabase.actedUserDao().insertActedUser(favoriteActionVO.getActedUser());
                    mAppDatabase.favoriteActionDao().insertFavoriteById(newsVO.getNewsId(),
                            favoriteActionVO.getActedUser().getUserId(),
                            favoriteActionVO);
                }
            }

            if (newsVO.getSentToActions() != null) {
                for (SentToVO sentToVO : newsVO.getSentToActions()) {
                    mAppDatabase.actedUserDao().insertActedUser(sentToVO.getSender());
                    mAppDatabase.actedUserDao().insertActedUser(sentToVO.getReceiver());
                    mAppDatabase.sendToDao().insertSendToById(newsVO.getNewsId(),
                            sentToVO.getSender().getUserId(),
                            sentToVO.getReceiver().getUserId(),
                            sentToVO);
                }
            }

        }

    }
}
