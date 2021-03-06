package io.github.ryanhoo.music.ui.playlist;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.github.ryanhoo.music.R;
import io.github.ryanhoo.music.RxBus;
import io.github.ryanhoo.music.data.model.PlayList;
import io.github.ryanhoo.music.data.source.AppRepository;
import io.github.ryanhoo.music.event.FavoriteChangeEvent;
import io.github.ryanhoo.music.event.PlayListCreatedEvent;
import io.github.ryanhoo.music.event.PlayListNowEvent;
import io.github.ryanhoo.music.event.PlayListUpdatedEvent;
import io.github.ryanhoo.music.ui.base.BaseFragment;
import io.github.ryanhoo.music.ui.base.adapter.OnItemClickListener;
import io.github.ryanhoo.music.ui.common.DefaultDividerDecoration;
import io.github.ryanhoo.music.ui.details.PlayListDetailsActivity;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created with Android Studio.
 * User: ryan.hoo.j@gmail.com
 * Date: 9/1/16
 * Time: 9:58 PM
 * Desc: PlayListFragment
 */
public class PlayListFragment extends BaseFragment implements PlayListContract.View,
        EditPlayListDialogFragment.Callback, PlayListAdapter.AddPlayListCallback {

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;

    private PlayListAdapter mAdapter;
    private int mEditIndex, mDeleteIndex;

    PlayListContract.Presenter mPresenter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_play_list, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);

        mAdapter = new PlayListAdapter(getActivity(), null);
        mAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                //进入文件夹详情页面
                PlayList playList = mAdapter.getItem(position);
                startActivity(PlayListDetailsActivity.launchIntentForPlayList(getActivity(), playList));
            }
        });
        //设置添加新List以及更多操作的回调
        mAdapter.setAddPlayListCallback(this);
        recyclerView.setAdapter(mAdapter);
        recyclerView.addItemDecoration(new DefaultDividerDecoration());
        //一开始就加载PlayList
        new PlayListPresenter(AppRepository.getInstance(), this).subscribe();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPresenter.unsubscribe();
    }

    // RxBus Events

    @Override
    protected Subscription subscribeEvents() {
        return RxBus.getInstance().toObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        if (o instanceof PlayListCreatedEvent) {
                            onPlayListCreatedEvent((PlayListCreatedEvent) o);
                        } else if (o instanceof FavoriteChangeEvent) {
                            onFavoriteChangeEvent((FavoriteChangeEvent) o);
                        } else if (o instanceof PlayListUpdatedEvent) {
                            onPlayListUpdatedEvent((PlayListUpdatedEvent) o);
                        }
                    }
                })
                .subscribe(RxBus.defaultSubscriber());
    }

    private void onPlayListCreatedEvent(PlayListCreatedEvent event) {
        mAdapter.getData().add(event.playList);
        mAdapter.notifyDataSetChanged();
        mAdapter.updateFooterView();
    }

    private void onFavoriteChangeEvent(FavoriteChangeEvent event) {
        // Update entire play lists
        mPresenter.loadPlayLists();
       /*
        Song song = event.song;
        List<PlayList> playLists = mAdapter.getData();
        if (playLists != null && playLists.size() > 1) {
            PlayList favorite = playLists.get(0);
            if (!favorite.isFavorite()) {
                // Find the favorite play list
                for (PlayList list : playLists) {
                    if (list.isFavorite()) {
                        favorite = list;
                        break;
                    }
                }
            }
            int index;
            if ((index = favorite.getSongs().indexOf(song)) != -1) {
                favorite.getSongs().remove(index);
            }
            favorite.addSong(song);
            mAdapter.notifyDataSetChanged();
        }
        */
    }

    public void onPlayListUpdatedEvent(PlayListUpdatedEvent event) {
        mPresenter.loadPlayLists();
    }

    // Adapter Callbacks，PlayListAdapter.AddPlayListCallback回调,点击ItemView右边的button按钮进行操作

    @Override
    public void onAction(View actionView, final int position) {
        final PlayList playList = mAdapter.getItem(position);
        PopupMenu actionMenu = new PopupMenu(getActivity(), actionView, Gravity.END | Gravity.BOTTOM);
        actionMenu.inflate(R.menu.play_list_action);
        //假如是favourite这个item，则只显示一个play操作选项
        if (playList.isFavorite()) {
            actionMenu.getMenu().findItem(R.id.menu_item_rename).setVisible(false);
            actionMenu.getMenu().findItem(R.id.menu_item_delete).setVisible(false);
        }
        actionMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //播放音乐，事件分发到MusicPlayerFragment进行处理
                if (item.getItemId() == R.id.menu_item_play_now) {
                    PlayListNowEvent playListNowEvent = new PlayListNowEvent(playList, 0);
                    RxBus.getInstance().post(playListNowEvent);
                } else if (item.getItemId() == R.id.menu_item_rename) {
                    //重命名，通过回调来进行操作
                    mEditIndex = position;
                    EditPlayListDialogFragment.editPlayList(playList)
                            .setCallback(PlayListFragment.this)
                            .show(getFragmentManager().beginTransaction(), "EditPlayList");
                } else if (item.getItemId() == R.id.menu_item_delete) {
                    mDeleteIndex = position;
                    mPresenter.deletePlayList(playList);
                }
                return true;
            }
        });
        actionMenu.show();
    }
    //PlayListAdapter.AddPlayListCallback回调,添加PlayList
    @Override
    public void onAddPlayList() {
        EditPlayListDialogFragment.createPlayList()
                .setCallback(PlayListFragment.this)
                .show(getFragmentManager().beginTransaction(), "CreatePlayList");
    }

    // Create or Edit Play List Callbacks，
    // EditPlayListDialogFragment.Callback,用来创建新的PlayList和修改PlayList的名字

    @Override
    public void onCreated(final PlayList playList) {
        mPresenter.createPlayList(playList);
    }

    @Override
    public void onEdited(final PlayList playList) {
        mPresenter.editPlayList(playList);
    }


    // MVP View，通过PlayListPresenter进行对数据的操作，然后针对成功或者失败的情况，
    // 在PlayListPresenter中调用PlayListFragment中方法来更新View

    @Override
    public void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void handleError(Throwable error) {
        Toast.makeText(getActivity(), error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPlayListsLoaded(List<PlayList> playLists) {
        mAdapter.setData(playLists);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPlayListCreated(PlayList playList) {
        mAdapter.getData().add(playList);
        mAdapter.notifyItemInserted(mAdapter.getData().size() - 1);
        mAdapter.updateFooterView();
    }

    @Override
    public void onPlayListEdited(PlayList playList) {
        mAdapter.getData().set(mEditIndex, playList);
        mAdapter.notifyItemChanged(mEditIndex);
        mAdapter.updateFooterView();
    }

    @Override
    public void onPlayListDeleted(PlayList playList) {
        mAdapter.getData().remove(mDeleteIndex);
        mAdapter.notifyItemRemoved(mDeleteIndex);
        mAdapter.updateFooterView();
    }

    @Override
    public void setPresenter(PlayListContract.Presenter presenter) {
        mPresenter = presenter;
    }
}
