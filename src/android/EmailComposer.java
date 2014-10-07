/*
    Copyright 2013-2014 appPlant UG

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
*/

package de.appplant.cordova.plugin.emailcomposer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.text.Html;
import android.util.Base64;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

public class EmailComposer extends CordovaPlugin {

    static protected final String STORAGE_FOLDER = File.separator + "email_composer";

    private CallbackContext command;

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread.
     * To do a non-trivial amount of work, use:
     *     cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action   The action to execute.
     * @param args     The exec() arguments in JSON form.
     * @param callback The callback context used when calling
     *                 back into JavaScript.
     * @return         Whether the action was valid.
     */
    @Override
    public boolean execute (String action, JSONArray args,
                            CallbackContext callback) throws JSONException {

        this.command = callback;

        if ("open".equals(action)) {
            open(args);

            return true;
        }

        if ("isServiceAvailable".equals(action)) {
            isServiceAvailable();

            return true;
        }

        // Returning false results in a "MethodNotFound" error.
        return false;
    }

    /**
     * Tells if the device has the capability to send emails.
     */
    private void isServiceAvailable () {
        Boolean available   = isEmailAccountConfigured();
        PluginResult result = new PluginResult(PluginResult.Status.OK, available);

        command.sendPluginResult(result);
    }

    /**
     * Sends an intent to the email app.
     *
     * @param args
     *      The email properties like subject or body
     *
     * @throws JSONException
     */
    private void open (JSONArray args) throws JSONException {
        JSONObject properties = args.getJSONObject(0);
        final Intent draft = getDraftWithProperties(properties);
        final EmailComposer plugin = this;

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                cordova.startActivityForResult(
                        plugin, Intent.createChooser(draft, "Select Email App"), 0);
            }
        });
    }

    /**
     * The intent with the containing email properties.
     *
     * @param params
     *      The email properties like subject or body
     * @return
     *      The resulting intent
     *
     * @throws JSONException
     */
    private Intent getDraftWithProperties (JSONObject params) throws JSONException {
        Intent mail = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);

        if (params.has("subject"))
            setSubject(params.getString("subject"), mail);
        if (params.has("body"))
            setBody(params.getString("body"), params.optBoolean("isHtml"), mail);
        if (params.has("to"))
            setRecipients(params.getJSONArray("to"), mail);
        if (params.has("cc"))
            setCcRecipients(params.getJSONArray("cc"), mail);
        if (params.has("bcc"))
            setBccRecipients(params.getJSONArray("bcc"), mail);
        if (params.has("attachments"))
            setAttachments(params.getJSONArray("attachments"), mail);

        mail.setType("application/octet-stream");

        return mail;
    }

    /**
     * Setter for the subject.
     *
     * @param subject
     *      The subject
     * @param draft
     *      The intent
     */
    private void setSubject (String subject, Intent draft) {
        draft.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
    }

    /**
     * Setter for the body.
     *
     * @param body
     *      The body
     * @param isHTML
     *      Indicates the encoding
     *      (HTML or plain text)
     * @param draft
     *      The intent
     */
    private void setBody (String body, Boolean isHTML, Intent draft) {
        if (isHTML) {
            draft.putExtra(android.content.Intent.EXTRA_TEXT, Html.fromHtml(body));
            draft.setType("text/html");
        } else {
            draft.putExtra(android.content.Intent.EXTRA_TEXT, body);
            draft.setType("text/plain");
        }
    }

    /**
     * Setter for the recipients.
     *
     * @param recipients
     *      List of email addresses
     * @param draft
     *      The intent
     *
     * @throws JSONException
     */
    private void setRecipients (JSONArray recipients, Intent draft) throws JSONException {
        String[] receivers = new String[recipients.length()];

        for (int i = 0; i < recipients.length(); i++) {
            receivers[i] = recipients.getString(i);
        }

        draft.putExtra(android.content.Intent.EXTRA_EMAIL, receivers);
    }

    /**
     * Setter for the cc recipients.
     *
     * @param recipients
     *      List of email addresses
     * @param draft
     *      The intent
     *
     * @throws JSONException
     */
    private void setCcRecipients (JSONArray recipients, Intent draft) throws JSONException {
        String[] receivers = new String[recipients.length()];

        for (int i = 0; i < recipients.length(); i++) {
            receivers[i] = recipients.getString(i);
        }

        draft.putExtra(android.content.Intent.EXTRA_CC, receivers);
    }

    /**
     * Setter for the bcc recipients.
     *
     * @param recipients
     *      List of email addresses
     * @param draft
     *      The intent
     *
     * @throws JSONException
     */
    private void setBccRecipients (JSONArray recipients, Intent draft) throws JSONException {
        String[] receivers = new String[recipients.length()];

        for (int i = 0; i < recipients.length(); i++) {
            receivers[i] = recipients.getString(i);
        }

        draft.putExtra(android.content.Intent.EXTRA_BCC, receivers);
    }

    /**
     * Setter for the attachments.
     *
     * @param attachments
     *      List of URIs
     * @param draft
     *      The intent
     *
     * @throws JSONException
     */
    private void setAttachments (JSONArray attachments, Intent draft) throws JSONException {
        ArrayList<Uri> uris = new ArrayList<Uri>();

        for (int i = 0; i < attachments.length(); i++) {
            Uri uri = getUriForPath(attachments.getString(i));

            uris.add(uri);
        }

        draft.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    }

    /**
     * If email apps are available.
     *
     * @return
     *      true if available, otherwise false
     */
    private Boolean isEmailAccountConfigured () {
        Uri uri = Uri.fromParts("mailto","max@mustermann.com", null);
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        PackageManager pm = cordova.getActivity().getPackageManager();
        Boolean available;

        available =
                pm.queryIntentActivities(intent, 0).size() > 1;

        return available;
    }

    /**
     * The URI for an attachment path.
     *
     * @param path
     *      The given path to the attachment
     *
     * @return
     *      The URI pointing to the given path
     */
    private Uri getUriForPath (String path) {
        if (path.startsWith("res:")) {
            return getUriForResourcePath(path);
        } else if (path.startsWith("file:")) {
            return getUriForAbsolutePath(path);
        } else if (path.startsWith("www:")) {
            return getUriForAssetPath(path);
        } else if (path.startsWith("base64:")) {
            return getUriForBase64Content(path);
        }

        return Uri.parse(path);
    }

    /**
     * The URI for a file.
     *
     * @param path
     *      The given absolute path
     *
     * @return
     *      The URI pointing to the given path
     */
    private Uri getUriForAbsolutePath (String path) {
        String absPath = path.replaceFirst("file://", "");
        File file      = new File(absPath);

        if (!file.exists()) {
            System.err.println("Attachment path not found: " + file.getAbsolutePath());
        }

        return Uri.fromFile(file);
    }

    /**
     * The URI for an asset.
     *
     * @param path
     *      The given asset path
     *
     * @return
     *      The URI pointing to the given path
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Uri getUriForAssetPath (String path) {
        String resPath  = path.replaceFirst("www:/", "www");
        String fileName = resPath.substring(resPath.lastIndexOf('/') + 1);
        File dir        = cordova.getActivity().getExternalCacheDir();

        if (dir == null)
            return null;

        String storage  = dir.toString() + STORAGE_FOLDER;
        File file       = new File(storage, fileName);

        new File(storage).mkdir();

        try {
            AssetManager assets = cordova.getActivity().getAssets();

            FileOutputStream outStream = new FileOutputStream(file);
            InputStream inputStream    = assets.open(resPath);

            copyFile(inputStream, outStream);
            outStream.flush();
            outStream.close();
        } catch (Exception e) {
            System.err.println("Attachment asset not found: assets/" + resPath);
            e.printStackTrace();
        }

        return Uri.fromFile(file);
    }

    /**
     * The URI for a resource.
     *
     * @param path
     *      The given relative path
     *
     * @return
     *      The URI pointing to the given path
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Uri getUriForResourcePath (String path) {
        String resPath   = path.replaceFirst("res://", "");
        String fileName  = resPath.substring(resPath.lastIndexOf('/') + 1);
        String resName   = fileName.substring(0, fileName.lastIndexOf('.'));
        String extension = resPath.substring(resPath.lastIndexOf('.'));
        File dir         = cordova.getActivity().getExternalCacheDir();

        if (dir == null)
            return null;

        String storage   = dir.toString() + STORAGE_FOLDER;
        int resId        = getResId(resPath);
        File file        = new File(storage, resName + extension);

        if (resId == 0) {
            System.err.println("Resource not found: " + resPath);
        }

        new File(storage).mkdir();

        try {
            Resources res = cordova.getActivity().getResources();
            FileOutputStream outStream = new FileOutputStream(file);
            InputStream inputStream    = res.openRawResource(resId);

            copyFile(inputStream, outStream);
            outStream.flush();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Uri.fromFile(file);
    }

    /**
     * The URI for a base64 encoded content.
     *
     * @param content
     *      The given base64 encoded content
     *
     * @return
     *      The URI including the given content
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Uri getUriForBase64Content (String content) {
        String resName = content.substring(content.indexOf(":") + 1, content.indexOf("//"));
        String resData = content.substring(content.indexOf("//") + 2);
        byte[] bytes   = Base64.decode(resData, 0);
        File dir       = cordova.getActivity().getExternalCacheDir();

        if (dir == null)
            return null;

        String storage = dir.toString() + STORAGE_FOLDER;
        File file      = new File(storage, resName);

        new File(storage).mkdir();

        try {
            FileOutputStream outStream = new FileOutputStream(file);

            outStream.write(bytes);
            outStream.flush();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String pkgName = getPackageName();
        String uriPath = pkgName + AttachmentProvider.AUTHORITY + "/" + resName;

        return Uri.parse("content://" + uriPath);
    }

    /**
     * Writes an InputStream to an OutputStream
     *
     * @param in
     *      The input stream
     * @param out
     *      The output stream
     */
    private void copyFile (InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;

        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    /**
     * @return
     *      The resource ID for the given resource.
     */
    private int getResId (String resPath) {
        Resources res = cordova.getActivity().getResources();
        int resId;

        String pkgName  = getPackageName();
        String dirName  = "drawable";
        String fileName = resPath;

        if (resPath.contains("/")) {
            dirName  = resPath.substring(0, resPath.lastIndexOf('/'));
            fileName = resPath.substring(resPath.lastIndexOf('/') + 1);
        }

        String resName = fileName.substring(0, fileName.lastIndexOf('.'));

        resId = res.getIdentifier(resName, dirName, pkgName);

        if (resId == 0) {
            resId = res.getIdentifier(resName, "drawable", pkgName);
        }

        return resId;
    }

    /**
     * @return
     *      The name for the package.
     */
    private String getPackageName () {
        return cordova.getActivity().getPackageName();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        command.success();
    }
}
