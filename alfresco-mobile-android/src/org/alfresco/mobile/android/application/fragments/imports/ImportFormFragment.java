/*******************************************************************************
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 * 
 * This file is part of Alfresco Mobile for Android.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.alfresco.mobile.android.application.fragments.imports;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.alfresco.mobile.android.api.session.AlfrescoSession;
import org.alfresco.mobile.android.application.ApplicationManager;
import org.alfresco.mobile.android.application.BaseActivity;
import org.alfresco.mobile.android.application.HomeScreenActivity;
import org.alfresco.mobile.android.application.PublicDispatcherActivity;
import org.alfresco.mobile.android.application.R;
import org.alfresco.mobile.android.application.accounts.Account;
import org.alfresco.mobile.android.application.accounts.AccountProvider;
import org.alfresco.mobile.android.application.accounts.AccountSchema;
import org.alfresco.mobile.android.application.accounts.fragment.AccountCursorAdapter;
import org.alfresco.mobile.android.application.exception.AlfrescoAppException;
import org.alfresco.mobile.android.application.intent.IntentIntegrator;
import org.alfresco.mobile.android.application.manager.ActionManager;
import org.alfresco.mobile.android.application.manager.StorageManager;
import org.alfresco.mobile.android.application.utils.AndroidVersion;
import org.alfresco.mobile.android.application.utils.thirdparty.LocalBroadcastManager;
import org.alfresco.mobile.android.ui.filebrowser.LocalFileExplorerAdapter;
import org.alfresco.mobile.android.ui.manager.MessengerManager;

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.Spinner;

/**
 * Display the form to choose account and import folder.
 * 
 * @author Jean Marie Pascal
 */
public class ImportFormFragment extends Fragment implements LoaderCallbacks<Cursor>
{

    public static final String TAG = "ImportFormFragment";

    private Cursor selectedAccountCursor;

    private String fileName;

    private File file;

    private View rootView;

    private Integer folderImportId;

    private int importFolderIndex;

    /** Principal ListView of the fragment */
    protected ListView lv;

    protected ArrayAdapter<?> adapter;

    protected CursorAdapter cursorAdapter;

    protected int selectedPosition;

    protected List<File> files = new ArrayList<File>();

    private Spinner spinnerDoc;

    private Spinner spinnerAccount;

    public static ImportFormFragment newInstance(Bundle b)
    {
        ImportFormFragment fr = new ImportFormFragment();
        fr.setArguments(b);
        return fr;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getActivity().setTitle(R.string.import_document_title);
        
        rootView = inflater.inflate(R.layout.app_import, container, false);
        if (rootView.findViewById(R.id.listView) != null)
        {
            initDocumentList(rootView);
        }
        else
        {
            initiDocumentSpinner(rootView);
        }

        spinnerAccount = (Spinner) rootView.findViewById(R.id.accounts_spinner);
        spinnerAccount.setOnItemSelectedListener(new OnItemSelectedListener()
        {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
            {
                selectedAccountCursor = (Cursor) parent.getItemAtPosition(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
                // Do nothing
            }
        });
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        cursorAdapter = new AccountCursorAdapter(getActivity(), null, R.layout.sdk_list_row, null);
        spinnerAccount.setAdapter(cursorAdapter);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        Intent intent = getActivity().getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        files.clear();

        try
        {
            if (Intent.ACTION_SEND_MULTIPLE.equals(action))
            {
                ClipData clipdata = intent.getClipData();
                if (clipdata != null && clipdata.getItemCount() > 1)
                {
                    Item item = null;
                    for (int i = 0; i < clipdata.getItemCount(); i++)
                    {
                        item = clipdata.getItemAt(i);
                        Uri uri = item.getUri();
                        if (uri != null)
                        {
                            retrieveIntentInfo(uri);
                        }
                        else
                        {
                            String timeStamp = new SimpleDateFormat("yyyyddMM_HHmmss").format(new Date());
                            File localParentFolder = StorageManager.getCacheDir(getActivity(), "AlfrescoMobile/import");
                            File f = createFile(localParentFolder, timeStamp + ".txt", item.getText().toString());
                            if (f.exists())
                            {
                                retrieveIntentInfo(Uri.fromFile(f));
                            }
                        }
                        if (!files.contains(file))
                        {
                            files.add(file);
                        }
                    }
                }
            }
            else
            {
                // Manage only one clip data. If multiple we ignore.
                if (AndroidVersion.isJBOrAbove() && (!Intent.ACTION_SEND.equals(action) || type == null))
                {
                    ClipData clipdata = intent.getClipData();
                    if (clipdata != null && clipdata.getItemCount() == 1 && clipdata.getItemAt(0) != null
                            && (clipdata.getItemAt(0).getText() != null || clipdata.getItemAt(0).getUri() != null))
                    {
                        Item item = clipdata.getItemAt(0);
                        Uri uri = item.getUri();
                        if (uri != null)
                        {
                            retrieveIntentInfo(uri);
                        }
                        else
                        {
                            String timeStamp = new SimpleDateFormat("yyyyddMM_HHmmss").format(new Date());
                            File localParentFolder = StorageManager.getCacheDir(getActivity(), "AlfrescoMobile/import");
                            File f = createFile(localParentFolder, timeStamp + ".txt", item.getText().toString());
                            if (f.exists())
                            {
                                retrieveIntentInfo(Uri.fromFile(f));
                            }
                        }
                    }
                }

                if (file == null && Intent.ACTION_SEND.equals(action) && type != null)
                {
                    Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    retrieveIntentInfo(uri);
                }
                else if (action == null && intent.getData() != null)
                {
                    retrieveIntentInfo(intent.getData());
                }
                else if (file == null || fileName == null)
                {
                    MessengerManager.showLongToast(getActivity(), getString(R.string.import_unsupported_intent));
                    getActivity().finish();
                    return;
                }
                files.add(file);
            }
        }
        catch (AlfrescoAppException e)
        {
            org.alfresco.mobile.android.application.manager.ActionManager.actionDisplayError(this, e);
            getActivity().finish();
            return;
        }

        if (adapter == null && files != null)
        {
            adapter = new LocalFileExplorerAdapter(getActivity(), R.layout.app_list_row, files);
            if (lv != null)
            {
                lv.setAdapter(adapter);
            }
            else if (spinnerDoc != null)
            {
                spinnerDoc.setAdapter(adapter);
            }
        }

        Button b = (Button) rootView.findViewById(R.id.cancel);
        b.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                getActivity().finish();
            }
        });

        b = (Button) rootView.findViewById(R.id.ok);
        b.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                next();
            }
        });

        refreshImportFolder();
    }

    // ///////////////////////////////////////////
    // ACCOUNT CURSOR
    // ///////////////////////////////////////////
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        Uri baseUri = AccountProvider.CONTENT_URI;
        return new CursorLoader(getActivity(), baseUri, AccountSchema.COLUMN_ALL, AccountSchema.COLUMN_ACTIVATION
                + " IS NULL OR " + AccountSchema.COLUMN_ACTIVATION + "= ''", null, null);
    }

    public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor)
    {
        if (cursor.getCount() == 0)
        {
            startActivityForResult(new Intent(getActivity(), HomeScreenActivity.class), 1);
            return;
        }
        cursorAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0)
    {
        cursorAdapter.changeCursor(null);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // INTERNALS
    // ///////////////////////////////////////////////////////////////////////////
    private void initiDocumentSpinner(View v)
    {
        spinnerDoc = (Spinner) v.findViewById(R.id.import_documents_spinner);
        if (adapter != null)
        {
            spinnerDoc.setAdapter(adapter);
        }
    }

    private void initDocumentList(View v)
    {
        lv = (ListView) v.findViewById(R.id.listView);

        if (adapter != null)
        {
            lv.setAdapter(adapter);
            lv.setSelection(selectedPosition);
        }
    }
    
    private void retrieveIntentInfo(Uri uri)
    {
        if (uri == null) { throw new AlfrescoAppException(getString(R.string.import_unsupported_intent), true); }

        String tmpPath = ActionManager.getPath(getActivity(), uri);
        if (tmpPath != null)
        {
            file = new File(tmpPath);

            if (file == null || !file.exists()) { throw new AlfrescoAppException(
                    getString(R.string.error_unknown_filepath), true); }
            fileName = file.getName();

            if (getActivity() instanceof PublicDispatcherActivity)
            {
                files.add(file);
                ((PublicDispatcherActivity) getActivity()).setUploadFile(files);
            }
        }
        else
        {
            // Error case : Unable to find the file path associated
            // to user pick.
            // Sample : Picasa image case
            throw new AlfrescoAppException(getString(R.string.error_unknown_filepath), true);
        }
    }
    
    private void refreshImportFolder()
    {
        Spinner spinner = (Spinner) rootView.findViewById(R.id.import_folder_spinner);
        SimpleImportFolderAdapter adapter = new SimpleImportFolderAdapter(getActivity(), R.layout.app_list_row,
                IMPORT_FOLDER_LIST);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener()
        {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
            {
                folderImportId = (Integer) parent.getItemAtPosition(pos);
                importFolderIndex = pos;
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
                // TODO Auto-generated method stub

            }
        });
        if (folderImportId == null)
        {
            importFolderIndex = 0;
        }
        spinner.setSelection(importFolderIndex);
    }

    @SuppressWarnings("serial")
    private static final List<Integer> IMPORT_FOLDER_LIST = new ArrayList<Integer>(3)
    {
        {
            add(R.string.menu_downloads);
            add(R.string.menu_browse_sites);
            add(R.string.menu_favorites);
            add(R.string.menu_browse_root);
        }
    };
    
    private void next()
    {
        long accountId = selectedAccountCursor.getLong(AccountSchema.COLUMN_ID_ID);
        Account tmpAccount = AccountProvider.retrieveAccount(getActivity(), accountId);

        switch (folderImportId)
        {
            case R.string.menu_browse_sites:
            case R.string.menu_browse_root:

                if (getActivity() instanceof PublicDispatcherActivity)
                {
                    ((PublicDispatcherActivity) getActivity()).setUploadFolder(folderImportId);
                }

                AlfrescoSession session = ApplicationManager.getInstance(getActivity()).getSession(tmpAccount.getId());

                // Try to use Session used by the application
                if (session != null)
                {
                    ((BaseActivity) getActivity()).setCurrentAccount(tmpAccount);
                    ((BaseActivity) getActivity()).setRenditionManager(null);
                    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                            new Intent(IntentIntegrator.ACTION_LOAD_ACCOUNT_COMPLETED).putExtra(IntentIntegrator.EXTRA_ACCOUNT_ID, tmpAccount.getId()));
                    return;
                }

                // Session is not used by the application so create one.
                ActionManager.loadAccount(getActivity(), tmpAccount);
                
                break;
            case R.string.menu_downloads:
                ImportLocalDialogFragment fr = ImportLocalDialogFragment.newInstance(tmpAccount, file);
                fr.show(getActivity().getFragmentManager(), ImportLocalDialogFragment.TAG);
                break;
            default:
                break;
        }
    }

    // TODO Move to IOUtils
    private File createFile(File localParentFolder, String filename, String data)
    {
        File outputFile = null;
        Writer writer = null;
        try
        {
            if (!localParentFolder.isDirectory())
            {
                localParentFolder.mkdir();
            }
            outputFile = new File(localParentFolder, filename);
            writer = new BufferedWriter(new FileWriter(outputFile));
            writer.write(data);
            writer.close();
        }
        catch (IOException e)
        {
            Log.w(TAG, Log.getStackTraceString(e));
        }
        return outputFile;
    }
}