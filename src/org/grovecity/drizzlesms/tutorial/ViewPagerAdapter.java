package org.grovecity.drizzlesms.tutorial;


import android.content.Context;
import android.content.Intent;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.grovecity.drizzlesms.ConversationListActivity;
import org.grovecity.drizzlesms.R;

public class ViewPagerAdapter extends PagerAdapter {

	private LayoutInflater inflater;
	private Context mContext;
	private int mImageID[];
	private Button mImageViewGo;
	private String mNumPages[];
	private String mTips[];
	private String mTipsTitle[];
    private LinearLayout tutOne, tutTwo, tutThree;

	public ViewPagerAdapter(Context context, String as[], String as1[],
			String as2[], int ai[]) {
		mContext = context;
		mNumPages = as;
		mTips = as2;
		mTipsTitle = as1;
		mImageID = ai;
	}

	public void destroyItem(ViewGroup viewgroup, int i, Object obj) {
		((ViewPager) viewgroup).removeView((ScrollView) obj);
	}

	public int getCount() {
		return mNumPages.length;
	}

    public Object instantiateItem(ViewGroup viewgroup, int i) {
        inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.view_pager_page_item, viewgroup, false);
        mImageViewGo = (Button) view.findViewById(R.id.gotit);
        mImageViewGo.setVisibility(View.INVISIBLE);
        ((TextView) view.findViewById(R.id.tv_tips)).setText(mTips[i]);
        ((ImageView) view.findViewById(R.id.iv_screenshots))
                .setImageResource(mImageID[i]);
        view.setTag(Integer.valueOf(i));
        if (i == 2) {
            mImageViewGo.setVisibility(View.VISIBLE);
            mImageViewGo
                    .setOnClickListener(new android.view.View.OnClickListener() {

                        public void onClick(View view1) {
                            SharedPreferencesBuilder
                                    .getSharedPreferences(mContext).edit()
                                    .putBoolean("SP_ShowTutorial", false)
                                    .commit();
                            Intent intent = new Intent(mContext, ConversationListActivity.class);
                            intent.setFlags(0x10008000);
                            mContext.startActivity(intent);
                        }

                    });
        }
        ((ViewPager) viewgroup).addView(view);
        return view;
    }

	public boolean isViewFromObject(View view, Object obj) {
		return view == (ScrollView) obj;
	}

}