package moe.feng.nhentai.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.AppCompatTextView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;

import com.google.gson.Gson;

import moe.feng.nhentai.R;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.adapter.GalleryPagerAdapter;
import moe.feng.nhentai.ui.common.AbsActivity;
import moe.feng.nhentai.util.FullScreenHelper;
import moe.feng.nhentai.util.Utility;
import moe.feng.nhentai.util.task.PageDownloaderNew;

public class GalleryActivity extends AbsActivity {

	private static final String EXTRA_BOOK_DATA = "book_data", EXTRA_FISRT_PAGE = "first_page";
	private Book book;
	private int page_num;
	private ViewPager mPager;
	private GalleryPagerAdapter mPagerAdpater;
	private View mAppBar, mBottomBar;
	private AppCompatSeekBar mSeekBar;
	private AppCompatTextView mTotalPagesText;
	private FullScreenHelper mFullScreenHelper;
	private PageDownloaderNew mDownloader;
	private Fragment old;

	public static void launch(Activity activity, Book book, int firstPageNum) {
		Intent intent = new Intent(activity, GalleryActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		intent.putExtra(EXTRA_BOOK_DATA, book.toJSONString());
		intent.putExtra(EXTRA_FISRT_PAGE, firstPageNum);
		activity.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !Utility.isChrome()) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
			statusBarHeight = Utility.getStatusBarHeight(getApplicationContext());
		}

		if (Build.VERSION.SDK_INT >= 21) {
			getWindow().setStatusBarColor(Color.TRANSPARENT);
			getWindow().setNavigationBarColor(Color.TRANSPARENT);
		}

		mFullScreenHelper = new FullScreenHelper(this);
		// 别问我为什么这么干 让我先冷静一下→_→
		mFullScreenHelper.setFullScreen(true);
		mFullScreenHelper.setFullScreen(false);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		Intent intent = getIntent();
		book = new Gson().fromJson(intent.getStringExtra(EXTRA_BOOK_DATA), Book.class);
		page_num = intent.getIntExtra(EXTRA_FISRT_PAGE, 0);
		mDownloader = new PageDownloaderNew(getApplicationContext(), book);
		mDownloader.setCurrentPosition(page_num);
		mDownloader.setOnDownloadListener(new GalleryDownloaderListener());

		setContentView(R.layout.activity_gallery);
	}

	@Override
	public void onResume() {
		super.onResume();
		mDownloader.continueDownload();
	}

	@Override
	public void onPause() {
		super.onPause();
		mDownloader.pause();
	}

	@Override
	public void onStop() {
		super.onStop();
		mDownloader.stop();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.menu_gallery, menu);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_save) {
			if (mDownloader.isDownloaded(mPager.getCurrentItem())) {
				FileCacheManager m = FileCacheManager.getInstance(getApplicationContext());
				String externalTarget = m.getExternalPagePath(book, mPager.getCurrentItem() + 1);
				if (m.saveToExternalPath(book, mPager.getCurrentItem() + 1)){
					Snackbar.make($(R.id.space_layout), String.format(
							getString(R.string.action_save_succeed),
							externalTarget
					), Snackbar.LENGTH_SHORT).show();
					return true;
				}
				Snackbar.make($(R.id.space_layout), R.string.action_save_unknown, Snackbar.LENGTH_SHORT).show();
			} else {
				Snackbar.make($(R.id.space_layout), R.string.action_save_failed, Snackbar.LENGTH_SHORT).show();
			}
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void setUpViews() {
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setTitle(book.getAvailableTitle());

		mAppBar = $(R.id.my_app_bar);
		mBottomBar = $(R.id.bottom_bar);
		mPager = $(R.id.pager);
		mSeekBar = $(R.id.seekbar);
		mTotalPagesText = $(R.id.total_pages_text);
		mPagerAdpater = new GalleryPagerAdapter(getFragmentManager(), book);
		mPager.setAdapter(mPagerAdpater);
		mPager.setCurrentItem(page_num, false);
		mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}

			@Override
			public void onPageSelected(int position) {
				if (old != null) {
					old.onPause();
				}
				old = mPagerAdpater.getItem(position);
				mPagerAdpater.getItem(position).onResume();
				mSeekBar.setProgress(position);
				mDownloader.setCurrentPosition(position);
			}

			@Override
			public void onPageScrollStateChanged(int state) {

			}
		});

		mTotalPagesText.setText(String.format(getString(R.string.info_total_pages), book.pageCount));
		mSeekBar.setKeyProgressIncrement(1);
		mSeekBar.setMax(book.pageCount - 1);
		mSeekBar.setProgress(page_num);
		mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			int progress = 0;

			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				progress = i;
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				mPager.setCurrentItem(progress, false);
			}

		});
	}

	public void toggleControlBar() {
		if (mAppBar.getAlpha() != 0f) {
			ViewCompat.animate(mAppBar).alpha(0f).start();
			ViewCompat.animate(mBottomBar).alpha(0f).start();
			mFullScreenHelper.setFullScreen(true);
		} else if (mAppBar.getAlpha() != 1f) {
			ViewCompat.animate(mAppBar).alpha(1f).start();
			ViewCompat.animate(mBottomBar).alpha(1f).start();
			mFullScreenHelper.setFullScreen(false);
		}
	}

	@Override
	public void onBackPressed() {
		if (mAppBar.getAlpha() != 1f) {
			toggleControlBar();
		} else {
			super.onBackPressed();
		}
	}

	public PageDownloaderNew getPageDownloader() {
		return mDownloader;
	}

	private class GalleryDownloaderListener implements PageDownloaderNew.OnDownloadListener {

		@Override
		public void onFinish(int position, int progress) {
			if (mPagerAdpater != null) {
				mPagerAdpater.notifyPageImageLoaded(position, true);
				mSeekBar.setSecondaryProgress(position);
			}
		}

		@Override
		public void onError(int position, int errorCode) {
			if (mPagerAdpater != null) {
				mPagerAdpater.notifyPageImageLoaded(position, false);
			}
		}

		@Override
		public void onStateChange(int state, int progress) {

		}

	}

}
