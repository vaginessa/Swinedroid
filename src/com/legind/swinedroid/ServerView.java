package com.legind.swinedroid;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ListIterator;

import org.achartengine.chartlib.AlertChart;
import org.achartengine.chartlib.AlertChart.AlertMoment;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.legind.Dialogs.ErrorMessageHandler;
import com.legind.sqlite.ServerDbAdapter;
import com.legind.swinedroid.RequestService.Request;
import com.legind.swinedroid.xml.OverviewXMLHandler;
import com.legind.swinedroid.xml.XMLHandlerException;
import com.legind.web.WebTransport.WebTransportException;

public class ServerView extends Activity implements Runnable {
	private ServerDbAdapter mDbHelper;
	private TextView mServerViewTitleText;
	private TextView mAllTimeHighText;
	private TextView mAllTimeMediumText;
	private TextView mAllTimeLowText;
	private TextView mAllTimeTotalText;
	private TextView mLast72HighText;
	private TextView mLast72MediumText;
	private TextView mLast72LowText;
	private TextView mLast72TotalText;
	private TextView mLast24HighText;
	private TextView mLast24MediumText;
	private TextView mLast24LowText;
	private TextView mLast24TotalText;
	private LinearLayout alertLinearLayout;
	private boolean mGotStatistics;
	private boolean mPausedForCertificate;
	private Long mRowId;
	private OverviewXMLHandler mOverviewXMLHandler;
	private ErrorMessageHandler mEMH;
	private ProgressDialog pd;
	private final String LOG_TAG = "com.legind.swinedroid.ServerView";
	private final int DOCUMENT_VALID = 0;
	private final int IO_ERROR = 1;
	private final int XML_ERROR = 2;
	private final int SERVER_ERROR = 3;
	private final int CERT_ERROR = 4;
	private final int ACTIVITY_SEARCH = 0;
	private final int ACTIVITY_ALERT_LIST = 1;
	private final int ACTIVITY_HASH_DIALOG = 2;
	private final int CERT_REJECTED = 0;
	private final int CERT_ACCEPTED = 1;
	private static final int REFRESH_ID = 1;
	private static final int VIEW_ID = 2;
	private static final int SEARCH_ID = 3;
    public static Activity A = null;
    
	private Request mBoundRequest;
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
		    mBoundRequest = ((Request.RequestBinder)service).getService();
			mServerViewTitleText.setText(mBoundRequest.getCurrentHost() + " Severity Statistics");
			
			if(!mGotStatistics){
				// Display the progress dialog first
				pd = ProgressDialog.show(ServerView.this, "", "Connecting. Please wait...", true);
				Thread thread = new Thread(ServerView.this);
				thread.start();
			}
		}
		
		public void onServiceDisconnected(ComponentName className) {
			mBoundRequest = null;
		}
	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		A = this;
		mOverviewXMLHandler = new OverviewXMLHandler();
		mDbHelper = new ServerDbAdapter(this);
		mDbHelper.open();
		mGotStatistics = false;
		
		// Hide the title bar
        this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.server_view);

		// Display all errors on the Swinedroid ListActivity
		mEMH = new ErrorMessageHandler(this,
				findViewById(R.id.server_edit_error_layout_root));

		mServerViewTitleText = (TextView) findViewById(R.id.server_view_title);
		mAllTimeHighText = (TextView) findViewById(R.id.all_time_high);
		mAllTimeMediumText = (TextView) findViewById(R.id.all_time_med);
		mAllTimeLowText = (TextView) findViewById(R.id.all_time_low);
		mAllTimeTotalText = (TextView) findViewById(R.id.all_time_total);
		mLast72HighText = (TextView) findViewById(R.id.last_72_high);
		mLast72MediumText = (TextView) findViewById(R.id.last_72_med);
		mLast72LowText = (TextView) findViewById(R.id.last_72_low);
		mLast72TotalText = (TextView) findViewById(R.id.last_72_total);
		mLast24HighText = (TextView) findViewById(R.id.last_24_high);
		mLast24MediumText = (TextView) findViewById(R.id.last_24_med);
		mLast24LowText = (TextView) findViewById(R.id.last_24_low);
		mLast24TotalText = (TextView) findViewById(R.id.last_24_total);
		alertLinearLayout = (LinearLayout) findViewById(R.id.server_view_alert_linear_layout);
    	
		if(savedInstanceState != null){
			mRowId = savedInstanceState.getLong(ServerDbAdapter.KEY_ROWID);
			if(savedInstanceState.getBoolean("mGotStatistics")){
				mGotStatistics = true;
				mAllTimeHighText.setText(savedInstanceState.getString("mAllTimeHighText"));
				mAllTimeMediumText.setText(savedInstanceState.getString("mAllTimeMediumText"));
				mAllTimeLowText.setText(savedInstanceState.getString("mAllTimeLowText"));
				mAllTimeTotalText.setText(savedInstanceState.getString("mAllTimeTotalText"));
				mLast72HighText.setText(savedInstanceState.getString("mLast72HighText"));
				mLast72MediumText.setText(savedInstanceState.getString("mLast72MediumText"));
				mLast72LowText.setText(savedInstanceState.getString("mLast72LowText"));
				mLast72TotalText.setText(savedInstanceState.getString("mLast72TotalText"));
				mLast24HighText.setText(savedInstanceState.getString("mLast24HighText"));
				mLast24MediumText.setText(savedInstanceState.getString("mLast24MediumText"));
				mLast24LowText.setText(savedInstanceState.getString("mLast24LowText"));
				mLast24TotalText.setText(savedInstanceState.getString("mLast24TotalText"));
				mOverviewXMLHandler.alertChart = new AlertChart();
				for(int i = 0; savedInstanceState.containsKey("alertMomentLabel" + String.valueOf(i)); i++){
					mOverviewXMLHandler.alertChart.addAlertMoment();
					mOverviewXMLHandler.alertChart.setLastMomentHighAlert(savedInstanceState.getInt("alertMomentHigh" + String.valueOf(i)));
					mOverviewXMLHandler.alertChart.setLastMomentMediumAlert(savedInstanceState.getInt("alertMomentMedium" + String.valueOf(i)));
					mOverviewXMLHandler.alertChart.setLastMomentLowAlert(savedInstanceState.getInt("alertMomentLow" + String.valueOf(i)));
					mOverviewXMLHandler.alertChart.setLastMomentLabel(savedInstanceState.getString("alertMomentLabel" + String.valueOf(i)));
				}
				alertLinearLayout.removeAllViews();
				mOverviewXMLHandler.alertChart.setTitleString("Alerts by Date");
				mOverviewXMLHandler.alertChart.setXAxisString("Date");
				alertLinearLayout.addView(mOverviewXMLHandler.alertChart.execute(this));
			}
		}
		if (mRowId == null) {
			Bundle extras = getIntent().getExtras();
			mRowId = extras != null ? extras.getLong(ServerDbAdapter.KEY_ROWID)
					: null;
		}
		
		startRequestService();
	}
    
	@Override
	protected void onDestroy() {
		mDbHelper.close();
		if(mBoundRequest != null)
			unbindService(mConnection);
		super.onDestroy();
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem refreshMenuItem = menu.add(0, REFRESH_ID, 0, R.string.menu_refresh);
        MenuItem viewMenuItem = menu.add(0, VIEW_ID, 0, R.string.menu_view_alerts);
        MenuItem searchMenuItem = menu.add(0, SEARCH_ID, 0, R.string.menu_search_alerts);
        refreshMenuItem.setIcon(R.drawable.ic_menu_refresh);
        viewMenuItem.setIcon(android.R.drawable.ic_menu_view);
        searchMenuItem.setIcon(android.R.drawable.ic_menu_search);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()){
        	case REFRESH_ID:
        		// Display the ProgressDialog and start thread
        		mGotStatistics = false;
        		pd = ProgressDialog.show(this, "", "Connecting. Please wait...", true);
        		Thread thread = new Thread(this);
        		thread.start();
        	break;
        	case VIEW_ID:
	        	Intent viewIntent = new Intent(ServerView.this, AlertList.class);
	        	viewIntent.putExtra(ServerDbAdapter.KEY_ROWID, mRowId);
	        	viewIntent.putExtra("mSpinnerText", "");
	        	viewIntent.putExtra("mSearchTermText", "");
	        	startActivityForResult(viewIntent, ACTIVITY_ALERT_LIST);
        	break;
	        case SEARCH_ID:
	        	Intent searchIntent = new Intent(this, AlertSearch.class);
	        	searchIntent.putExtra(ServerDbAdapter.KEY_ROWID, mRowId);
	        	startActivityForResult(searchIntent, ACTIVITY_SEARCH);
	        break;
        }
        return super.onMenuItemSelected(featureId, item);
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(ServerDbAdapter.KEY_ROWID, mRowId);
		if(mGotStatistics || mPausedForCertificate){
			outState.putBoolean("mGotStatistics", true);
			outState.putString("mAllTimeHighText", mAllTimeHighText.getText().toString());
			outState.putString("mAllTimeMediumText", mAllTimeMediumText.getText().toString());
			outState.putString("mAllTimeLowText", mAllTimeLowText.getText().toString());
			outState.putString("mAllTimeTotalText", mAllTimeTotalText.getText().toString());
			outState.putString("mLast72HighText", mLast72HighText.getText().toString());
			outState.putString("mLast72MediumText", mLast72MediumText.getText().toString());
			outState.putString("mLast72LowText", mLast72LowText.getText().toString());
			outState.putString("mLast72TotalText", mLast72TotalText.getText().toString());
			outState.putString("mLast24HighText", mLast24HighText.getText().toString());
			outState.putString("mLast24MediumText", mLast24MediumText.getText().toString());
			outState.putString("mLast24LowText", mLast24LowText.getText().toString());
			outState.putString("mLast24TotalText", mLast24TotalText.getText().toString());
			if(mGotStatistics){
				ListIterator<AlertMoment> itr = mOverviewXMLHandler.alertChart.alertMoments.listIterator();
				while(itr.hasNext()){
					int i = itr.nextIndex();
					AlertMoment alertMoment = itr.next();
					outState.putInt("alertMomentHigh" + String.valueOf(i), alertMoment.mHigh);
					outState.putInt("alertMomentMedium" + String.valueOf(i), alertMoment.mMedium);
					outState.putInt("alertMomentLow" + String.valueOf(i), alertMoment.mLow);
					outState.putString("alertMomentLabel" + String.valueOf(i), alertMoment.mLabel);
				}
			}
		} else {
			pd.dismiss();
		}
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch(requestCode){
        	case ACTIVITY_HASH_DIALOG:
        		/*
        		 * The ServerHashDialog activity has finished.  if the cert is rejected, finish this activity.
        		 * If accepted, try connecting to the server all over again.
        		 */
        		switch(resultCode){
        			case CERT_REJECTED:
        				finish();
        			break;
        			case CERT_ACCEPTED:
        				mPausedForCertificate = false;
        				pd = ProgressDialog.show(this, "", "Connecting. Please wait...", true);
        				Bundle extras = intent.getExtras();
        				mDbHelper.updateSeverHashes(mRowId, extras.getString("MD5"), extras.getString("SHA1"));
        				mBoundRequest.fetchServerHashes();
        				Thread thread = new Thread(this);
        				thread.start();
        			break;
        		}
        	break;
        }
    }
    
	private void startRequestService() {
        Intent newinIntent = new Intent(this, Request.class);
        newinIntent.putExtra(Request.ROW_ID_TAG, mRowId);
        startService(newinIntent);
        bindService(newinIntent, mConnection, 0);
	}
    
	public void run() {
		sendRequestHandleResponse();
	}
	
	public void sendRequestHandleResponse(){
		try{
			mBoundRequest.openWebTransportConnection();
			if(!mBoundRequest.inspectCertificate()){
				handler.sendEmptyMessage(CERT_ERROR);
			} else {
				mOverviewXMLHandler.createElement(mBoundRequest, "overview");
				handler.sendEmptyMessage(DOCUMENT_VALID);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, e.toString());
			handler.sendEmptyMessage(IO_ERROR);
		} catch (SAXException e) {
			Log.e(LOG_TAG, e.toString());
			handler.sendEmptyMessage(XML_ERROR);
		} catch (XMLHandlerException e){
			Log.e(LOG_TAG, e.toString());
			Message msg = Message.obtain();
			msg.setTarget(handler);
			msg.what = SERVER_ERROR;
			msg.obj = e.getMessage();
			msg.sendToTarget();
		} catch (WebTransportException e){
			mBoundRequest.closeWebTransportConnection();
			sendRequestHandleResponse();
		}
	}

	// Catch and display any errors sent to the handler, otherwise populate all statistics fields
	private volatile Handler handler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			pd.dismiss();
			OnCancelListener cancelListener = new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					mDbHelper.close();
					finish();
					return;
				}
			};
			switch(message.what){
				case IO_ERROR:
					mEMH.DisplayErrorMessage("Could not connect to server.  Please ensure that your settings are correct and try again later.",cancelListener);
				break;
				case XML_ERROR:
					mEMH.DisplayErrorMessage("Server responded with an invalid XML document.  Please try again later.",cancelListener);
				break;
				case SERVER_ERROR:
					mEMH.DisplayErrorMessage((String) message.obj,cancelListener);
				break;
				case CERT_ERROR:
					/*
					 * If there is a certificate mismatch, display the ServerHashDialog activity
					 */
					// must set this to true, in case onPause happens for child activity
					mPausedForCertificate = true;
					//mBoundRequest.displayCertificateDialog(ServerView.this);
		        	Intent i = new Intent(ServerView.this, ServerHashDialog.class);
		        	i.putExtra("SHA1", mBoundRequest.getLastServerCertSHA1());
		        	i.putExtra("MD5", mBoundRequest.getLastServerCertMD5());
		        	i.putExtra("CERT_INVALID", (mBoundRequest.getLastServerCertSHA1() == null && mBoundRequest.getLastServerCertMD5() == null ? false : true));
		        	startActivityForResult(i, ACTIVITY_HASH_DIALOG);
				break;
				case DOCUMENT_VALID:
					mGotStatistics = true;
					DecimalFormat df = new DecimalFormat();
					DecimalFormatSymbols dfs = new DecimalFormatSymbols();
					dfs.setGroupingSeparator(',');
					df.setDecimalFormatSymbols(dfs);
					mAllTimeHighText.setText(df.format(mOverviewXMLHandler.current_element.all_time_high));
					mAllTimeMediumText.setText(df.format(mOverviewXMLHandler.current_element.all_time_medium));
					mAllTimeLowText.setText(df.format(mOverviewXMLHandler.current_element.all_time_low));
					mAllTimeTotalText.setText(df.format(mOverviewXMLHandler.current_element.all_time_high + mOverviewXMLHandler.current_element.all_time_medium + mOverviewXMLHandler.current_element.all_time_low));
					mLast72HighText.setText(df.format(mOverviewXMLHandler.current_element.last_72_high));
					mLast72MediumText.setText(df.format(mOverviewXMLHandler.current_element.last_72_medium));
					mLast72LowText.setText(df.format(mOverviewXMLHandler.current_element.last_72_low));
					mLast72TotalText.setText(df.format(mOverviewXMLHandler.current_element.last_72_high + mOverviewXMLHandler.current_element.last_72_medium + mOverviewXMLHandler.current_element.last_72_low));
					mLast24HighText.setText(df.format(mOverviewXMLHandler.current_element.last_24_high));
					mLast24MediumText.setText(df.format(mOverviewXMLHandler.current_element.last_24_medium));
					mLast24LowText.setText(df.format(mOverviewXMLHandler.current_element.last_24_low));
					mLast24TotalText.setText(df.format(mOverviewXMLHandler.current_element.last_24_high + mOverviewXMLHandler.current_element.last_24_medium + mOverviewXMLHandler.current_element.last_24_low));
					alertLinearLayout.removeAllViews();
					mOverviewXMLHandler.alertChart.setTitleString("Alerts by Date");
					mOverviewXMLHandler.alertChart.setXAxisString("Date");
					alertLinearLayout.addView(mOverviewXMLHandler.alertChart.execute(ServerView.A));
				break;
			}

		}
	};
}
