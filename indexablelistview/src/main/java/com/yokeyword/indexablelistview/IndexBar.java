package com.yokeyword.indexablelistview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.yokeyword.indexablelistview.help.PinyinUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * 字母索引
 * Created by YoKeyword on 2016/3/20.
 */
public class IndexBar extends View {
    private static final int MSG_SEARCH = 1;

    private OnIndexTitleSelectedListener mOnIndexSelectedListener;
    private OnSearchResultListener mOnSearchResultListener;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ArrayList<String> mIndex = new ArrayList<>();   // List:索引字母

    private int mIndexHeight; // 每个索引的高度

    private int mSelectionPos;   // 记录当前选中位置
    private int mCurrentScrollPos;   // 记录当前ListView滚动位置

    private ListView mListView;
    private IndexBarAdapter mAdapter;
    private List<IndexEntity> mItems;
    private List<IndexEntity> mFilterList;

    private int mScrollState = -1;    // 记录ListView滚动状态

    private TextView mOverlayView;  // 中心:悬浮 显示索引的View
    private TextView mRightOverlayView; // 右侧:悬浮 显示索引的View
    private int dp80, mMinHeight;

    private HandlerThread mSearchHandlerThread;
    private Handler mSearchHandler;
    private boolean mNeedShutdown;

    public IndexBar(Context context, int barTextColor, int barSelectedColor, float barTextSize) {
        super(context);
        init(context, barTextColor, barSelectedColor, barTextSize);
    }

    private void init(Context context, int barTextColor, int barSelectedColor, float barTextSize) {
        paint.setColor(barTextColor);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(barTextSize);

        focusPaint.setTextAlign(Paint.Align.CENTER);
        focusPaint.setTextSize(barTextSize + 1);
        focusPaint.setColor(barSelectedColor);

        dp80 = dp2px(context, 80);
        mMinHeight = dp2px(context, 32);
    }

    /**
     * 根据Y坐标判断 位置
     */
    private int positionForPoint(float y) {
        int position = (int) (y / mIndexHeight);

        if (position < 0) {
            position = 0;
        } else if (position > mIndex.size() - 1) {
            position = mIndex.size() - 1;
        }

        return position;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(widthMeasureSpec);

        if (mode == MeasureSpec.AT_MOST) {
            int maxWidth = dp2px(getContext(), 25);
            super.onMeasure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mIndex.size() == 0) return;
        mIndexHeight = getHeight() / mIndex.size();
        if (mIndexHeight > mMinHeight) {
            mIndexHeight = mMinHeight;
        }

        for (int i = 0; i < mIndex.size(); i++) {
            if (mSelectionPos == i) {
                canvas.drawText(mIndex.get(i), getWidth() / 2, mIndexHeight / 2 + mIndexHeight * i, focusPaint);
            } else {
                canvas.drawText(mIndex.get(i), getWidth() / 2, mIndexHeight / 2 + mIndexHeight * i, paint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = event.getY();
        int touchPos = positionForPoint(y);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                processRightOverlayView(y);
                if (touchPos != mSelectionPos) {
                    mSelectionPos = touchPos;
                }

                if (mListView != null) {
                    if (mListView.getLastVisiblePosition() == mListView.getCount() - 1) {
                        invalidate();
                    }
                    mListView.setSelection(mAdapter.getIndexMapPosition(mSelectionPos) + mListView.getHeaderViewsCount());
                }
                processOverlayView(touchPos, y);
                break;
            case MotionEvent.ACTION_MOVE:
                processRightOverlayView(y);
                if (touchPos != mSelectionPos) {
                    mSelectionPos = touchPos;

                    if (mListView != null) {
                        if (mListView.getLastVisiblePosition() == mListView.getCount() - 1) {
                            invalidate();
                        }
                        mListView.setSelection(mAdapter.getIndexMapPosition(mSelectionPos) + mListView.getHeaderViewsCount());
                    }
                    processOverlayView(touchPos, y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mOverlayView != null) mOverlayView.setVisibility(GONE);
                if (mRightOverlayView != null) mRightOverlayView.setVisibility(GONE);
                break;
        }

        return true;
    }

    private void processRightOverlayView(float y) {
        if (mRightOverlayView != null) {
            if (y - dp80 > 0) {
                mRightOverlayView.setY(y - dp80);
            } else {
                mRightOverlayView.setY(0);
            }
        }
    }

    private void processOverlayView(final int touchPos, float y) {
        if (mRightOverlayView != null) {
            if (mRightOverlayView.getVisibility() != VISIBLE) {
                mRightOverlayView.setVisibility(VISIBLE);
            }
            mRightOverlayView.setText(mIndex.get(touchPos));
        }
        if (mOverlayView != null) {
            if (mOverlayView.getVisibility() != VISIBLE) {
                mOverlayView.setVisibility(VISIBLE);
            }
            mOverlayView.setText(mIndex.get(touchPos));
        }
        if (mOnIndexSelectedListener != null) {
            mListView.post(new Runnable() {
                @Override
                public void run() {
                    int position = mListView.getFirstVisiblePosition();
                    String realIndexTitle = mAdapter.getItemTitle(position);
                    mOnIndexSelectedListener.onSelection(touchPos, realIndexTitle);
                }
            });
        }
    }

    private String getIndexTitle(int position) {
        if (mAdapter == null) return "";
        int size = mAdapter.getHeaderSize();

        if (position >= size) {
            return mIndex.get(position);
        } else {
            SparseArray<String> map = mAdapter.getTitleMap();
            return map.get(map.keyAt(position));
        }
    }

    void setListView(ListView indexListView) {
        mListView = indexListView;

        ListAdapter listAdapter = mListView.getAdapter();
        if (listAdapter instanceof IndexBarAdapter) {
            mAdapter = (IndexBarAdapter) mListView.getAdapter();
        } else if (listAdapter instanceof HeaderViewListAdapter) {
            ListAdapter adapter = ((HeaderViewListAdapter) listAdapter).getWrappedAdapter();
            if (adapter instanceof IndexBarAdapter) {
                mAdapter = (IndexBarAdapter) adapter;
            } else {
                throw new ClassCastException("Your Adapter must extends IndexBarAdapter!");
            }
        } else {
            throw new ClassCastException("Your Adapter must extends IndexBarAdapter!");
        }

        mItems = mAdapter.getItems();

        SparseArray<String> indexMap = mAdapter.getTitleMap();
        List<String> headerIndexs = mAdapter.getHeaderIndexs();
        if (headerIndexs != null && headerIndexs.size() > 0) {
            for (String header : headerIndexs) {
                mIndex.add(header);
            }
        }

        for (int i = headerIndexs.size(); i < indexMap.size(); i++) {
            mIndex.add(indexMap.get(indexMap.keyAt(i)));
        }
    }

    void searchTextChange(String key) {
        mNeedShutdown = true;

        if (mSearchHandlerThread == null) {
            mSearchHandlerThread = new HandlerThread("Search_Thread");
            mSearchHandlerThread.start();
            // 后续维护,这里有可能产生持续到搜索结束时的内存泄漏 (话说没人有这么大数据量吧...)
            mSearchHandler = new Handler(mSearchHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (mItems == null || mAdapter == null) return;

                    if (mOnSearchResultListener != null) {
                        mOnSearchResultListener.onStart();
                    }

                    String currentKey = (String) msg.obj;

                    mNeedShutdown = false;

                    if (mFilterList == null) {
                        mFilterList = new ArrayList<>();
                    }

                    mFilterList.clear();

                    if (TextUtils.isEmpty(currentKey.trim())) {
                        runOnUIThread(true);
                    } else {
                        for (IndexEntity tmp : mItems) {
                            if (mNeedShutdown) {
                                return;
                            }

                            String name = tmp.getName();
                            if (name.contains(currentKey) || PinyinUtil.getPingYin(name).startsWith(currentKey)) {
                                mFilterList.add(tmp);
                            }
                        }
                        HashSet<IndexEntity> hashSet = new HashSet(mFilterList);
                        mFilterList.clear();
                        mFilterList.addAll(hashSet);

                        runOnUIThread(false);
                    }
                }
            };
        }
        Message msg = Message.obtain();
        msg.obj = key;
        msg.what = MSG_SEARCH;
        mSearchHandler.sendMessage(msg);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mSearchHandlerThread != null) {
            mSearchHandlerThread.quit();
        }
        super.onDetachedFromWindow();
    }

    void runOnUIThread(final boolean show) {
        if (getContext() instanceof Activity) {
            ((Activity) getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mNeedShutdown) {
                        return;
                    }
                    if (show) {
                        setVisibility(VISIBLE);
                        mAdapter.setFilterDatas(null);
                    } else {
                        setVisibility(GONE);
                        mAdapter.setFilterDatas(mFilterList);

                    }
                    if (mOnSearchResultListener != null) {
                        mOnSearchResultListener.onResult(!show, mFilterList.size());
                    }
                }
            });
        }
    }

    void setOnSearchResultListener(OnSearchResultListener listener) {
        mOnSearchResultListener = listener;
    }

    interface OnSearchResultListener {
        void onStart();

        void onResult(boolean isSearch, int dataSize);
    }

    void setOverlayView(TextView overlay) {
        mOverlayView = overlay;
    }

    void showTouchOverlayView(TextView overlay) {
        mRightOverlayView = overlay;
    }

    void onListViewScrollStateChanged(int scrollState) {
        if (scrollState == 0) mScrollState = -1;
        else mScrollState = scrollState;
    }

    void onListViewScroll(int firstVisibleItem) {
        if (mAdapter == null) return;

        if (getVisibility() == GONE) return;

        if (firstVisibleItem == 0) {
            if (mCurrentScrollPos != firstVisibleItem) {
                initInvalidate(firstVisibleItem, firstVisibleItem);
            }
            return;
        }

        if (mCurrentScrollPos != firstVisibleItem) {
            String first;
            if (mListView != null) {
                if (firstVisibleItem <= mListView.getHeaderViewsCount()) {
                    first = mAdapter.getItemFirstSpell(0);
                } else {
                    first = mAdapter.getItemFirstSpell(firstVisibleItem - mListView.getHeaderViewsCount());
                }
            } else {
                first = mAdapter.getItemFirstSpell(firstVisibleItem);
            }
            for (int i = 0; i < mIndex.size(); i++) {
                if (first.equals(mIndex.get(i))) {
                    initInvalidate(firstVisibleItem, i);
                    return;
                }
            }
        }
    }

    private void initInvalidate(int firstVisibleItem, int i) {
        mCurrentScrollPos = firstVisibleItem;
        if (mScrollState != -1) {
            mSelectionPos = i;

            String indexTitle = getIndexTitle(i);
            mOnIndexSelectedListener.onSelection(mSelectionPos, indexTitle);
        }

        invalidate();
    }

    public void setOnIndexSelectedListener(OnIndexTitleSelectedListener listener) {
        this.mOnIndexSelectedListener = listener;
    }


    public static int dp2px(Context context, float spValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    interface OnIndexTitleSelectedListener {
        void onSelection(int position, String indexTitle);
    }
}
