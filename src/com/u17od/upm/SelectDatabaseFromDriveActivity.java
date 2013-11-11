package com.u17od.upm;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SelectDatabaseFromDriveActivity extends ListActivity {

    static final int REQUEST_ACCOUNT_PICKER = 1;
    static final int REQUEST_AUTHORIZATION = 2;

    private static Uri fileUri;
    private static Drive service;
    private GoogleAccountCredential credential;
    
    private ProgressDialog progressDialog;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drive_file_list);

        credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                	//todo: VPost fix this  
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        service = getDriveService(credential);
                        new DownloadListOfFilesTask().execute();
                    }
                }
                break;
                
                //todo: remove this 
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    new DownloadListOfFilesTask().execute();
                } else {
                    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                }
                break;
            /*case CAPTURE_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
                    saveFileToDrive();
                }*/
        }
    }

    private class DownloadListOfFilesTask extends AsyncTask<Void, Void, Integer> {

        private static final int ERROR_CODE_DB_EXCEPTION = 2;

        private FileList rootMetadata;
        
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SelectDatabaseFromDriveActivity.this,
                    "", getString(R.string.drive_get_file_list));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
            	Files.List request = service.files().list();
                rootMetadata = request.execute();
                return 0;
            }
            catch (UserRecoverableAuthIOException e) {
        	  startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
        	  return REQUEST_AUTHORIZATION;
        	}
            catch(IOException e){
            	Log.e("AppEntryActivity", "Problem communicating with Drive", e);
                return ERROR_CODE_DB_EXCEPTION;
            }
            catch(Exception e){
            	Log.e("AppEntryActivity", "Problem communicating with Drive", e);
                return ERROR_CODE_DB_EXCEPTION;
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            switch (result) {
                case 0:
                    setListAdapter(new ArrayAdapter<String>(
                            SelectDatabaseFromDriveActivity.this,
                            android.R.layout.simple_list_item_1,
                            driveFiles(rootMetadata)));
                    break;
               
                case ERROR_CODE_DB_EXCEPTION:
                    UIUtilities.showToast(SelectDatabaseFromDriveActivity.this,
                            R.string.drive_problem, true);
                    break;
            }
        }
        
        /*
         * Extract the filenames from the given list of Drive Entries and return
         * a simple String array.
         */
        private ArrayList<String> driveFiles(FileList files) {
        	List<File> result = new ArrayList<File>(files.getItems());
        	ArrayList<String> fileNames = new ArrayList<String>();
        	
            for (File entry : result) {
                 fileNames.add(entry.getOriginalFilename());
            }
            return fileNames;
        }

    }
    
    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                .build();
    }    
}
