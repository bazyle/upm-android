package com.u17od.upm;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.u17od.upm.database.PasswordDatabase;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class SelectDatabaseFromDriveActivity extends ListActivity {

	private static final int REQUEST_ACCOUNT_PICKER = 1;
    private static final int REQUEST_AUTHORIZATION = 2;
    private static final int ENTER_PW_REQUEST_CODE = 111;
    
    private static Drive service;
    private static String parentId;
    private static String accountName; 
    private GoogleAccountCredential credential;
    
    private ProgressDialog progressDialog;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drive_file_list);
        accountName = Utilities.getConfig(this, Utilities.DRIVE_PREFS, Utilities.DRIVE_SECRET);
        credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
        if(accountName == null || accountName.length() == 0){
	        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
        }else{
        	credential.setSelectedAccountName(accountName);
            service = getDriveService(credential);
            new DownloadListOfFilesTask().execute();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                	//todo: VPost fix this  
                    accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                    	Utilities.setConfig(this, Utilities.DRIVE_PREFS, Utilities.DRIVE_SECRET, accountName);
                        credential.setSelectedAccountName(accountName);
                        service = getDriveService(credential);
                        new DownloadListOfFilesTask().execute();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    new DownloadListOfFilesTask().execute();
                } else {
                    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                }
                break;
                
            case Activity.RESULT_CANCELED:
                    UIUtilities.showToast(this, R.string.enter_password_cancalled);
                    break;
            case ENTER_PW_REQUEST_CODE :
                        // Setting the DatabaseFileName preference effectively says
                        // that this is the db to open when the app starts
                        Utilities.setSyncMethod(Prefs.SyncMethod.DRIVE, this);
                        String selectedDropboxFilename =
                                Utilities.getConfig(this, Utilities.DRIVE_PREFS,
                                        Utilities.DRIVE_SELECTED_FILENAME);
                        Utilities.setDatabaseFileName(selectedDropboxFilename,
                                SelectDatabaseFromDriveActivity.this);

                        // Put a reference to the decrypted database on the Application object
                        UPMApplication app = (UPMApplication) getApplication();
                        app.setPasswordDatabase(EnterMasterPassword.decryptedPasswordDatabase);
                        app.setTimeOfLastSync(new Date());

                        setResult(RESULT_OK);
                        finish();
                        break;
        }
    }

    /**
     * Called when an file from the listview is selected
     */
    @Override
    protected void onListItemClick(ListView lv, View v, int position, long id) {
        String selectedFileName = (String) lv.getItemAtPosition(position);
        Utilities.setConfig(this, Utilities.DRIVE_PREFS,
                Utilities.DRIVE_SELECTED_FILENAME, selectedFileName);
        new DownloadFileTask().execute(selectedFileName);
    	
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
    
    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                .build();
    }    
    
    private class DownloadListOfFilesTask extends AsyncTask<Void, Void, Integer> {

        private static final int ERROR_CODE_DB_EXCEPTION = 1;

        private FileList rootMetadata;
        
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SelectDatabaseFromDriveActivity.this,
                    "", getString(R.string.drive_get_file_list));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
            	
            	parentId = Utilities.getConfig(SelectDatabaseFromDriveActivity.this, Utilities.DRIVE_PREFS, Utilities.DRIVE_UPMFOLDER_ID);
            	if(parentId == null || parentId.length() == 0){
            		Files.List parentFolderRequest = service.files().list().setQ("mimeType = 'application/vnd.google-apps.folder' and title = 'UPM'");
            		FileList parentFolder = parentFolderRequest.execute();
            		List<File> fs =  parentFolder.getItems();
            		File parent = fs.get(0);
            		parentId = parent.getId();
            	}
            	
            	Files.List request = service.files().list().setQ("'"+parentId+"' in parents and trashed = false");
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
                case REQUEST_AUTHORIZATION:
                	UIUtilities.showToast(SelectDatabaseFromDriveActivity.this,
                            "request permission", true);
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
            	if(entry.getDownloadUrl() != null && entry.getDownloadUrl().length() > 0){
            		fileNames.add(entry.getOriginalFilename());
            	}
            }
            return fileNames;
        }

    }
    
    private class DownloadFileTask extends AsyncTask<String, Void, Integer> {

        private static final int ERROR_IO_ERROR = 1;
        private static final int ERROR_DRIVE_ERROR = 2;
        private static final int NOT_UPM_DB = 3;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SelectDatabaseFromDriveActivity.this,
                    "", getString(R.string.downloading_db));
        }

        @Override
        protected Integer doInBackground(String... fileName) {
            FileOutputStream outputStream = null;
            try {
                // Download the file and save it to UPM's internal files area
            	String fName = fileName[0];
            	java.io.File file = new java.io.File(getFilesDir(), fName);
                Files.List request = service.files().list().setQ("'"+ parentId +"' in parents and trashed = false and title= '"+ fName +"'");
                FileList result = request.execute();
                File driveFile = result.getItems().get(0);
            	
                if (driveFile.getDownloadUrl() != null && driveFile.getDownloadUrl().length() > 0) {
                 	
                    outputStream = new FileOutputStream(file);
                    
            		downloadFile(driveFile, outputStream);
        	        
            	} else {
            		return ERROR_DRIVE_ERROR;
            	}
            	
            	 // Check this is a UPM database file
                if (!PasswordDatabase.isPasswordDatabase(file)) {
                    return NOT_UPM_DB;
                }
                
                EnterMasterPassword.databaseFileToDecrypt = file;

                return 0;
	            } catch (IOException e) {
	                return ERROR_IO_ERROR;
	            } catch (Exception e) {
	                return ERROR_DRIVE_ERROR;
	            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        return ERROR_IO_ERROR;
                    }
                }
            }
        }
        
        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            Intent i = null;
            switch (result) {
                case 0:

                    // Call up the EnterMasterPassword activity
                    // When it returns we'll pick up in the method onActivityResult
                    i = new Intent(SelectDatabaseFromDriveActivity.this, EnterMasterPassword.class);
                    startActivityForResult(i, ENTER_PW_REQUEST_CODE);
                    break;
                case ERROR_IO_ERROR:
                    UIUtilities.showToast(SelectDatabaseFromDriveActivity.this,
                            R.string.problem_saving_db, true);
                    break;
                case ERROR_DRIVE_ERROR:
                    UIUtilities.showToast(SelectDatabaseFromDriveActivity.this,
                            R.string.drive_problem, true);
                    break;
                case NOT_UPM_DB:
                    UIUtilities.showToast(SelectDatabaseFromDriveActivity.this,
                            R.string.not_password_database, true);
                    break;
            }
        }
       
        private void downloadFile(File driveFile, FileOutputStream outputStream) throws IOException{
        	 HttpResponse resp =
     	            service.getRequestFactory().buildGetRequest(new GenericUrl(driveFile.getDownloadUrl()))
     	                .execute();
     	         InputStream  inputStream =  resp.getContent();
     	        int c;
     	        while ((c = inputStream.read()) != -1) {
     	          
     	          outputStream.write(c);
     	        }
     	        inputStream.close();
        }
    }
    
   
}
