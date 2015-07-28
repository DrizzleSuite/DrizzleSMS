package org.grovecity.drizzlesms.tutorial;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;

import org.grovecity.drizzlesms.ConversationListActivity;
import org.grovecity.drizzlesms.R;

import java.util.Timer;
import java.util.TimerTask;


public class TutorialActivity extends Activity {
	private class PageSwitcherTask extends TimerTask {

		public void run() {
			runOnUiThread(new Runnable() {

				public void run() {
					if (mViewPager.getCurrentItem() == 3) {
						mTimer.cancel();
						return;
					}
					if (mCurrentPageNumber == -1) {
						mCurrentPageNumber = mViewPager.getCurrentItem();
						return;
					} else {
						mViewPager.setCurrentItem(1 + mViewPager
								.getCurrentItem());
						return;
					}
				}
			});
		}

		private PageSwitcherTask() {
			super();
		}

		PageSwitcherTask(PageSwitcherTask pageswitchertask) {
			this();
		}
	}

	private PagerAdapter mAdapter;
	private int mCurrentPageNumber;
	private int mImageId[];
	private CirclePageIndicator mIndicator;
	private String mNumPages[];
	private Timer mTimer;
	private ViewPager mViewPager;

	public TutorialActivity() {
	}

	private void enablePageSwitcher(int i) {
		mCurrentPageNumber = -1;
		mTimer = new Timer();
		mTimer.scheduleAtFixedRate(new PageSwitcherTask(null), 0L, i * 1000);
	}

	private void setupViewPager() {
		mNumPages = (new String[] { "1", "2", "3" });
		mImageId = (new int[] { R.drawable.drizzle_tut,
				R.drawable.drizzle_tut, R.drawable.drizzle_tut });
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mAdapter = new ViewPagerAdapter(this, mNumPages,
				TutorialConstants.TipsTitle, TutorialConstants.TipsDescription,
				mImageId);
		mViewPager.setAdapter(mAdapter);
		mIndicator = (CirclePageIndicator) findViewById(R.id.indicator);
		mIndicator.setPageColor(0xff000000);
		mIndicator.setSnap(true);
		mIndicator.setViewPager(mViewPager);
		enablePageSwitcher(3);
	}

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.viewpager_main);
		if (SharedPreferencesBuilder.getSharedPreferences(this).getBoolean(
				"SP_ShowTutorial", true)
				|| getIntent() != null
				&& getIntent().getStringExtra("TAG") != null) {
			setupViewPager();
			return;
		} else {
			Intent intent = new Intent(this, ConversationListActivity.class);
			intent.setFlags(0x10008000);
			startActivity(intent);
			return;
		}
	}

	public void onWindowFocusChanged(boolean flag) {
		super.onWindowFocusChanged(flag);
		if (android.os.Build.VERSION.SDK_INT >= 19 && flag) {
			getWindow().getDecorView().setSystemUiVisibility(5126);
		}
	}

}
