package moe.feng.nhentai.ui;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.DimenRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.ksoichiro.android.observablescrollview.ObservableRecyclerView;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollViewCallbacks;
import com.github.ksoichiro.android.observablescrollview.ScrollState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealFrameLayout;
import moe.feng.nhentai.R;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.dao.LatestBooksKeeper;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.adapter.BookListRecyclerAdapter;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.ui.fragment.main.DownloadManagerFragment;
import moe.feng.nhentai.ui.fragment.main.FavoriteCategoryFragment;
import moe.feng.nhentai.ui.fragment.main.FavoriteFragment;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.CrashHandler;
import moe.feng.nhentai.util.FilesUtil;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.Updates;
import moe.feng.nhentai.util.Utility;

public class HomeActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

	// View states
	private int mSectionType = SECTION_LATEST;
	private static final int SECTION_LATEST = 0, SECTION_FAV_TAB = 1, SECTION_FOLLOWING_ARTISTS = 2;
	private boolean finishLaunchAnimation = false;

	// Header View
	private float mHeaderTranslationYStart;
	private boolean isFABShowing = true, isSearchBoxShowing = true;
	private int currentY = 0;

	// List
	private ObservableRecyclerView mRecyclerView;
	private BookListRecyclerAdapter mAdapter;
	private StaggeredGridLayoutManager mLayoutManager;
	private SwipeRefreshLayout mSwipeRefreshLayout;
	private ArrayList<Book> mBooks;
	private int mNowPage = 1, mHorCardCount = 2;
	private boolean isFirstLoad = true;

	// Search Bar
	private RevealFrameLayout mSearchBar;
	private CardView mSearchBarCard;
	private ImageButton mSearchBarOtherBtn;

	// Title Bar
	private LinearLayout mTitleBarLayout;
	private AppCompatTextView mTitleMain, mTitleSub;

	// Splash Screen
	private ImageView mSplashIvLogo;
	private AppCompatTextView mSplashTvLogo;
	private FrameLayout mSplashLayout;

	// Drawer
	private DrawerLayout mDrawerLayout;
	private NavigationView mNavigationView;
	private ActionBarDrawerToggle mDrawerToggle;

	// Views
	private View mBackgroundView;
	private FrameLayout mParentLayout, mMainLayout, mFragmentLayout;
	private Toolbar mToolbar;
	private ActionBar mActionBar;
	private FloatingActionButton mLuckyFAB;

	// Fragments
	private DownloadManagerFragment mFragmentDownload;
	private FavoriteFragment mFragmentFavBooks;
	private FavoriteCategoryFragment mFragmentFavCategory;

	// Utils
	private FavoritesManager mFM;
	private LatestBooksKeeper mListKeeper;
	private Settings mSets;

	private Handler mHandler = new Handler();

	public static final String TAG = HomeActivity.class.getSimpleName();

	private static final int REQUEST_CODE_PERMISSION_GET = 101;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		mSets = Settings.getInstance(getApplicationContext());
		CrashHandler.init(getApplicationContext());
		CrashHandler.register();

		/** Set up translucent status bar */
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().setStatusBarColor(Color.TRANSPARENT);
				getWindow().setNavigationBarColor(ContextCompat.getColor(this,R.color.deep_purple_800));
			}
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_home);

		mFM = FavoritesManager.getInstance(getApplicationContext());
		mListKeeper = LatestBooksKeeper.getInstance(getApplicationContext());

		initViews();

		getWindow().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.background_material_light)));

		if (mListKeeper.getData() != null && !mListKeeper.getData().isEmpty() && mListKeeper.getUpdatedMiles() != -1) {
			mBooks = mListKeeper.getData();
			mNowPage = mListKeeper.getNowPage();
			mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
			setRecyclerAdapter(mAdapter);
			isFirstLoad = false;
		} else {
			mBooks = new ArrayList<>();
			mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
			setRecyclerAdapter(mAdapter);
			new PageGetTask().execute(mNowPage);
		}

		if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			if (mSets.getBoolean(Settings.KEY_NO_MEDIA, true)) {
				FilesUtil.createNewFile(FilesUtil.NOMEDIA_FILE);
			}
			onLoadMain();
            Updates.check(this);
		} else {
			new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_permission_title)
					.setMessage(R.string.dialog_permission_msg)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							ActivityCompat.requestPermissions(HomeActivity.this,
									new String[]{
											Manifest.permission.WRITE_EXTERNAL_STORAGE,
											Manifest.permission.READ_EXTERNAL_STORAGE
									},
									REQUEST_CODE_PERMISSION_GET);
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							onLoadMain();
						}
					})
			.show();
		}
	}

	private void onLoadMain() {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				startSplashFinishToMain();
				mRecyclerView.invalidate();
				mAdapter.notifyDataSetChanged();
			}
		}, 1000);
	}

	private void startSplashFinishToMain() {
		mSplashIvLogo.animate()
				.translationY(-getResources().getDimension(R.dimen.logo_fade_out_translation_y))
				.alpha(0.0f)
				.setDuration(300)
				.start();
		mSplashTvLogo.animate()
				.alpha(0.0f)
				.setDuration(300)
				.start();
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				mSplashLayout.setVisibility(View.GONE);

				mHeaderTranslationYStart = -(mParentLayout.getHeight() - getResources().getDimension(R.dimen.background_max_height));
				mBackgroundView.animate()
						.translationY(mHeaderTranslationYStart)
						.setDuration(700)
						.setInterpolator(new OvershootInterpolator(0.6f))
						.start();
				mMainLayout.setVisibility(View.VISIBLE);

				mToolbar.setTranslationY(getResources().getDimension(R.dimen.widget_fade_in_translation_y_s));
				mToolbar.animate()
						.translationY(0f)
						.alpha(1.0f)
						.setDuration(200)
						.start();

				mSearchBar.setTranslationY(getResources().getDimension(R.dimen.widget_fade_in_translation_y_s));
				mSearchBar.animate()
						.translationY(0f).alpha(1.0f)
						.setDuration(200)
						.setStartDelay(100)
						.start();
				mLuckyFAB.setScaleX(0);
				mLuckyFAB.setScaleY(0);
				mLuckyFAB.animate()
						.scaleX(1f)
						.scaleY(1f)
						.setInterpolator(new OvershootInterpolator())
						.setDuration(200)
						.setStartDelay(100)
						.start();
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						int cx = (mSearchBarCard.getLeft() + mSearchBarCard.getRight()) / 2;
						int cy = (mSearchBarCard.getTop() + mSearchBarCard.getBottom()) / 2;
						revealFrom(cx, cy, mSearchBarCard);
						if (!isFirstLoad) {
							mRecyclerView.setVisibility(View.VISIBLE);
							mRecyclerView.setAlpha(0f);
							mRecyclerView.setTranslationY(getResources().getDimension(R.dimen.logo_fade_out_translation_y));
							mRecyclerView.animate()
									.translationY(0f)
									.alpha(1.0f)
									.setDuration(100)
									.setInterpolator(new OvershootInterpolator(0.5f))
									.setStartDelay(200)
									.start();
						}
					}
				}, 100);

				mTitleBarLayout.setTranslationY(getResources().getDimension(R.dimen.widget_fade_in_translation_y_m));
				mTitleBarLayout.animate()
						.translationY(0f)
						.alpha(1.0f)
						.setDuration(150)
						.setStartDelay(200)
						.start();

				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						finishLaunchAnimation = true;
						if (mSets.getInt(Settings.KEY_CELEBRATE, -1) != 2) {
							Snackbar.make(
									mDrawerLayout, R.string.celebrate_2016, Snackbar.LENGTH_INDEFINITE
							).setAction(R.string.snack_action_get_it, new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									mSets.putInt(Settings.KEY_CELEBRATE, 2);
								}
							}).show();
						}
					}
				}, 200);
			}
		}, 300);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mFM.save();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		int id = item.getItemId();
		if (id == R.id.action_load_next_page) {
			mSwipeRefreshLayout.setRefreshing(true);
			new PageGetTask().execute(++mNowPage);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (requestCode == REQUEST_CODE_PERMISSION_GET) {
			if (grantResults.length > 0) {
				boolean b = true;
				for (int i : grantResults) {
					if (i == PackageManager.PERMISSION_DENIED) {
						showPermissionDeniedSnackbar();
						b = false;
						break;
					}
				}
				if (b && mSets.getBoolean(Settings.KEY_NO_MEDIA, true)) {
					FilesUtil.createNewFile(FilesUtil.NOMEDIA_FILE);
				}
			} else {
				showPermissionDeniedSnackbar();
			}
			onLoadMain();
		}
	}

	private void showPermissionDeniedSnackbar() {
		Snackbar.make(
				mRecyclerView,
				R.string.snack_permission_failed,
				Snackbar.LENGTH_INDEFINITE)
				.setAction(R.string.snack_action_try_again, new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						ActivityCompat.requestPermissions(HomeActivity.this,
								new String[]{
										Manifest.permission.WRITE_EXTERNAL_STORAGE,
										Manifest.permission.READ_EXTERNAL_STORAGE
								},
								REQUEST_CODE_PERMISSION_GET);
					}
				})
				.show();
	}

	private void initViews() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			$(R.id.status_bar_header).setVisibility(View.VISIBLE);
		}

		mToolbar = $(R.id.toolbar);
		setSupportActionBar(mToolbar);
		mActionBar = getSupportActionBar();
		mToolbar.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mAdapter.getItemCount() > 0) {
					mRecyclerView.smoothScrollToPosition(0);
				}
			}
		});

		mDrawerLayout = $(R.id.drawer_layout);
		mNavigationView = $(R.id.navigation_view);
		mDrawerToggle = new MyDrawerToggle();
		mNavigationView.setNavigationItemSelectedListener(this);
		mNavigationView.setBackgroundResource(R.color.background_material_light);
		mDrawerLayout.setDrawerListener(new MyDrawerListener());
		mDrawerLayout.post(new Runnable() {
			@Override
			public void run() {
				mDrawerToggle.syncState();
			}
		});
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		mBackgroundView = $(R.id.bg_view);
		mParentLayout = $(R.id.parent_layout);
		mMainLayout = $(R.id.main_layout);
		mFragmentLayout = $(R.id.fragment_layout);
		mLuckyFAB = $(R.id.fab);
		mSplashIvLogo = $(R.id.iv_nh_logo);
		mSplashTvLogo = $(R.id.tv_nh_logo);
		mSplashLayout = $(R.id.splash_layout);
		mSearchBar = $(R.id.search_bar);
		mSearchBarCard = $(R.id.card_view);
		mSearchBarOtherBtn = $(R.id.btn_search_bar_other);
		mTitleBarLayout = $(R.id.title_bar_layout);
		mTitleMain = $(R.id.tv_title_main);
		mTitleSub = $(R.id.tv_title_sub);
		mSwipeRefreshLayout = $(R.id.swipe_refresh_layout);
		mRecyclerView = $(R.id.recycler_view);

		if ((mHorCardCount = mSets.getInt(Settings.KEY_CARDS_COUNT, -1)) < 1) {
			mHorCardCount = Utility.getHorizontalCardCountInScreen(this);
		}

		mLayoutManager = new StaggeredGridLayoutManager(mHorCardCount, StaggeredGridLayoutManager.VERTICAL);
		mRecyclerView.setLayoutManager(mLayoutManager);
		mRecyclerView.setHasFixedSize(false);
		mRecyclerView.setScrollViewCallbacks(new ObservableScrollViewCallbacks() {
			@Override
			public void onScrollChanged(int i, boolean b, boolean b1) {
				currentY = i + getResources().getDimensionPixelOffset(R.dimen.list_margin_top);
				updateTranslation(currentY);
			}

			@Override
			public void onDownMotionEvent() {

			}

			@Override
			public void onUpOrCancelMotionEvent(ScrollState scrollState) {

			}
		});
		mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				int deltaY = -dy;

				if (deltaY > 0 != isSearchBoxShowing) {
					if (deltaY >= 0) {
						showSearchBox();
					} else {
						hideSearchBox();
					}
				}
				if (deltaY > 0 != isFABShowing) {
					if (deltaY >= 0) {
						showFAB();
					} else {
						hideFAB();
					}
				}
			}
		});

		mSwipeRefreshLayout.setColorSchemeResources(
				R.color.deep_purple_500, R.color.pink_500, R.color.orange_500, R.color.brown_500,
				R.color.indigo_500, R.color.blue_500, R.color.teal_500, R.color.green_500
		);
		mSwipeRefreshLayout.setProgressViewOffset(
				false,
				calcDimens(R.dimen.search_bar_height),
				calcDimens(R.dimen.search_bar_height, R.dimen.title_bar_height,
						R.dimen.title_bar_content_margin_bottom, R.dimen.background_over_height)
		);
		mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				if (!mSwipeRefreshLayout.isRefreshing()) {
					mSwipeRefreshLayout.setRefreshing(true);
				}
				if (mAdapter.getItemCount() >= 1) {
					mRecyclerView.smoothScrollToPosition(0);
				}
				mBooks.clear();
				mAdapter.notifyDataSetChanged();
				new PageGetTask().execute(mNowPage = 1);
			}
		});

		mSearchBarCard.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				SearchActivity.launch(HomeActivity.this, mSearchBarCard);
			}
		});

		updateTitleBar(SECTION_LATEST);
	}

	private void updateTranslation(int currentY) {
		if (!finishLaunchAnimation) return;

		int headerDeltaY = Math.max(Math.min(currentY, calcDimens(R.dimen.background_delta_height)), 0);
		mBackgroundView.setTranslationY(mHeaderTranslationYStart - headerDeltaY * 1.5f);

		int titleBarDistance = calcDimens(R.dimen.title_bar_height);
		float titleAlpha = Math.min(currentY, titleBarDistance);
		titleAlpha /= (float) titleBarDistance;
		mTitleBarLayout.setAlpha(1 - titleAlpha);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mSearchBarCard.setCardElevation(titleAlpha * calcDimens(R.dimen.searchbar_elevation_raised));
		}

		if (currentY * 1.5f + 5 >= calcDimens(R.dimen.background_delta_height)) {
			ViewCompat.setElevation(mToolbar, calcDimens(R.dimen.appbar_elevation));
		} else {
			ViewCompat.setElevation(mToolbar, 0f);
		}
	}

	private void updateTitleBar(int type) {
		switch (type) {
			case SECTION_LATEST:
				mTitleMain.setText(R.string.title_bar_main_recent);
				if (mListKeeper.getUpdatedMiles() != -1) {
					if (System.currentTimeMillis() - mListKeeper.getUpdatedMiles() < 1 * 60 * 1000) {
						mTitleSub.setText(R.string.title_bar_updated_time_just_now);
					} else {
						Calendar c = Calendar.getInstance();
						c.setTimeInMillis(mListKeeper.getUpdatedMiles());
						SimpleDateFormat format = new SimpleDateFormat("yyyy/M/d H:mm:ss");
						String result;
						try {
							result = format.format(c.getTime());
						} catch (Exception e) {
							e.printStackTrace();
							result = "null";
						}
						mTitleSub.setText(getString(R.string.title_bar_updated_time_at, result));
					}
				} else {
					mTitleSub.setText(R.string.title_bar_updated_time_null);
				}
				break;
		}
	}

	private void showFAB() {
		if (!finishLaunchAnimation) return;
		isFABShowing = true;
		mLuckyFAB.animate()
				.scaleX(1f)
				.scaleY(1f)
				.setInterpolator(new OvershootInterpolator())
				.setDuration(300)
				.start();
	}

	private void hideFAB() {
		if (!finishLaunchAnimation) return;
		isFABShowing = false;
		mLuckyFAB.animate()
				.scaleX(0f)
				.scaleY(0f)
				.setInterpolator(new AnticipateInterpolator())
				.setDuration(300)
				.start();
	}

	private void showSearchBox() {
		if (!finishLaunchAnimation) return;
		isSearchBoxShowing = true;
		if (currentY < 10) {
			mSearchBar.setTranslationY(0);
		} else {
			mSearchBar.setTranslationY(-calcDimens(R.dimen.logo_fade_out_translation_y));
			mSearchBar.animate()
					.translationY(0)
					.alpha(1f)
					.setDuration(200)
					.start();
		}
	}

	private void hideSearchBox() {
		if (!finishLaunchAnimation) return;
		if (currentY > calcDimens(R.dimen.background_delta_height)) {
			isSearchBoxShowing = false;
			mSearchBar.setTranslationY(0);
			mSearchBar.animate()
					.translationY(-calcDimens(R.dimen.logo_fade_out_translation_y))
					.alpha(0f)
					.setDuration(200)
					.start();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == BookDetailsActivity.REQUEST_MAIN) {
			if (resultCode == BookDetailsActivity.RESULT_HAVE_FAV) {
				mAdapter.notifyItemChanged(intent.getIntExtra("position", 0));
			}
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		mDrawerLayout.closeDrawers();

		int id = item.getItemId();
		switch (id) {
			case R.id.navigation_item_home:
				mActionBar.setTitle(R.string.app_name);
				mFragmentLayout.setVisibility(View.GONE);
				if (currentY >= calcDimens(R.dimen.background_delta_height)) {
					ViewCompat.setElevation(mToolbar, calcDimens(R.dimen.appbar_elevation));
				} else {
					ViewCompat.setElevation(mToolbar, 0f);
				}
				mToolbar.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (mAdapter.getItemCount() > 0) {
							mRecyclerView.smoothScrollToPosition(0);
						}
					}
				});
				return true;
			case R.id.navigation_item_download:
				mActionBar.setTitle(R.string.item_download);
				mFragmentLayout.setVisibility(View.VISIBLE);
				ViewCompat.setElevation(mToolbar, calcDimens(R.dimen.appbar_elevation));
				if (mFragmentDownload == null) mFragmentDownload = new DownloadManagerFragment();
				getFragmentManager().beginTransaction()
						.replace(R.id.fragment_layout, mFragmentDownload)
						.commit();
				mToolbar.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (mAdapter.getItemCount() > 0) {
							mFragmentDownload.scrollToTop();
						}
					}
				});
				return true;
			case R.id.navigation_item_fav_books:
				mActionBar.setTitle(R.string.item_favorite_books);
				mFragmentLayout.setVisibility(View.VISIBLE);
				ViewCompat.setElevation(mToolbar, calcDimens(R.dimen.appbar_elevation));
				if (mFragmentFavBooks == null) mFragmentFavBooks = new FavoriteFragment();
				getFragmentManager().beginTransaction()
						.replace(R.id.fragment_layout, mFragmentFavBooks)
						.commit();
				mToolbar.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (mAdapter.getItemCount() > 0) {
							mFragmentFavBooks.scrollToTop();
						}
					}
				});
				return true;
			case R.id.navigation_item_fav_categories:
				mActionBar.setTitle(R.string.item_favorite_categories);
				mFragmentLayout.setVisibility(View.VISIBLE);
				ViewCompat.setElevation(mToolbar, calcDimens(R.dimen.appbar_elevation));
				if (mFragmentFavCategory == null) mFragmentFavCategory = new FavoriteCategoryFragment();
				getFragmentManager().beginTransaction()
						.replace(R.id.fragment_layout, mFragmentFavCategory)
						.commit();
				mToolbar.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (mAdapter.getItemCount() > 0) {
							mFragmentFavCategory.scrollToTop();
						}
					}
				});
				return true;
			case R.id.navigation_item_settings:
				SettingsActivity.launchActivity(this, SettingsActivity.FLAG_MAIN);
				return true;
			case R.id.navigation_item_donate:
				new AlertDialog.Builder(this)
						.setTitle(R.string.dialog_donate_title)
						.setMessage(R.string.dialog_donate_message)
						.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {

							}
						})
						.show();
				return true;
			case R.id.navigation_item_open_nhentai:
				Uri uri = Uri.parse("http://nhentai.net");
				startActivity(new Intent(Intent.ACTION_VIEW, uri));
				return true;
		}

		return false;
	}

	public FavoritesManager getFavoritesManager() {
		return mFM == null ? mFM = FavoritesManager.getInstance(getApplicationContext()) : mFM;
	}

	private void setRecyclerAdapter(BookListRecyclerAdapter adapter) {
		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder viewHolder) {
				BookListRecyclerAdapter.ViewHolder holder = (BookListRecyclerAdapter.ViewHolder) viewHolder;
				BookDetailsActivity.launch(HomeActivity.this, holder.mPreviewImageView, holder.book, position);
			}
		});
		adapter.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView rv, int dx, int dy) {
				super.onScrolled(rv, dx, dy);
				if (!mSwipeRefreshLayout.isRefreshing() && mLayoutManager.findLastCompletelyVisibleItemPositions(new int[mHorCardCount])[1] >= mAdapter.getItemCount() - 2) {
					mSwipeRefreshLayout.setRefreshing(true);
					new PageGetTask().execute(++mNowPage);
				}
			}
		});

		mRecyclerView.setAdapter(adapter);
		adapter.notifyDataSetChanged();
	}

	private void revealFrom(int cx, int cy, View root) {
		Resources r = getResources();
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 96, r.getDisplayMetrics());

		int finalRadius = (int) Math.max(root.getWidth(), px);

		SupportAnimator animator = ViewAnimationUtils.createCircularReveal(root, cx, cy, 0, finalRadius);
		animator.setInterpolator(new AccelerateDecelerateInterpolator());
		animator.setDuration(200);
		animator.start();
	}

	private int calcDimens(@DimenRes int... dimenIds) {
		int result = 0;
		for (int dimenId : dimenIds) {
			result += getResources().getDimensionPixelSize(dimenId);
		}
		return result;
	}

	private <T extends View> T $(int id) {
		return (T) findViewById(id);
	}

	private class PageGetTask extends AsyncTask<Integer, Void, BaseMessage> {

		@Override
		protected BaseMessage doInBackground(Integer... params) {
			mFM.reload();
			return PageApi.getHomePageList(params[0]);
		}

		@Override
		protected void onPostExecute(BaseMessage msg) {
			mSwipeRefreshLayout.setRefreshing(false);
			if (msg != null) {
				if (msg.getCode() == 0 && msg.getData() != null) {
					if (!((ArrayList<Book>) msg.getData()).isEmpty()) {
						mBooks.addAll((ArrayList<Book>) msg.getData());

						mListKeeper.setData(mBooks);
						mListKeeper.setUpdatedMiles(System.currentTimeMillis());
						mListKeeper.setNowPage(mNowPage);
						new Thread() {
							@Override
							public void run() {
								mListKeeper.save();
							}
						}.start();

						updateTitleBar(mSectionType);
						mHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								mAdapter.notifyDataSetChanged();
							}
						}, 500);
						if (!isFirstLoad) {
							mRecyclerView.setVisibility(View.VISIBLE);
						}
						isFirstLoad = false;
					}
				} else if (mNowPage == 1) {
					mListKeeper.setData(new ArrayList<Book>());
					mListKeeper.setUpdatedMiles(-1);
					mListKeeper.setNowPage(1);
					Snackbar.make(
							mRecyclerView,
							R.string.tips_network_error,
							Snackbar.LENGTH_LONG
					).setAction(
							R.string.snack_action_try_again,
							new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									mSwipeRefreshLayout.setRefreshing(true);
									new PageGetTask().execute(mNowPage);
								}
							}
					).show();
				}
			}
		}

	}

	private class MyDrawerListener implements DrawerLayout.DrawerListener {

		@Override
		public void onDrawerOpened(View drawerView) {
			mDrawerToggle.onDrawerOpened(drawerView);
		}

		@Override
		public void onDrawerClosed(View drawerView) {
			mDrawerToggle.onDrawerClosed(drawerView);
		}

		@Override
		public void onDrawerSlide(View drawerView, float slideOffset) {
			mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
		}

		@Override
		public void onDrawerStateChanged(int newState) {
			mDrawerToggle.onDrawerStateChanged(newState);
		}

	}

	private class MyDrawerToggle extends ActionBarDrawerToggle {

		public MyDrawerToggle() {
			super(HomeActivity.this, mDrawerLayout,
					mToolbar,
					R.string.abc_action_bar_home_description,
					R.string.abc_action_bar_home_description
			);
		}

		public MyDrawerToggle(Activity activity, DrawerLayout drawerLayout, Toolbar toolbar, int openDrawerContentDescRes, int closeDrawerContentDescRes) {
			super(activity, drawerLayout, toolbar, openDrawerContentDescRes, closeDrawerContentDescRes);
		}

		@Override
		public void onDrawerClosed(View drawerView) {
			super.onDrawerClosed(drawerView);
			supportInvalidateOptionsMenu();
		}

		@Override
		public void onDrawerOpened(View drawerView) {
			super.onDrawerOpened(drawerView);
			supportInvalidateOptionsMenu();
		}

	}

}
