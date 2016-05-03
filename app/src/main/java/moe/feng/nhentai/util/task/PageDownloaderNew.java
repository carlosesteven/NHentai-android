package moe.feng.nhentai.util.task;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.FileAsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import cz.msebera.android.httpclient.Header;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.api.common.NHentaiUrl;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.util.ExecutorManager;

import static moe.feng.nhentai.cache.common.Constants.CACHE_PAGE_IMG;

public class PageDownloaderNew {

	public static final int STATE_START = 100, STATE_PAUSE = 101, STATE_STOP = 102, STATE_ALL_OK = 103;
	public static final String TAG = PageDownloaderNew.class.getSimpleName();
	public boolean isRunning = true;
	private Context context;
	private Book book;
	private int currentPosition, downloadingPosition = -1;
	private OnDownloadListener listener;
	private DownloadThread mDownloadThread;
	private int state;
	private boolean[] isDownloaded;

	public PageDownloaderNew(Context context, Book book) {
		this.context = context;
		this.book = book;
	}

	public int getCurrentPosition() {
		return this.currentPosition;
	}

	public void setCurrentPosition(int currentPosition) {
		this.currentPosition = currentPosition;
	}

	private int nextToDownloadPosition() {
		int pos = findFirstUndownloadedPosition(getCurrentPosition());
		if (pos == book.pageCount - 1) {
			pos = findFirstUndownloadedPosition(0);
		}
		return pos;
	}

	private int findFirstUndownloadedPosition(int start) {
		for (int i = start; i < book.pageCount; i++) {
			if (!isDownloaded[i]) {
				Log.i(TAG, i + " is undownloaded.");
				return i;
			}
		}
		return book.pageCount - 1;
	}

	public void start() {
		Log.i(TAG, "download start");
		if (mDownloadThread != null) {
			mDownloadThread.isRunning = false;
			try {
				mDownloadThread.cancel(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		mDownloadThread = new DownloadThread();
		downloadingPosition = -1;
		isDownloaded = new boolean[book.pageCount];
		for (int i = 0; i < book.pageCount; i++) {
			isDownloaded[i] = PageApi.isPageOriginImageLocalFileExist(context, book, i + 1);
		}
		state = STATE_START;
		mDownloadThread.executeOnExecutor(ExecutorManager.getExecutor());
	}

	public void continueDownload() {
		Log.i(TAG, "download continue");
		if (mDownloadThread != null && mDownloadThread.isRunning) {
			state = STATE_START;
		} else {
			this.start();
		}
	}

	public void pause() {
		Log.i(TAG, "download pause");
		state = STATE_PAUSE;
	}

	public void stop() {
		Log.i(TAG, "download stop");
		state = STATE_STOP;
	}

	public boolean isDownloaded(int position) {
		return isDownloaded[position];
	}

	public void setDownloaded(int position, boolean bool) {
		isDownloaded[position] = bool;
	}

	public boolean isAllDownloaded() {
		boolean b = true;
		for (int i = 0; i < book.pageCount && b; i++) {
			b = isDownloaded[i];
		}
		return b;
	}

	public int getDownloadedCount() {
		int i = 0;
		for (boolean b : isDownloaded) {
			if (b) {
				i++;
			}
		}
		return i;
	}

	public boolean isDownloading() {
		return mDownloadThread != null && mDownloadThread.isRunning;
	}

	public boolean isStop() {
		return state == STATE_STOP;
	}

	public boolean isPause() {
		return state == STATE_PAUSE;
	}

	public boolean isThreadAllOk() {
		return state == STATE_ALL_OK;
	}

	public OnDownloadListener getOnDownloadListener() {
		return listener;
	}

	public void setOnDownloadListener(OnDownloadListener listener) {
		this.listener = listener;
	}

	private void next() {
		if (isRunning && !isAllDownloaded()) {
			downloadingPosition = nextToDownloadPosition();
			Log.i(TAG, "downloadingPosition:" + downloadingPosition);
			if (state == STATE_PAUSE) {
				Log.i(TAG, "download paused");
				if (listener != null) listener.onStateChange(STATE_PAUSE, getDownloadedCount());
				while (state == STATE_PAUSE) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			if (state == STATE_STOP) {
				Log.i(TAG, "download stopped");
				if (listener != null) listener.onStateChange(STATE_STOP, getDownloadedCount());
				isRunning = false;
				return;
			}
			setDownload(CACHE_PAGE_IMG, NHentaiUrl.getOriginPictureUrl(book.galleryId, String.valueOf(downloadingPosition + 1)));
		} else {
			Log.i(TAG, "all downloaded");
			if (listener != null) listener.onStateChange(STATE_ALL_OK, getDownloadedCount());
			isRunning = false;
		}
	}

	private void setDownload(final String type, final String url) {
		URL ufile;
		try {
			ufile = new URL(url);
		} catch (MalformedURLException e) {
			return;
		}
		final AsyncHttpClient client = new SyncHttpClient();
		client.setConnectTimeout(5000);
		client.setLoggingEnabled(false);
		client.get(ufile.toString(), null, new FileAsyncHttpResponseHandler(context) {
			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable throwable, File file) {
				if (statusCode != 200) {
					if (url.contains("jpg")) {
						URL filel = null;
						try {
							filel = new URL(url.replace("jpg", "png"));
						} catch (MalformedURLException ex) {
							ex.printStackTrace();
						}
						setDownload(type, filel.toString());
					} else {
						Log.i(TAG, "download error");
						if (listener != null) listener.onError(currentPosition, -1);
						next();
					}
				}
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, File file) {
				FileCacheManager cacheManager = FileCacheManager.getInstance(context);
				if (cacheManager.move(file, new File(cacheManager.getCachePath(type, cacheManager.getCacheName(url))))) {
					Log.i(TAG, "download finish");
					isDownloaded[downloadingPosition] = true;
					if (listener != null) listener.onFinish(currentPosition, getDownloadedCount());
					next();
				}
			}
		});
	}

	public interface OnDownloadListener {

		void onFinish(int position, int progress);
		void onError(int position, int errorCode);
		void onStateChange(int state, int progress);

	}

	private class DownloadThread extends AsyncTask<String, Void, Void> {

		public boolean isRunning = true;

		@Override
		protected Void doInBackground(String... params) {
			next();
			return null;
		}
	}

}
