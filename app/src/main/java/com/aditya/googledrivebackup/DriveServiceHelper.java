package com.aditya.googledrivebackup;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;

import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
public class DriveServiceHelper {
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;
    private final String mailid ;

    public DriveServiceHelper(Drive driveService, String mailId) {
        mDriveService = driveService;
        mailid = mailId;
    }

    /**
     * Creates a backup file in the user's My Drive folder and returns its file ID.
     */
    public Task<Void> createFile() {
        return Tasks.call(mExecutor, () -> {
            String folderId = createFolder();

            String sharedFolderId = createSharedFolder(folderId);
            createSharedFolderFiles(sharedFolderId);

            //TODO :- Here in setQ we have to provide the file name and
            // in file uri we have to provide the exact location of that file in mobile.
            FileList result = mDriveService.files().list()
                    .setQ("mimeType = 'application/octet-stream' and name = 'databaseName' and parents = '" + folderId + "' ")
                    .setSpaces("drive")
                    .execute();
            if (result.getFiles().size() > 0) {
                String fileid = result.getFiles().get(0).getId();
                Log.d("Adi", "createFile: " +"Already Exist");
                Uri fileUri = Uri.fromFile(new java.io.File(Environment.getDataDirectory().getPath()
                        + "App Location"));

                java.io.File fileContent = new java.io.File(fileUri.getPath());

                FileContent mediaContent = new FileContent("application/octet-stream", fileContent);
                File body = new File();
                File file = mDriveService.files().update(fileid,body,mediaContent).execute();
                Log.d("Adi", "createFile: "+"Updated Succesfully");

            } else {
                Uri fileUri = Uri.fromFile(new java.io.File(Environment.getDataDirectory().getPath()
                        + "App Location"));

                java.io.File fileContent = new java.io.File(fileUri.getPath());

                FileContent mediaContent = new FileContent("application/octet-stream", fileContent);
                File body = new File();
                body.setName(fileContent.getName());
                body.setMimeType("application/octet-stream");
                body.setParents(Collections.singletonList(folderId));
                File file = mDriveService.files().create(body,mediaContent).execute();
            }
            return null;
        });
    }

    public void createSharedFolderFiles(String folderid) throws IOException {

        //TODO - we have to change the path in file URI
        Uri fileUri = Uri.fromFile(new java.io.File(Environment.getDataDirectory().getPath()
                + "/data/com.aditya.googledrivebackup/shared_prefs"));

        java.io.File f = new java.io.File(fileUri.getPath());

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(java.io.File file, String name) {
                return name.endsWith(".xml");
            }
        };

        // Note that this time we are using a File class as an array,
        java.io.File[] files = f.listFiles(filter);

        FileList result = mDriveService.files().list()
                .setQ("mimeType = 'application/xml' and parents = '" + folderid + "' ")
                .setSpaces("drive")
                .execute();
        if (result.getFiles().size() > 0) {
            int size = result.getFiles().size();
            for (int i = 0; i < files.length; i++) {
                String fileid = result.getFiles().get(i).getId();
                System.out.println(files[i].getName());
                if(files[i].getName().equals(result.getFiles().get(size-1-i).getName())){
                    FileContent mediaContent = new FileContent("application/xml", files[i]);
                    File body = new File();
                    File file = mDriveService.files().update(fileid,body, mediaContent).execute();
                    Log.d("Adi", "createSharedFolderFiles: " + "Succesfully Updated");
                    System.out.println(files[i].getName());
                }
            }
        }
        else {
            // Get the names of the files by using the .getName() method
            for (int i = 0; i < files.length; i++) {
                System.out.println(files[i].getName());
                FileContent mediaContent = new FileContent("application/xml", files[i]);
                File body = new File();
                body.setName(files[i].getName());
                body.setMimeType("application/xml");
                body.setParents(Collections.singletonList(folderid));
                File file = mDriveService.files().create(body, mediaContent).execute();
                Log.d("Adi", "createSharedFolderFiles: " + "Succesfully created");
            }
        }
    }

    /**
     * Creates a Folder in the user's My Drive folder and returns its folder ID.
     */
    public String createFolder() throws IOException {

        FileList result = mDriveService.files().list()
                .setQ("mimeType = '" + "application/vnd.google-apps.folder" + "' and name = '" + "Backup"+ "' ")
                .setSpaces("drive")
                .execute();

        if (result.getFiles().size() > 0) {
            Log.d("Adi", "createFolder: " + "Already Exists");
            return result.getFiles().get(0).getId();

        }
        else {

            File metadata = new File()
                    .setMimeType("application/vnd.google-apps.folder")
                    .setName("Backup");

            File googleFile = mDriveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }
            givePermission(googleFile.getId());
            return googleFile.getId();
        }
    }

    public String createSharedFolder(String folderId) throws IOException {
        //TODO - we have to change in setQ,name of the folder
        FileList result = mDriveService.files().list()
                .setQ("mimeType = 'application/vnd.google-apps.folder' and name = 'shared_prefs' and parents = '" + folderId + "' ")
                .setSpaces("drive")
                .execute();

        if (result.getFiles().size() > 0) {
            Log.d("Adi", "createFolder: " + "Already Exists");
            return result.getFiles().get(0).getId();

        }
        else {

            File metadata = new File()
                    .setParents(Collections.singletonList(folderId))
                    .setMimeType("application/vnd.google-apps.folder")
                    .setName("shared_prefs");

            File googleFile = mDriveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }
            givePermission(googleFile.getId());
            return googleFile.getId();
        }
    }
    private void givePermission(String fileId) throws IOException {
        JsonBatchCallback<Permission> callback = new JsonBatchCallback<Permission>() {
            @Override
            public void onFailure(GoogleJsonError e,
                                  HttpHeaders responseHeaders)
                    throws IOException {
                // Handle error
                System.err.println(e.getMessage());
            }

            @Override
            public void onSuccess(Permission permission,
                                  HttpHeaders responseHeaders)
                    throws IOException {
                System.out.println("Permission ID: " + permission.getId());
            }
        };
        BatchRequest batch = mDriveService.batch();
        Permission userPermission = new Permission()
                .setType("user")
                .setRole("writer")
                .setEmailAddress(mailid);
        mDriveService.permissions().create(fileId, userPermission)
                .setFields("id")
                .queue(batch, callback);
    }


    public Task<String> queryFiles() throws IOException {
        return Tasks.call(mExecutor, () -> {
            //TODO - we have to change in setq the name of the folder
            FileList result = mDriveService.files().list()
                    .setQ("mimeType = '" + "application/vnd.google-apps.folder" + "' and name = '" + "Backup"+ "' ")
                    .setSpaces("drive")
                    .execute();

            if (result.getFiles().size() > 0) {
                Log.d("Adi", "Query: " + "SuccessFul");
                DateTime date =  result.getFiles().get(0).getCreatedTime();
                DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");
                String strDate = dateFormat.format(date.getValue());
                Log.d("Adi", "queryFiles: "+strDate);
                return strDate;
            }
            return null;
        });
    }

    public Task<Void> downloadFile() {
        return Tasks.call(mExecutor, ()-> {
            //TODO - we have to change the path in file URI
            String fileId = "";
            Uri fileUri = Uri.fromFile(new java.io.File(Environment.getDataDirectory().getPath()
                    + "/data/com.aditya.googledrivebackup/databases/"));

            java.io.File fileSaveLocation = new java.io.File(fileUri.getPath(),"moods-db");
            if(fileSaveLocation.exists()){
                Log.d("Adi", "downloadFile: "+fileSaveLocation.getPath());
                Log.d("Adi", "downloadFile: File Already Exist");
                fileSaveLocation.delete();
                Log.d("Adi", "downloadFile: File Deleted");
            }

            FileList result = mDriveService.files().list()
                    .setQ("mimeType = 'application/octet-stream' and name = '" +"moods-db"+"' ")
                    .setSpaces("drive")
                    .execute();

            if(result.getFiles().size()>0){
                Log.d("Adi", "downloadFile: "+"We Found The File");
                fileId = result.getFiles().get(0).getId();
            }

            // Retrieve the metadata as a File object.
            OutputStream outputStream = new FileOutputStream(fileSaveLocation);
            mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            Log.d("Adi", "downloadFile: "+"Downloaded");

            downloadSharedPrefs();
            return null;
        });
    }

    public void downloadSharedPrefs() throws IOException {
        String fileId="";
        Uri fileUri = Uri.fromFile(new java.io.File(Environment.getDataDirectory().getPath()
                + "/data/com.mood.tracker/shared_prefs/"));

        java.io.File f = new java.io.File(fileUri.getPath());

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(java.io.File file, String name) {
                return name.endsWith(".xml");
            }
        };

        // Note that this time we are using a File class as an array,
        java.io.File[] files = f.listFiles(filter);

        // We are checking if file already exists in device folder or not and downloading each file from the gdrive
        for (int i = 0; i < files.length; i++) {
            if(files[i].exists()){
                files[i].delete();
            }
            FileList result = mDriveService.files().list()
                    .setQ("mimeType = 'application/xml' and name = '" +files[i].getName()+"' ")
                    .setSpaces("drive")
                    .execute();

            if(result.getFiles().size()>0){
                Log.d("Adi", "downloadFile: "+"We Found The File");
                fileId = result.getFiles().get(0).getId();
            }
            java.io.File saveLocation = new java.io.File(f.getPath(),files[i].getName());
            // Retrieve the metadata as a File object.
            OutputStream outputStream = new FileOutputStream(saveLocation);
            mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            Log.d("Adi", "downloadFile: "+"Downloaded");

        }
    }

}
