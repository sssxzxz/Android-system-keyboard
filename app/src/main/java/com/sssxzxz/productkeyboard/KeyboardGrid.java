package com.sssxzxz.productkeyboard;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Exact cell geometry shared by the keyboard and its layout preview. */
final class KeyboardGrid extends ViewGroup {
    KeyboardGrid(Context context) { super(context); setMotionEventSplittingEnabled(true); }
    void put(View child,int position,int span) { child.setTag(new int[]{position,span}); addView(child,new LayoutParams(0,0)); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec), h=MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w,h);
        int gap=Math.max(1,Math.round(2*getResources().getDisplayMetrics().density));
        for(int i=0;i<getChildCount();i++) {
            View v=getChildAt(i); int[] p=(int[])v.getTag(); int c=p[0]%5,r=p[0]/5;
            int cw=(c+p[1])*w/5-c*w/5-2*gap, ch=(r+1)*h/5-r*h/5-2*gap;
            v.measure(MeasureSpec.makeMeasureSpec(Math.max(1,cw),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,ch),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        int w=r-l,h=b-t,gap=Math.max(1,Math.round(2*getResources().getDisplayMetrics().density));
        for(int i=0;i<getChildCount();i++) {
            View v=getChildAt(i); int[] p=(int[])v.getTag(); int c=p[0]%5,row=p[0]/5;
            v.layout(c*w/5+gap,row*h/5+gap,(c+p[1])*w/5-gap,(row+1)*h/5-gap);
        }
    }
}
