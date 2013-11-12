package com.u17od.upm;

public class SyncDatabaseViaDriveActivity extends SyncDatabaseActivity {

	@Override
	protected void uploadDatabase() {
		UIUtilities.showToast(SyncDatabaseViaDriveActivity.this, "uploadDatabase", false);		
	}

	@Override
	protected void downloadDatabase() {
		UIUtilities.showToast(SyncDatabaseViaDriveActivity.this, "downloadDatabase", false);
	}

}
