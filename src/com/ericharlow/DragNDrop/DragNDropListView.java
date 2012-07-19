/*
 * Copyright (C) 2010 Eric Harlow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ericharlow.DragNDrop;

import com.nononsenseapps.notepad.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.AttributeSet;
import com.nononsenseapps.helpers.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;

public class DragNDropListView extends ListView {
	// For scrolling
	public static final int slowSpeed = 8;
	public static final int fastSpeed = 24;

	boolean mDragMode;
	private View expandedView = null;
	private int expandedPos;

	int mStartPosition;
	int mEndPosition;
	int mDragPointOffset; // Used to adjust drag view location

	ImageView mDragView;
	GestureDetector mGestureDetector;

	DropListener mDropListener;
	RemoveListener mRemoveListener;
	DragListener mDragListener;

	private int lastScrollY = -1;

	public DragNDropListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setDropListener(DropListener l) {
		mDropListener = l;
	}

	public void setRemoveListener(RemoveListener l) {
		mRemoveListener = l;
	}

	public void setDragListener(DragListener l) {
		mDragListener = l;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		final int action = ev.getAction();
		final int x = (int) ev.getX();
		final int y = (int) ev.getY();

		if (action == MotionEvent.ACTION_DOWN && onDragHolder(x, y)) {
			// x > this.getWidth() / 2) {
			mDragMode = true;
		}

		if (!mDragMode)
			return super.onTouchEvent(ev);

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			mStartPosition = pointToPosition(x, y);
			if (mStartPosition != INVALID_POSITION) {
				int mItemPosition = mStartPosition - getFirstVisiblePosition();
				mDragPointOffset = y - getChildAt(mItemPosition).getTop();
				mDragPointOffset -= ((int) ev.getRawY()) - y;
				startDrag(mItemPosition, y);
				drag(0, y);// replace 0 with x if desired

			}
			break;
		case MotionEvent.ACTION_MOVE:
			// Action move can at times batch a bunch of actions
			// together for efficiency purposes. We'd like to act on each.
			final int historySize = ev.getHistorySize();
			for (int h = 0; h < historySize; h++) {
				drag(0, (int) ev.getHistoricalY(h));
			}
			drag(0, y);// replace 0 with x if desired
			break;
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
		default:
			mDragMode = false;
			mEndPosition = pointToPosition(x, y);
			stopDrag(mStartPosition - getFirstVisiblePosition());
			Log.d("dragndrop", "Start " + mStartPosition + " End "
					+ mEndPosition);
			Log.d("dragndrop", "listener: " + mDropListener);
			if (mDropListener != null && mStartPosition != INVALID_POSITION
					&& mEndPosition != INVALID_POSITION)
				mDropListener.onDrop(mStartPosition, getTargetPosition(x, y));
			break;
		}
		return true;
	}

	/**
	 * Given coords x and y, returns true if they are within a drag holder else
	 * false. Caution: This method makes the assumption that the drag holder is
	 * flush against the right edge of the list and spans the entire height of
	 * the list item.
	 */
	private boolean onDragHolder(int x, int y) {
		Log.d("dragndrop", "Got position: " + pointToPosition(x, y));
		final View item = getChildAt(pointToPosition(x, y)
				- getFirstVisiblePosition());
		if (item == null) {
			Log.d("dragndrop", "item was null");
			return false;
		}
		// final View dragItem = item.findViewById(R.id.dragholder);
		final View dragItem = item.findViewById(R.id.dragView);
		if (dragItem == null) {
			Log.d("dragndrop", "dragItem was null");
			return false;
		}

		final int left = item.getRight() - dragItem.getWidth();
		Rect frame = new Rect(left, item.getTop(), item.getRight(),
				item.getBottom());
		Log.d("dragndrop", "item left " + frame.left + " right " + frame.right);
		Log.d("dragndrop", "x " + x + " y " + y);
		Log.d("dragndrop",
				"item right " + item.getRight() + " limit "
						+ (item.getRight() - dragItem.getWidth()));
		return frame.contains(x, y);
	}

	/**
	 * move the drag view
	 * 
	 * When approaching the edges, will also scroll the list. To make the list
	 * only scroll when users drag in the "right" direction, we store the old y
	 * value to determine direction.
	 * 
	 * Views that are targetable (contains a view called dropIndicator or
	 * dropIndicatorNotAdjacent) will be highlighted appropriately.
	 * 
	 * @param x
	 * @param y
	 */
	private void drag(int x, int y) {
		if (mDragView != null) {
			WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) mDragView
					.getLayoutParams();
			layoutParams.x = x;
			layoutParams.y = y - mDragPointOffset;
			WindowManager mWindowManager = (WindowManager) getContext()
					.getSystemService(Context.WINDOW_SERVICE);
			mWindowManager.updateViewLayout(mDragView, layoutParams);

			// change null to "this" when ready to use
			if (mDragListener != null)
				mDragListener.onDrag(x, y, null);

			final int pointPos = pointToPosition(x, y);
			// If we are close to the edges, scroll the list
			// First case if we are at the bottom half all scrolling will be
			// downwards
			final View v;
			if (y > (getHeight() / 2)) {
				final int last = this.getLastVisiblePosition();
				v = getChildAt(last - getFirstVisiblePosition());
				if (v != null) {
					// Only scroll if the finger has moved downwards since last
					// time.
					if (y >= lastScrollY) {
						// If the finger is on the third to last last visible
						// position in the list, scroll slowly.
						if (last - 2 == pointPos) {
							setSelectionFromTop(last, v.getTop() - slowSpeed);
						}
						// If the finger is below the list, or below the third
						// to last item, scroll fast
						else if (-1 == pointPos || last - 2 < pointPos) {
							setSelectionFromTop(last, v.getTop() - fastSpeed);
						}
					}
					// Remember finger position for next time
					lastScrollY = y;
				}
			}
			// Upper half, scrolling upwards
			else {
				final int first = this.getFirstVisiblePosition();
				v = getChildAt(0);
				if (v != null) {
					// Only scroll if the finger has moved upwards since last
					// time.
					if (y <= lastScrollY) {
						// If the finger is on the third visible position in
						// the
						// list, scroll slowly.
						if ((first + 2) == pointPos) {
							setSelectionFromTop(first, v.getTop() + slowSpeed);
						}
						// If the finger is above the list, or on the first
						// position, scroll fast
						else if (first + 2 > pointPos) {
							setSelectionFromTop(first, v.getTop() + fastSpeed);
						}
					}
					// Remember finger position for next time
					lastScrollY = y;
				}
			}

			// Expand and unexpand views as necessary
			// Get the view we are currently targeting
			final int targetPos = getTargetPosition(x, y);
			final View dropTarget = getChildAt(targetPos
					- getFirstVisiblePosition());
			final View dropExpansion;

			if (dropTarget != null) {
				// There are two kinds of spaces. Regular one and one that
				// should not expand if the item originated just below. Just
				// take whichever isn't null
				dropExpansion = dropTarget.findViewById(R.id.dropIndicator) != null ? dropTarget
						.findViewById(R.id.dropIndicator) : dropTarget
						.findViewById(R.id.dropIndicatorNotAdjacent);
			} else {
				dropExpansion = null;
			}
			expand(targetPos, dropExpansion);
		}
	}

	/**
	 * Will only do any thing if the new view is different from the existing
	 * expanded view. It will then start by unexpanding the existing one and
	 * then expand the new view.
	 * 
	 * Safe for null pointers.
	 * 
	 * @param pointPos
	 * @param dropExpansion
	 */
	private void expand(final int pointPos, final View dropExpansion) {
		// Only if the views are different have we gone to a new item
		if (dropExpansion != expandedView) {
			// Only expand if the view is not where we started!
			if (dropExpansion != null && pointPos != mStartPosition) {
				// The second type of space should only expand if the view did
				// not originate from just below it
				if (dropExpansion.getId() == R.id.dropIndicatorNotAdjacent
						&& pointPos != (mStartPosition - 1)
						|| dropExpansion.getId() != R.id.dropIndicatorNotAdjacent) {
					// And expand the new view instead
					dropExpansion.setVisibility(View.VISIBLE);
				}
			}
			unExpand();
			// And save for later
			expandedView = dropExpansion;
			expandedPos = pointPos;
		}
	}

	/**
	 * Will unexpand the expanded view, if it exists.
	 */
	private void unExpand() {
		// We have passed it, so un-expand it
		if (expandedView != null) {
			expandedView.setVisibility(View.INVISIBLE);
			expandedView = null;
			expandedPos = -1;
		}
	}

	/**
	 * Gets the target for the coordinates. The target will be: 1) If the
	 * coordinates are in the upper half of the view the target will be the item
	 * above the view. If the view is the first item, then it is the target.
	 * 
	 * 2) If the coordinates are in the lower half of the view, the view itself
	 * is the target.
	 * 
	 * @param x
	 * @param y
	 * @return the position in the list which is the target
	 */
	private int getTargetPosition(final int x, final int y) {
		final int realPos = pointToPosition(x, y);
		if ((realPos - 1) == mStartPosition)
			return realPos;

		if (realPos == -1) {
			if (expandedPos == 0 || expandedPos == getCount() - 1)
				return expandedPos;
			else
				return realPos;
		}

		final View realView = getChildAt(realPos - getFirstVisiblePosition());
		if (realView == null)
			return realPos;

		final int top = realView.getTop();
		if (y < top + realView.getHeight() / 2)
			return realPos < 1 ? realPos : realPos - 1;
		else
			return realPos;
	}

	// enable the drag view for dragging
	private void startDrag(int itemIndex, int y) {
		stopDrag(itemIndex);

		View item = getChildAt(itemIndex);
		Log.d("dragdrop", "getting bitmap from " + itemIndex);
		Log.d("dragdrop", "my calc: " + pointToPosition(0, y) + " = "
				+ (pointToPosition(0, y) - getFirstVisiblePosition()));
		if (item == null)
			return;
		item.setDrawingCacheEnabled(true);
		if (mDragListener != null)
			mDragListener.onStartDrag(item);

		// Create a copy of the drawing cache so that it does not get recycled
		// by the framework when the list tries to clean up memory
		Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
		item.setDrawingCacheEnabled(false);

		WindowManager.LayoutParams mWindowParams = new WindowManager.LayoutParams();
		mWindowParams.gravity = Gravity.TOP;
		mWindowParams.x = 0;
		mWindowParams.y = y - mDragPointOffset;

		mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		mWindowParams.format = PixelFormat.TRANSLUCENT;
		mWindowParams.windowAnimations = 0;

		Context context = getContext();
		ImageView v = new ImageView(context);
		v.setImageBitmap(bitmap);

		WindowManager mWindowManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.addView(v, mWindowParams);
		mDragView = v;
	}

	// destroy drag view
	private void stopDrag(int itemIndex) {
		if (mDragView != null) {
			if (mDragListener != null)
				mDragListener.onStopDrag(getChildAt(itemIndex));
			mDragView.setVisibility(GONE);
			WindowManager wm = (WindowManager) getContext().getSystemService(
					Context.WINDOW_SERVICE);
			wm.removeView(mDragView);
			mDragView.setImageDrawable(null);
			mDragView = null;
		}
		unExpand();
	}

	// private GestureDetector createFlingDetector() {
	// return new GestureDetector(getContext(), new SimpleOnGestureListener() {
	// @Override
	// public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
	// float velocityY) {
	// if (mDragView != null) {
	// int deltaX = (int)Math.abs(e1.getX()-e2.getX());
	// int deltaY = (int)Math.abs(e1.getY() - e2.getY());
	//
	// if (deltaX > mDragView.getWidth()/2 && deltaY < mDragView.getHeight()) {
	// mRemoveListener.onRemove(mStartPosition);
	// }
	//
	// stopDrag(mStartPosition - getFirstVisiblePosition());
	//
	// return true;
	// }
	// return false;
	// }
	// });
	// }
}
