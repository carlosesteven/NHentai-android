package moe.feng.nhentai.ui.fragment;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.AppCompatTextView;
import android.view.View;
import android.widget.ImageView;

import com.google.gson.Gson;
import com.squareup.picasso.Callback;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.api.common.NHentaiUrl;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.GalleryActivity;
import moe.feng.nhentai.ui.common.LazyFragment;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.PicassoCache;
import moe.feng.nhentai.view.WheelProgressView;
import uk.co.senab.photoview.PhotoViewAttacher;

import static moe.feng.nhentai.cache.common.Constants.CACHE_PAGE_IMG;

public class BookPageFragment extends LazyFragment {

	public static final String TAG = BookPageFragment.class.getSimpleName();
	public static final int MSG_FINISHED_LOADING = 1, MSG_ERROR_LOADING = 2;
	private static final String ARG_BOOK_DATA = "arg_book_data", ARG_PAGE_NUM = "arg_page_num";
	private Book book;
	private int pageNum;
	private ImageView mImageView;
	private PhotoViewAttacher mPhotoViewAttacher;
	private AppCompatTextView mPageNumText, mTipsText;
	private WheelProgressView mWheelProgress;
	private Bitmap mBitmap;
	private Callback onimageload;

	public static BookPageFragment newInstance(Book book, int pageNum) {
		BookPageFragment fragment = new BookPageFragment();
		Bundle data = new Bundle();
		data.putString(ARG_BOOK_DATA, book.toJSONString());
		data.putInt(ARG_PAGE_NUM, pageNum);
		fragment.setArguments(data);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle data = getArguments();
		book = new Gson().fromJson(data.getString(ARG_BOOK_DATA), Book.class);
		pageNum = data.getInt(ARG_PAGE_NUM);
		onimageload = new Callback() {

			@Override
			public void onSuccess() {
				if (mPhotoViewAttacher != null) {
					mPhotoViewAttacher.update();
					mPhotoViewAttacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
						@Override
						public void onViewTap(View view, float v, float v1) {
							((GalleryActivity) getActivity()).toggleControlBar();
						}
					});
				} else {
					mPhotoViewAttacher = new PhotoViewAttacher(mImageView);
					mPhotoViewAttacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
						@Override
						public void onViewTap(View view, float v, float v1) {
							((GalleryActivity) getActivity()).toggleControlBar();
						}
					});
				}
			}

			@Override
			public void onError() {
				// TODO Auto-generated method stub

			}
		};
		setHandler(new MyHandler());
	}

	@Override
	public int getLayoutResId() {
		return R.layout.fragment_book_page;
	}

	@Override
	public void finishCreateView(Bundle state) {
		mImageView = $(R.id.image_view);
		mPhotoViewAttacher = new PhotoViewAttacher(mImageView);
		mPageNumText = $(R.id.page_number);
		mTipsText = $(R.id.little_tips);
		mWheelProgress = $(R.id.wheel_progress);

		mPageNumText.setText(Integer.toString(pageNum));

		$(R.id.background_view).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (getActivity() instanceof GalleryActivity) {
					((GalleryActivity) getActivity()).toggleControlBar();
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();

		$(R.id.loading_content).setVisibility(View.VISIBLE);
		mWheelProgress.setVisibility(View.VISIBLE);
		mWheelProgress.spin();

		try {
			if (PageApi.isPageOriginImageLocalFileExist(getApplicationContext(), book, pageNum)) {
				//new DownloadTask().execute();
				String url = NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(pageNum));
				PicassoCache.getPicassoInstance(getApplicationContext()).load(FileCacheManager.getInstance(getApplicationContext()).getCachedImage(CACHE_PAGE_IMG, url)).into(mImageView, onimageload);
			} else {
				PicassoCache.getPicassoInstance(getApplicationContext()).load(Uri.parse(NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(pageNum)))).into(mImageView, onimageload);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		mImageView.setImageBitmap(null);
		try {
			((BitmapDrawable) mImageView.getDrawable()).getBitmap().recycle();
		} catch (Exception e) {

		}
		System.gc();
	}

	private class DownloadTask extends AsyncTask<Void, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(Void... params) {
			return PageApi.getPageOriginImage(getApplicationContext(), book, pageNum);
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);

			if (result != null) {
				$(R.id.loading_content).setVisibility(View.GONE);
				mBitmap = result;
				mImageView.setImageBitmap(mBitmap);
				mPhotoViewAttacher.update();
				mPhotoViewAttacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
					@Override
					public void onViewTap(View view, float v, float v1) {
						((GalleryActivity) getActivity()).toggleControlBar();
					}
				});
			}
		}

	}

	private class MyHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_FINISHED_LOADING:
					if (PageApi.isPageOriginImageLocalFileExist(getApplicationContext(), book, pageNum)) {
						if (mImageView != null) {
							String url = NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(pageNum));
							PicassoCache.getPicassoInstance(getApplicationContext()).load(FileCacheManager.getInstance(getApplicationContext()).getCachedImage(CACHE_PAGE_IMG, url)).into(mImageView, onimageload);
						}
					} else {
						/*if (getActivity() != null && getActivity() instanceof GalleryActivity) {
							PageDownloader downloader = ((GalleryActivity) getActivity()).getPageDownloader();
							if (downloader != null) {
								downloader.setDownloaded(pageNum - 1, false);
								if (!downloader.isDownloading()) {
									downloader.start();
								}
							} else {
								//new DownloadTask().execute();
								PicassoCache.getPicassoInstance(getApplicationContext()).load(Uri.parse(NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(pageNum)))).into(mImageView,onimageload);
								return;
							}
						} else {
							//new DownloadTask().execute();
							PicassoCache.getPicassoInstance(getApplicationContext()).load(Uri.parse(NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(pageNum)))).into(mImageView,onimageload);
						}*/
						PicassoCache.getPicassoInstance(getApplicationContext()).load(Uri.parse(NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(pageNum)))).into(mImageView, onimageload);
					}
					break;
				case MSG_ERROR_LOADING:
					mWheelProgress.setVisibility(View.INVISIBLE);
					break;
			}
		}

	}

}
