package com.nononsenseapps.notepad;

import java.util.Collection;

public interface OnEditorDeleteListener {
	public void onEditorDelete(long id);
	public void onMultiDelete(Collection<Long> ids, long curId);
}
