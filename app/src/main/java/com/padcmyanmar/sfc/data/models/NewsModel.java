package com.padcmyanmar.sfc.data.models;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;

import com.padcmyanmar.sfc.data.db.AppDatabase;
import com.padcmyanmar.sfc.data.vo.CommentActionVO;
import com.padcmyanmar.sfc.data.vo.FavoriteActionVO;
import com.padcmyanmar.sfc.data.vo.NewsVO;
import com.padcmyanmar.sfc.data.vo.SentToVO;
import com.padcmyanmar.sfc.events.RestApiEvents;
import com.padcmyanmar.sfc.network.MMNewsDataAgentImpl;
import com.padcmyanmar.sfc.utils.AppConstants;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

/**
 * Created by aung on 12/3/17.
 */

public class NewsModel extends ViewModel {

    private AppDatabase mAppDatabase;

    private LiveData<List<NewsVO>> mNews;

    private int mmNewsPageIndex = 1;

    public NewsModel() {
        EventBus.getDefault().register(this);
    }

    public void initDatabase(Context context) {
        mAppDatabase = AppDatabase.getNewsDatabase(context);
    }

    public LiveData<List<NewsVO>> getNews() {
        return mAppDatabase.newsDao().getAllNews();
    }

    public void startLoadingMMNews() {
        MMNewsDataAgentImpl.getInstance().loadMMNews(AppConstants.ACCESS_TOKEN, mmNewsPageIndex);
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
        //mNews.addAll(event.getLoadNews());
        //mmNewsPageIndex = event.getLoadedPageIndex() + 1;

        mAppDatabase.actedUserDao().deleteAll();
        mAppDatabase.commentActionDao().deleteAll();
        mAppDatabase.favoriteActionDao().deleteAll();
        mAppDatabase.sendToDao().deleteAll();
        mAppDatabase.newsInImageDao().deleteAll();
        mAppDatabase.publicationDao().deleteAll();
        mAppDatabase.newsDao().deleteAll();

        List<NewsVO> newsVOs = event.getLoadNews();
        for (NewsVO newsVO : newsVOs) {
            mAppDatabase.newsInImageDao().insertImageWithNews(newsVO);
            mAppDatabase.publicationDao().insertPublication(newsVO.getPublication());

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

            mAppDatabase.newsDao().insertNewsWithPubId(newsVO.getPublication().getPublicationId(), newsVO);

        }

    }
}
