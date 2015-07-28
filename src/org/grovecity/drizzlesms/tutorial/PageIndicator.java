package org.grovecity.drizzlesms.tutorial;

import android.support.v4.view.ViewPager;

public interface PageIndicator extends ViewPager.OnPageChangeListener {

	   void notifyDataSetChanged();

	   void setCurrentItem(int var1);

	   void setOnPageChangeListener(ViewPager.OnPageChangeListener var1);

	   void setViewPager(ViewPager var1);

	   void setViewPager(ViewPager var1, int var2);
	}
