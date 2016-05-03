package moe.feng.nhentai.ui;

import android.animation.Animator;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.ShareActionProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.nvanbenschoten.motion.ParallaxImageView;
import com.squareup.picasso.Picasso;

import java.io.File;

import moe.feng.nhentai.Async.DownloadInterface.DownloadInterface;
import moe.feng.nhentai.Async.DownloadTask;
import moe.feng.nhentai.R;
import moe.feng.nhentai.api.BookApi;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.api.common.NHentaiUrl;
import moe.feng.nhentai.cache.common.Constants;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.model.Category;
import moe.feng.nhentai.ui.adapter.BookPreviewGridAdapter;
import moe.feng.nhentai.ui.common.AbsActivity;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.ColorGenerator;
import moe.feng.nhentai.util.ExecutorManager;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.TextDrawable;
import moe.feng.nhentai.util.Utility;
import moe.feng.nhentai.util.task.BookDownloader;
import moe.feng.nhentai.view.AutoWrapLayout;
import moe.feng.nhentai.view.ObservableScrollView;
import moe.feng.nhentai.view.WheelProgressView;

public class BookDetailsActivity extends AbsActivity implements
		ObservableScrollView.OnScrollChangeListener,
		DownloadInterface {

	public final static int REQUEST_MAIN = 1001, RESULT_HAVE_FAV = 100;
	public final static int NOTIFICATION_ID_FINISH = 10000;
	private final static String EXTRA_BOOK_DATA = "book_data", EXTRA_POSITION = "item_position";
	android.support.v4.app.NotificationCompat.Builder mBuilder;
	private ObservableScrollView mScrollView;
	private FrameLayout mAppBarContainer, mImageContainer;
	private ImageView mImagePlaceholderView;
	private ParallaxImageView mImageView;
	private FloatingActionButton mFAB;
	private TextView mTitleText;
	private LinearLayout mTagsLayout;
	private LinearLayout mContentView, mAppBarBackground;
	private WheelProgressView mProgressWheel;
	private RecyclerView mPreviewList;
	private boolean isPlayingFABAnimation = false;
	private int APP_BAR_HEIGHT, TOOLBAR_HEIGHT, STATUS_BAR_HEIGHT = 0, minHeight = 0;
	private MenuItem mActionDownload;
	private ShareActionProvider mShareActionProvider;
	private Book book;
	private int fromPosition;
	private boolean isFavorite = false, originFavorite = false, isFromExternal = false;
	private boolean isDownloaded = false;
	private AlertDialog mDialogDel, mDialogDownload, mDialogDelOrDownload;
	private MaterialDialog mDialogDownloading;
	private BookDownloader mDownloader;
	private FileCacheManager mFileCacheManager;
	private int notID;

	public static void launch(Activity activity, ImageView imageView, Book book, int fromPosition) {
		Intent intent = new Intent(activity, BookDetailsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		intent.putExtra(EXTRA_BOOK_DATA, book.toJSONString());
		intent.putExtra(EXTRA_POSITION, fromPosition);
		activity.startActivityForResult(intent, REQUEST_MAIN);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		APP_BAR_HEIGHT = getResources().getDimensionPixelSize(R.dimen.appbar_parallax_max_height);
		TOOLBAR_HEIGHT = getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material);
		if (Build.VERSION.SDK_INT >= 19) {
			STATUS_BAR_HEIGHT = Utility.getStatusBarHeight(getApplicationContext());
		}
		minHeight = APP_BAR_HEIGHT - TOOLBAR_HEIGHT - STATUS_BAR_HEIGHT;

		Intent intent = getIntent();
		if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			book = new Book();
			String url = intent.getData().toString();
			if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
			String bid = url.substring(url.lastIndexOf("/") + 1, url.length());
			Log.i("TAG", "url get id:" + bid);
			book.bookId = bid;
			fromPosition = -1;
			isFromExternal = true;
		} else {
			book = Book.toBookFromJson(intent.getStringExtra(EXTRA_BOOK_DATA));
			fromPosition = intent.getIntExtra(EXTRA_POSITION, 0);
		}

		mFileCacheManager = FileCacheManager.getInstance(getApplicationContext());

		isFavorite = originFavorite = FavoritesManager.getInstance(getApplicationContext()).contains(book.bookId);

		setContentView(R.layout.activity_book_details);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		FileCacheManager cm = FileCacheManager.getInstance(getApplicationContext());

		TextDrawable textDrawable;
		if (book.title != null) {
			int color = ColorGenerator.MATERIAL.getColor(book.title);
			textDrawable = TextDrawable.builder().buildRect(Utility.getFirstCharacter(book.title), color);
		} else {
			textDrawable = TextDrawable.builder().buildRect("", getResources().getColor(R.color.deep_purple_500));
		}
		mImagePlaceholderView.setImageDrawable(textDrawable);

		if (book.galleryId != null) {
			if (cm.cacheExistsUrl(Constants.CACHE_COVER, book.bigCoverImageUrl)) {
				if (cm.cacheExistsUrl(Constants.CACHE_PAGE_IMG,
						NHentaiUrl.getOriginPictureUrl(book.galleryId, "1"))) {
					Picasso.with(getApplicationContext())
							.load(cm.getBitmapUrlFile(Constants.CACHE_PAGE_IMG, NHentaiUrl.getOriginPictureUrl(book.galleryId, "1")))
							.fit()
							.centerCrop()
							.into(mImageView);
				} else {
					Picasso.with(getApplicationContext())
							.load(cm.getBitmapUrlFile(Constants.CACHE_COVER, book.bigCoverImageUrl))
							.fit()
							.centerCrop()
							.into(mImageView);
					if (mSets.getBoolean(Settings.KEY_FULL_IMAGE_PREVIEW, false)) {
						new CoverTask().execute(book);
					}
				}
			} else {
				if (cm.cacheExistsUrl(Constants.CACHE_THUMB, book.previewImageUrl)) {
					Picasso.with(getApplicationContext())
							.load(cm.getBitmapUrlFile(Constants.CACHE_THUMB, book.previewImageUrl))
							.fit()
							.centerCrop()
							.into(mImageView);
				} else {
					mImageView.setImageDrawable(textDrawable);
				}
				new CoverTask().execute(book);
			}
		}

		if (book.pageCount != 0) {
			mContentView.setVisibility(View.GONE);
			mProgressWheel.setVisibility(View.VISIBLE);
			mProgressWheel.spin();
			new Thread() {
				@Override
				public void run() {
					try {
						Thread.sleep(250);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							updateUIContent();
						}
					});
				}
			}.start();
		} else {
			startBookGet();
		}

		if (!isFromExternal) checkIsDownloaded();

		mBuilder = new android.support.v4.app.NotificationCompat.Builder(getApplicationContext())
				.setSmallIcon(android.R.drawable.stat_sys_download);
	}

	@Override
	protected void setUpViews() {
		mAppBarContainer = $(R.id.appbar_container);
		mAppBarBackground = $(R.id.appbar_background);
		mImageContainer = $(R.id.image_container);
		mImageView = $(R.id.preview_image);
		mImagePlaceholderView = $(R.id.preview_placeholder);
		mFAB = $(R.id.fab);
		mTitleText = $(R.id.tv_title);
		mTagsLayout = $(R.id.book_tags_layout);
		mContentView = $(R.id.book_content);
		mProgressWheel = $(R.id.wheel_progress);
		mPreviewList = $(R.id.preview_list);
		mScrollView = $(R.id.scroll_view);

		mPreviewList.setHasFixedSize(true);
		mPreviewList.setLayoutManager(new GridLayoutManager(getApplicationContext(), 2, LinearLayoutManager.HORIZONTAL, false));

		float fabTranslationY = -getResources().getDimension(R.dimen.floating_action_button_size_half);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			fabTranslationY -= getResources().getDimension(R.dimen.fab_transition_z) * 2;
		}
		mFAB.setTranslationY(fabTranslationY);
		mScrollView.addOnScrollChangeListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		mImageView.registerSensorManager(10);
	}

	@Override
	public void onPause() {
		super.onPause();
		mImageView.unregisterSensorManager();
	}

	@Override
	public void onScrollChanged(ObservableScrollView view, int x, int y, int oldx, int oldy) {
		setViewsTranslation(Math.min(minHeight, y));
	}

	private void setViewsTranslation(int target) {
		mAppBarContainer.setTranslationY(-target);
		mAppBarBackground.setTranslationY(target);
		float alpha = Math.min(1, -mAppBarContainer.getTranslationY() / (float) minHeight);
		mAppBarBackground.setAlpha(alpha);

		mFAB.setTranslationY(-getResources().getDimension(R.dimen.floating_action_button_size_half)-target);
		if (alpha > 0.8f && !isPlayingFABAnimation) {
			hideFAB();
		} else if (alpha < 0.65f && !isPlayingFABAnimation) {
			showFAB();
		}

		mImageContainer.setTranslationY(target * 0.7f);
	}

	private void updateUIContent() {
		$(R.id.toolbar).invalidate();

		mFAB.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				GalleryActivity.launch(BookDetailsActivity.this, book, 0);
			}
		});
		$(R.id.appbar_container).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				GalleryActivity.launch(BookDetailsActivity.this, book, 0);
			}
		});

		updateDetailsContent();
	}

	private void updateDetailsContent() {
		mProgressWheel.setVisibility(View.GONE);
		mContentView.setVisibility(View.VISIBLE);
		mContentView.animate().alphaBy(0f).alpha(1f).setDuration(1500).start();
		mTitleText.setText(TextUtils.isEmpty(book.titleJP) ? book.title : book.titleJP);
		if (isFromExternal) {
			new CoverTask().execute(book);
		}

		checkIsDownloaded();
		setUpShareAction();
		updatePreviewList();
		updateTagsContent();
	}

	private void updatePreviewList() {
		BookPreviewGridAdapter adapter = new BookPreviewGridAdapter(mPreviewList, book);
		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder holder) {
				GalleryActivity.launch(BookDetailsActivity.this, book, position);
			}
		});
		mPreviewList.setAdapter(adapter);
	}

	private void updateTagsContent() {
		int x = getResources().getDimensionPixelSize(R.dimen.tag_margin_x);
		int y = getResources().getDimensionPixelSize(R.dimen.tag_margin_y);
		int min_width = getResources().getDimensionPixelSize(R.dimen.tag_title_width);

		// Add Parodies Tags
		if (!TextUtils.isEmpty(book.parodies)) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_parodies);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
			tagView.setText(book.parodies);
			tagView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					CategoryActivity.launch(
							BookDetailsActivity.this,
							new Category(Category.Type.PARODY, book.parodies)
					);
				}
			});
			AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
			alp.setMargins(x, y, x, y);
			tagLayout.addView(tagView, alp);
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Characters
		if (!book.characters.isEmpty()) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_characters);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			for (final String tag : book.characters) {
				TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
				tagView.setText(tag);
				tagView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						CategoryActivity.launch(
								BookDetailsActivity.this,
								new Category(Category.Type.CHARACTER, tag)
						);
					}
				});
				AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
				alp.setMargins(x, y, x, y);
				tagLayout.addView(tagView, alp);
			}
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Tags
		if (!book.tags.isEmpty()) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_tag);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			for (final String tag : book.tags) {
				TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
				tagView.setText(tag);
				tagView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						CategoryActivity.launch(
								BookDetailsActivity.this,
								new Category(Category.Type.TAG, tag)
						);
					}
				});
				AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
				alp.setMargins(x, y, x, y);
				tagLayout.addView(tagView, alp);
			}
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Artist Tag
		if (!book.artists.isEmpty()) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_artists);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			for (final String artist : book.artists) {
				TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
				tagView.setText(artist);
				tagView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						CategoryActivity.launch(
								BookDetailsActivity.this,
								new Category(Category.Type.ARTIST, artist)
						);
					}
				});
				AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
				alp.setMargins(x, y, x, y);
				tagLayout.addView(tagView, alp);
			}
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Groups Tag
		if (!TextUtils.isEmpty(book.group)) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_group);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
			tagView.setText(book.group);
			tagView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					CategoryActivity.launch(
							BookDetailsActivity.this,
							new Category(Category.Type.GROUP, book.group)
					);
				}
			});
			AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
			alp.setMargins(x, y, x, y);
			tagLayout.addView(tagView, alp);
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Language Tag
		if (!TextUtils.isEmpty(book.language)) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setText(R.string.tag_type_language);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
			tagView.setText(book.language);
			tagView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					CategoryActivity.launch(
							BookDetailsActivity.this,
							new Category(Category.Type.LANGUAGE, book.language)
					);
				}
			});
			AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
			alp.setMargins(x, y, x, y);
			tagLayout.addView(tagView, alp);
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}
	}

	private void setUpShareAction() {
		String sendingText = String.format(getString(R.string.action_share_send_text),
				book.getAvailableTitle(),
				NHentaiUrl.getBookDetailsUrl(book.bookId)
		);
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(Intent.EXTRA_TEXT, sendingText);
		intent.setType("text/plain");
		if (mShareActionProvider != null) {
			mShareActionProvider.setShareHistoryFileName("custom_share_history.xml");
			mShareActionProvider.setShareIntent(intent);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.menu_details, menu);

		MenuItem mFavItem = menu.findItem(R.id.action_favorite);
		mFavItem.setIcon(isFavorite ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_outline_white_24dp);
		mFavItem.setTitle(isFavorite ? R.string.action_favorite_true : R.string.action_favorite_false);

		MenuItem mShareItem = menu.findItem(R.id.action_share);
		mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(mShareItem);
		setUpShareAction();

		mActionDownload = menu.findItem(R.id.action_download);
		mActionDownload.setIcon(isDownloaded ? R.drawable.ic_cloud_done_white_24dp : R.drawable.ic_cloud_download_white_24dp);
		mActionDownload.setTitle(isDownloaded ? R.string.action_download_okay : R.string.action_download_none);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			this.onBackPressed();
			return true;
		}
		if (id == R.id.action_favorite) {
			FavoritesManager fm = FavoritesManager.getInstance(getApplicationContext());
			if (isFavorite) {
				fm.remove(fm.find(book));
			} else {
				fm.add(book);
			}
			fm.save();
			isFavorite = !isFavorite;
			Snackbar.make(
					$(R.id.main_content),
					isFavorite ? R.string.favorite_add_finished : R.string.favorite_remove_finished,
					Snackbar.LENGTH_LONG
			).show();
			invalidateOptionsMenu();
			return true;
		}
		if (id == R.id.action_download) {
			new Thread() {
				@Override
				public void run() {
					onActionDownloadClick();
				}
			}.start();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		if (originFavorite != isFavorite && fromPosition >= 0) {
			Intent intent = new Intent();
			intent.putExtra("position", fromPosition);
			setResult(RESULT_HAVE_FAV, intent);
		}
		super.onBackPressed();
	}

	private void startBookGet() {
		mContentView.setVisibility(View.GONE);
		mProgressWheel.setVisibility(View.VISIBLE);
		mProgressWheel.spin();

		new BookGetTask().execute(book.bookId);
	}

	private void onActionDownloadClick() {
		final String downloadPath = mFileCacheManager.getExternalPath(book);
		final int count = mFileCacheManager.getExternalBookDownloadedCount(book.bookId);
		if (mFileCacheManager.externalBookExists(book)) {
			if (mFileCacheManager.isExternalBookAllDownloaded(book.bookId)) {
				isDownloaded = true;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidateOptionsMenu();
						showDeleteDialog();
					}
				});
			} else {
				isDownloaded = false;
				int state = getSharedPreferences("data", MODE_PRIVATE).getInt(notID + "state", DownloadTask.State.NONE.getValue());
				if (state == DownloadTask.State.DOWNLOADING.getValue() || state == DownloadTask.State.PAUSE.getValue()) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							invalidateOptionsMenu();
							showDownloadingDialog();
						}
					});
				} else {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							invalidateOptionsMenu();
							showDeleteOrDownloadDialog(count, downloadPath, book.title);
						}
					});
				}
			}
		} else {
			Log.i("TAG", "Couldn\'t find downloaded info.");
			isDownloaded = false;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					invalidateOptionsMenu();
					showDownloadDialog(count, downloadPath, book.title);
				}
			});
		}
	}

	private void checkIsDownloaded() {
		new Thread() {
			@Override
			public void run() {
				isDownloaded = mFileCacheManager.externalBookExists(book)
						&& mFileCacheManager.isExternalBookAllDownloaded(book.bookId);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidateOptionsMenu();
					}
				});
			}
		}.start();
	}

	private void showDeleteDialog() {
		if (mDialogDel == null) {
			mDialogDel = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_ask_delete_title)
					.setMessage(R.string.dialog_ask_delete_summary)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							new DeleteTask().execute();
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mDialogDel.dismiss();
						}
					})
					.create();
		}

		mDialogDel.show();
	}

	private void showDownloadDialog(final int count, String downloadPath, final String title) {
		if (mDialogDownload == null) {
			mDialogDownload = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_ask_download_title)
					.setMessage(getString(R.string.dialog_ask_download_summary, downloadPath))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							startDownload(count, title);
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mDialogDownload.dismiss();
						}
					})
					.create();
		}

		mDialogDownload.show();
	}

	private void showDeleteOrDownloadDialog(final int count, String downloadPath, final String title) {
		if (mDialogDelOrDownload == null) {
			mDialogDelOrDownload = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_ask_d_or_d_title)
					.setMessage(getString(R.string.dialog_ask_d_or_d_summary, count, downloadPath))
					.setPositiveButton(R.string.dialog_ask_d_or_d_continue, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							startDownload(count, title);
						}
					})
					.setNeutralButton(R.string.dialog_ask_d_or_d_delete, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							getSharedPreferences("data", MODE_PRIVATE).edit().putInt(notID + "state", DownloadTask.State.CANCELED.getValue()).apply();
							new DeleteTask().execute();
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mDialogDelOrDownload.dismiss();
						}
					})
					.create();
		}

		mDialogDelOrDownload.show();
	}

	private void startDownload(int progress, final String title) {
		progress = Math.max(0, progress);
		notID = Integer.parseInt(book.galleryId);
		showDownloadingDialog();
		int state = getSharedPreferences("data", MODE_PRIVATE).getInt(notID + "state", DownloadTask.State.NONE.getValue());
		if (state != DownloadTask.State.DOWNLOADING.getValue()) {
			new DownloadTask(progress, book, this).executeOnExecutor(ExecutorManager.getExecutor());
		}
	}

	private void showDownloadingDialog() {
		mDialogDownloading = new MaterialDialog.Builder(BookDetailsActivity.this)
				.title(getString(R.string.dialog_download_title, book.getAvailableTitle()))
				.positiveText(getString(R.string.dialog_download_pause))
				.negativeText(R.string.dialog_ask_d_or_d_delete)
				.onPositive(new MaterialDialog.SingleButtonCallback() {
					@Override
					public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
						int state = getSharedPreferences("data", MODE_PRIVATE).getInt(notID + "state", DownloadTask.State.NONE.getValue());
						if (state == DownloadTask.State.DOWNLOADING.getValue()) {
							getSharedPreferences("data", MODE_PRIVATE).edit().putInt(notID + "state", DownloadTask.State.PAUSE.getValue()).apply();
						}
						if (state == DownloadTask.State.PAUSE.getValue()) {
							getSharedPreferences("data", MODE_PRIVATE).edit().putInt(notID + "state", DownloadTask.State.RESUME_DOWNLOAD.getValue()).apply();
						}
					}
				})
				.onNegative(new MaterialDialog.SingleButtonCallback() {
					@Override
					public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
						getSharedPreferences("data", MODE_PRIVATE).edit().putInt(notID + "state", DownloadTask.State.CANCELED.getValue()).apply();
						new DeleteTask().execute();
					}
				})
				.build();
		/** mDialogDownloading.setButton(
		 DialogInterface.BUTTON_NEUTRAL,
		 getString(R.string.dialog_download_restart),
		 new DialogInterface.OnClickListener() {
		@Override public void onClick(DialogInterface dialogInterface, int i) {
		if (mDownloader.isThreadAllOk()) return;
		mDownloader.start();
		}
		}
		 ); **/

		mDialogDownloading.show();
	}

	private void showFAB() {
		mFAB.animate().scaleX(1f).scaleY(1f)
				.setInterpolator(new OvershootInterpolator())
				.setListener(new Animator.AnimatorListener() {
					@Override
					public void onAnimationStart(Animator animator) {
						isPlayingFABAnimation = true;
					}

					@Override
					public void onAnimationEnd(Animator animator) {
						isPlayingFABAnimation = false;
						if (mAppBarBackground.getAlpha() > 0.8f) {
							hideFAB();
						}
					}

					@Override
					public void onAnimationCancel(Animator animator) {
						isPlayingFABAnimation = false;
					}

					@Override
					public void onAnimationRepeat(Animator animator) {

					}
				})
				.start();
	}

	private void hideFAB() {
		mFAB.animate().scaleX(0f).scaleY(0f)
				.setInterpolator(new AnticipateInterpolator())
				.setListener(new Animator.AnimatorListener() {
					@Override
					public void onAnimationStart(Animator animator) {
						isPlayingFABAnimation = true;
					}

					@Override
					public void onAnimationEnd(Animator animator) {
						isPlayingFABAnimation = false;
						if (mAppBarBackground.getAlpha() < 0.65f) {
							showFAB();
						}
					}

					@Override
					public void onAnimationCancel(Animator animator) {
						isPlayingFABAnimation = false;
					}

					@Override
					public void onAnimationRepeat(Animator animator) {

					}
				})
				.start();
	}

	protected <T extends View> T $(int id) {
		return (T) findViewById(id);
	}

	@Override
	public void onProgress(int ID, int Progress) {

	}

	@Override
	public void onStop(int ID) {
		NotificationManagerCompat.from(getApplicationContext()).cancel(ID);
	}

	@Override
	public void onPause(int ID) {

	}

	@Override
	public void onOK(int ID) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				isDownloaded = true;
				invalidateOptionsMenu();

				NotificationManagerCompat nm = NotificationManagerCompat.from(getApplicationContext());

				Intent intent = new Intent(getApplicationContext(), BookDetailsActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra(EXTRA_BOOK_DATA, book.toJSONString());

				Notification n = new NotificationCompat.Builder(BookDetailsActivity.this)
						.setContentTitle(book.title)
						.setContentText(getString(R.string.dialog_download_notification_title))
						.setOngoing(false)
						.setContentIntent(
								PendingIntent.getActivity(
										getApplicationContext(),
										0,
										intent,
										PendingIntent.FLAG_CANCEL_CURRENT
								)
						)
						.setAutoCancel(true)
						.setSmallIcon(android.R.drawable.stat_sys_download_done)
						.setPriority(Notification.PRIORITY_MAX)
						.build();

				nm.notify(notID, n);

				if (mDialogDownloading == null) return;
				mDialogDownloading.dismiss();
			}
		});
	}

	private class BookGetTask extends AsyncTask<String, Void, BaseMessage> {

		@Override
		protected BaseMessage doInBackground(String... params) {
			Book externalBook = mFileCacheManager.getExternalBook(book.bookId);
			return externalBook != null ? new BaseMessage(0, externalBook) : BookApi.getBook(params[0]);
		}

		@Override
		protected void onPostExecute(BaseMessage result) {
			if (result.getCode() == 0) {
				book = result.getData();
				updateUIContent();
			} else {
				mProgressWheel.setVisibility(View.GONE);

				Snackbar.make(
						$(R.id.main_content),
						R.string.tips_network_error,
						Snackbar.LENGTH_LONG
				).setAction(
						R.string.snack_action_try_again,
						new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								startBookGet();
							}
						}).show();
			}
		}

	}

	private class CoverTask extends AsyncTask<Book, Void, File> {

		@Override
		protected File doInBackground(Book... params) {
			if (mSets.getBoolean(Settings.KEY_FULL_IMAGE_PREVIEW, false)) {
				return PageApi.getPageOriginImageFile(BookDetailsActivity.this, params[0], 1);
			} else {
				return BookApi.getCoverFile(BookDetailsActivity.this, params[0]);
			}
		}

		@Override
		protected void onPostExecute(File result) {
			mImageView.setImageBitmap(null);
			Picasso.with(getApplicationContext())
					.load(result)
					.into(mImageView);
		}
	}

	private class DeleteTask extends AsyncTask<Void, Integer, Void> {

		private ProgressDialog dialog;

		public DeleteTask() {
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			dialog = new ProgressDialog(BookDetailsActivity.this);
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			dialog.setCancelable(false);
			dialog.setCanceledOnTouchOutside(false);
			dialog.setMax(100);

			dialog.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			File parentDir = new File(mFileCacheManager.getExternalPath(book));
			File[] files = parentDir.listFiles();
			int count = 0;
			for (File file : files) {
				file.delete();
				count++;
				this.onProgressUpdate(Utility.calcProgress(count, files.length));
			}
			parentDir.delete();
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... ints) {
			super.onProgressUpdate(ints);
			dialog.setProgress(ints[0]);
		}

		@Override
		protected void onPostExecute(Void args) {
			isDownloaded = false;
			invalidateOptionsMenu();
			dialog.dismiss();
		}

	}
}
