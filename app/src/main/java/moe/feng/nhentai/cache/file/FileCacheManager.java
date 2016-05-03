package moe.feng.nhentai.cache.file;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.loopj.android.http.FileAsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import cz.msebera.android.httpclient.Header;
import moe.feng.nhentai.api.common.NHentaiUrl;
import moe.feng.nhentai.cache.common.Constants;
import moe.feng.nhentai.model.Book;

import static moe.feng.nhentai.BuildConfig.DEBUG;

public class FileCacheManager {

	private static final String TAG = FileCacheManager.class.getSimpleName();
	
	private static FileCacheManager sInstance;
	
	private File mCacheDir, mExternalDir;

	private URL ufile;

	private boolean state = true;

	private FileCacheManager(Context context) {
		try {
			mCacheDir = context.getExternalCacheDir();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (mCacheDir == null) {
			String cacheAbsDir = "/Android/data" + context.getPackageName() + "/cache/";
			mCacheDir = new File(Environment.getExternalStorageDirectory().getPath() + cacheAbsDir);
		}
		if (mExternalDir == null) {
			String externalAbsDir = "/NHBooks/";
			mExternalDir = new File(Environment.getExternalStorageDirectory().getPath() + externalAbsDir);
		}
	}

	public static FileCacheManager getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new FileCacheManager(context);
		}

		return sInstance;
	}
	
	public boolean createCacheFromNetwork(String type, String url) {
		if (DEBUG) {
			Log.d(TAG, "requesting cache from " + url);
		}
		
		URL u;
		
		try {
			u = new URL(url);
		} catch (MalformedURLException e) {
			return false;
		}
		
		HttpURLConnection conn;
		
		try {
			conn = (HttpURLConnection) u.openConnection();
		} catch (IOException e) {
			return false;
		}
		
		conn.setConnectTimeout(5000);

		try {
			if (conn.getResponseCode() != 200) {
				if (url.contains("jpg")) {
					try {
						u = new URL(url.replace("jpg", "png"));
					} catch (MalformedURLException ex) {
						return false;
					}
					try {
						conn = (HttpURLConnection) u.openConnection();
					} catch (IOException ex) {
						return false;
					}
				} else {
					return false;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			return createCacheFromStrem(type, getCacheName(url), conn.getInputStream());
		} catch (IOException e) {
			return false;
		}
	}

	public boolean createCacheFromNetworkFast(final String type, final String url, final Context context) {
		state = true;

		if (DEBUG) {
			Log.d(TAG, "requesting cache from " + url);
		}

		try {
			ufile = new URL(url);
		} catch (MalformedURLException e) {
			return false;
		}
		SyncHttpClient client = new SyncHttpClient();
		client.setConnectTimeout(5000);
		client.setLoggingEnabled(false);
		client.get(ufile.toString(), null, new FileAsyncHttpResponseHandler(context) {
			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable throwable, File file) {
				if (statusCode != 200) {
					if (url.contains("jpg")) {
						try {
							ufile = new URL(url.replace("jpg", "png"));
						} catch (MalformedURLException ex) {
							state = false;
						}
						createCacheFromNetworkFast(type, ufile.toString(), context);
					}
				}
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, File file) {
				move(file, new File(getCachePath(type, getCacheName(url))));
				state = true;
			}
		});
		return state;
	}
	
	public boolean createCacheFromStrem(String type, String name, InputStream stream) {
		File f = new File(getCachePath(type, name) + "_downloading");
		f.getParentFile().mkdirs();
		f.getParentFile().mkdir();
		
		if (f.exists()) {
			f.delete();
		}
		
		try {
			f.createNewFile();
		} catch (IOException e) {
			return false;
		}
		
		FileOutputStream opt;
		
		try {
			opt = new FileOutputStream(f);
		} catch (FileNotFoundException e) {
			return false;
		}
		
		byte[] buf = new byte[512];
		int len = 0;
		
		try {
			while ((len = stream.read(buf)) != -1) {
				opt.write(buf, 0, len);
			}
		} catch (IOException e) {
			return false;
		}
		
		try {
			stream.close();
			opt.close();
		} catch (IOException e) {
			
		}

		f.renameTo(new File(getCachePath(type, name)));

		return true;
	}

	private boolean copy(File src, File dst) {
		try {
			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dst);

			// Transfer bytes from in to out
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean move(File src, File dst) {
		try {
			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dst);

			// Transfer bytes from in to out
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();

			return src.delete();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	// True if the cache downloaded from url exists
	public boolean cacheExistsUrl(String type, String url) {
		return cacheExists(type, getCacheName(url));
	}
	
	public boolean cacheExists(String type, String name) {
		return new File(getCachePath(type, name)).isFile();
	}

	public File getCachedImage(String type, String url) {
		return new File(getCachePath(type, getCacheName(url)));
	}

	public boolean externalPageExists(Book book, int page) {
		return new File(getExternalPagePath(book, page)).isFile();
	}

	public boolean externalBookExists(Book book) {
		return new File(mExternalDir.getAbsolutePath()+ "/Books/" + book.title + "/book.json").isFile();
	}

	public boolean externalBookExists(String bookId) {
		return new File(mExternalDir.getAbsolutePath()+ "/Books/" + getExternalBook(bookId).title + "/book.json").isFile();
	}

	public boolean isExternalBookAllDownloaded(String bid) {
		Book book = getExternalBook(bid);
		if (book != null) {
			return getExternalBookDownloadedCount(book.bookId) == book.pageCount;
		} else {
			return false;
		}
	}

	public boolean deleteCacheUrl(String type, String url) {
		return deleteCache(type, getCacheName(url));
	}

	public boolean deleteCache(String type, String name) {
		if (cacheExists(type, name)) {
			return new File(getCachePath(type, name)).delete();
		} else {
			return false;
		}
	}

	public InputStream openCacheStream(String type, String name) {
		try {
			return new FileInputStream(new File(getCachePath(type, name)));
		} catch (IOException e) {
			return null;
		}
	}
	
	public InputStream openCacheStreamUrl(String type, String url) {
		return openCacheStream(type, getCacheName(url));
	}
	
	public Bitmap getBitmap(String type, String name) {
		InputStream ipt = openCacheStream(type, name);
		
		if (ipt == null) return null;
		
		Bitmap ret = BitmapFactory.decodeStream(ipt);
		
		try {
			ipt.close();
		} catch (IOException e) {
			
		}
		
		return ret;
	}

	public File getBitmapAllowingExternalPic(Book book, int page) {
		File cache = new File(getCachePath(Constants.CACHE_PAGE_IMG,
				NHentaiUrl.getOriginPictureUrl(book.galleryId, Integer.toString(page))));
		File external = new File(getExternalPagePath(book, page));
		return external.isFile() ? external : cache;
	}
	
	public Bitmap getBitmapUrl(String type, String url) {
		return getBitmap(type, getCacheName(url));
	}

	public File getBitmapFile(String type, String name) {
		return new File(getCachePath(type, name));
	}

	public File getBitmapUrlFile(String type, String url) {
		return getBitmapFile(type, getCacheName(url));
	}

	public Book getExternalBook(String bid) {
		File parentDir = new File(mExternalDir.getAbsolutePath()+ "/Books/");

		if (parentDir.isDirectory()) {
			File[] files = parentDir.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					File bookFile = new File(file.getAbsolutePath() + "/book.json");
					if (bookFile.isFile()) {
						try {
							InputStream ins = new FileInputStream(bookFile);

							byte b[] = new byte[(int) bookFile.length()];
							ins.read(b);
							ins.close();

							Book book = new Gson().fromJson(new String(b), Book.class);
							if (book.bookId.equals(bid)) {
								Log.i(TAG, "Found bookId: " + book.bookId);
								return book;
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
			return null;
		} else {
			return null;
		}

	}

	public ArrayList<Book> getExternalBooks() {
		File parentDir = new File(mExternalDir.getAbsolutePath()+ "/Books/");

		if (parentDir.isDirectory()) {
			File[] files = parentDir.listFiles();
			ArrayList<Book> result = new ArrayList<>();
			for (File file : files) {
				if (file.isDirectory()) {
					File bookFile = new File(file.getAbsolutePath() + "/book.json");
					if (bookFile.isFile()) {
						try {
							InputStream ins = new FileInputStream(bookFile);

							byte b[] = new byte[(int) bookFile.length()];
							ins.read(b);
							ins.close();

							Book book = Book.toBookFromJson(new String(b));
							Log.i(TAG, "Found external bookId: " + book.bookId);
							result.add(book);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
			return result;
		} else {
			return new ArrayList<>();
		}
	}

	public int getExternalBookDownloadedCount(String bookId) {
		Book book = getExternalBook(bookId);
		if (book != null && externalBookExists(book)) {
			File parentDir = new File(mExternalDir.getAbsolutePath()+ "/Books/" + book.title);
			String[] pngs = parentDir.list(new FilenameFilter() {
				@Override
				public boolean accept(File file, String s) {
					return s.endsWith(".png");
				}
			});
			return pngs != null ? pngs.length : 0;
		} else {
			return -1;
		}
	}

	public String getCacheName(String url) {
		return url.replaceAll("/", ".").replaceAll(":", "");
	}
	
	public String getCachePath(String type, String name) {
		return mCacheDir.getAbsolutePath() + "/" + type + "/" + name + ".cache";
	}

	public String getExternalPath(Book book) {
		return mExternalDir.getAbsolutePath() + "/Books/" + book.title;
	}

	public String getExternalPagePath(Book book, int page) {
		return getExternalPath(book) + "/" + String.format("%03d", page) + ".png";
	}

	public boolean saveToExternalPath(Book book, int page) {
		String path = getExternalPagePath(book, page);
		String src = getCachePath(Constants.CACHE_PAGE_IMG, getCacheName(NHentaiUrl.getOriginPictureUrl(book.galleryId, Integer.toString(page))));
		File target = new File(path);
		File srcFile = new File(src);

		File targetParent = new File(mExternalDir.getAbsolutePath() + "/Books/" + book.title);
		if (targetParent.isFile()) {
			targetParent.delete();
		}
		if (!targetParent.isDirectory()) {
			targetParent.mkdirs();
		}

		if (target.exists()) {
			target.delete();
		}

		return saveBookDataToExternalPath(book) && srcFile.isFile() && copy(srcFile, target);
	}

	public boolean saveBookDataToExternalPath(Book book) {
		String path = getExternalPath(book);
		File d = new File(path);

		if (d.isFile()) {
			d.delete();
		}
		d.mkdirs();

		File f = new File(path + "/book.json");

		f.delete();

		try {
			OutputStream out = new FileOutputStream(f);

			out.write(book.toJSONString().getBytes());

			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

}
