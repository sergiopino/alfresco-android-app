/*******************************************************************************
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *  
 *  This file is part of Alfresco Mobile for Android.
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package org.alfresco.mobile.android.application;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.mobile.android.api.session.AlfrescoSession;
import org.alfresco.mobile.android.application.accounts.Account;
import org.alfresco.mobile.android.application.accounts.AccountManager;
import org.alfresco.mobile.android.application.accounts.AccountProvider;
import org.alfresco.mobile.android.application.exception.AlfrescoAppException;
import org.alfresco.mobile.android.application.exception.CloudExceptionUtils;
import org.alfresco.mobile.android.application.fragments.SimpleAlertDialogFragment;
import org.alfresco.mobile.android.application.fragments.WaitingDialogFragment;
import org.alfresco.mobile.android.application.intent.IntentIntegrator;
import org.alfresco.mobile.android.application.manager.RenditionManager;
import org.alfresco.mobile.android.application.utils.thirdparty.LocalBroadcastManager;
import org.alfresco.mobile.android.ui.manager.MessengerManager;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

/**
 * Base class for all activities.
 * 
 * @author Jean Marie Pascal
 */
public abstract class BaseActivity extends Activity
{
    protected static BaseActivity activity;

    protected AccountManager accountManager;

    protected LocalBroadcastManager broadcastManager;

    protected ApplicationManager applicationManager;

    protected BroadcastReceiver receiver;

    protected BroadcastReceiver utilsReceiver;

    protected List<BroadcastReceiver> receivers = new ArrayList<BroadcastReceiver>(2);

    protected Account currentAccount;

    protected RenditionManager renditionManager;

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    public void onCreate(Bundle savedInstanceState)
    {
        activity = this;
        super.onCreate(savedInstanceState);
        broadcastManager = LocalBroadcastManager.getInstance(this);
        applicationManager = ApplicationManager.getInstance(this);
        accountManager = applicationManager.getAccountManager();

        IntentFilter filters = new IntentFilter();
        filters.addAction(IntentIntegrator.ACTION_DISPLAY_DIALOG);
        filters.addAction(IntentIntegrator.ACTION_DISPLAY_ERROR);
        utilsReceiver = new UtilsReceiver();
        broadcastManager.registerReceiver(utilsReceiver, filters);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        for (BroadcastReceiver receiver : receivers)
        {
            broadcastManager.unregisterReceiver(receiver);
        }
        receivers.clear();
    }

    // ///////////////////////////////////////////////////////////////////////////
    // UTILS
    // ///////////////////////////////////////////////////////////////////////////
    protected Fragment getFragment(String tag)
    {
        return getFragmentManager().findFragmentByTag(tag);
    }

    protected void displayWaitingDialog()
    {
        if (getFragmentManager().findFragmentByTag(WaitingDialogFragment.TAG) == null)
        {
            new WaitingDialogFragment().show(getFragmentManager(), WaitingDialogFragment.TAG);
        }
    }

    protected void removeWaitingDialog()
    {
        if (getFragmentManager().findFragmentByTag(WaitingDialogFragment.TAG) != null)
        {
            ((WaitingDialogFragment) getFragmentManager().findFragmentByTag(WaitingDialogFragment.TAG)).dismiss();
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ACCOUNTS / SESSION MANAGEMENT
    // ///////////////////////////////////////////////////////////////////////////
    public void setCurrentAccount(Account account)
    {
        this.currentAccount = account;
    }

    public void setCurrentAccount(long accountId)
    {
        this.currentAccount = AccountProvider.retrieveAccount(this, accountId);
    }

    public Account getCurrentAccount()
    {
        return currentAccount;
    }

    public AlfrescoSession getCurrentSession()
    {
        if (currentAccount == null)
        {
            currentAccount = applicationManager.getCurrentAccount();
        }

        return currentAccount != null ? applicationManager.getSession(currentAccount.getId()) : null;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // MANAGERS
    // ///////////////////////////////////////////////////////////////////////////
    public RenditionManager getRenditionManager()
    {
        return renditionManager;
    }

    public void setRenditionManager(RenditionManager renditionManager)
    {
        this.renditionManager = renditionManager;
    }

    public AccountManager getAccountManager()
    {
        return accountManager;
    }

    // ////////////////////////////////////////////////////////
    // BROADCAST RECEIVER
    // ///////////////////////////////////////////////////////
    /**
     * Register a broadcast receiver to this specific activity. If used this methods is responsible to unregister the
     * receiver during on stop().
     * 
     * @param receiver
     * @param filter
     */
    public void registerPrivateReceiver(BroadcastReceiver receiver, IntentFilter filter)
    {
        if (receiver != null && filter != null)
        {
            broadcastManager.registerReceiver(receiver, filter);
            receivers.add(receiver);
        }
    }

    /**
     * Utility BroadcastReceiver for displaying dialog after an error or to display custom message.
     * Use ACTION_DISPLAY_DIALOG or ACTION_DISPLAY_ERROR Action inside an Intent and send it with localBroadcastManager instance.
     * 
     * @author Jean Marie Pascal
     */
    private class UtilsReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.d("UtilsReceiver", intent.getAction());

            //
            if (IntentIntegrator.ACTION_DISPLAY_DIALOG.equals(intent.getAction()))
            {
                removeWaitingDialog();

                SimpleAlertDialogFragment.newInstance(intent.getExtras()).show(activity.getFragmentManager(),
                        SimpleAlertDialogFragment.TAG);
                return;
            }

            // Intent for Display Errors
            if (IntentIntegrator.ACTION_DISPLAY_ERROR.equals(intent.getAction()))
            {
                if (getFragment(WaitingDialogFragment.TAG) != null)
                {
                    ((DialogFragment) getFragment(WaitingDialogFragment.TAG)).dismiss();
                }
                Exception e = (Exception) intent.getExtras().getSerializable(IntentIntegrator.DISPLAY_ERROR_DATA);

                String errorMessage = getString(R.string.error_general);
                if (e instanceof AlfrescoAppException && ((AlfrescoAppException) e).isDisplayMessage())
                {
                    errorMessage = e.getMessage();
                }

                MessengerManager.showLongToast(activity, errorMessage);

                CloudExceptionUtils.handleCloudException(activity, e, false);

                return;
            }
        }
    }
}