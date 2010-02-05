package com.legind.swinedroid;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import org.xml.sax.SAXException;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.legind.Dialogs.ErrorMessageHandler;
import com.legind.sqlite.ServerDbAdapter;
import com.legind.swinedroid.xml.OverviewXMLHandler;
import com.legind.swinedroid.xml.XMLHandlerException;

public class ServerView extends ListActivity implements Runnable {
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
	private boolean mGotStatistics;
	private Long mRowId;
	private OverviewXMLHandler mOverviewXMLHandler;
	private int mPortInt;
	private String mHostText;
	private String mUsernameText;
	private String mPasswordText;
	private ErrorMessageHandler mEMH;
	private ProgressDialog pd;
	private final String LOG_TAG = "com.legind.swinedroid.ServerView";
	private final int DOCUMENT_VALID = 0;
	private final int IO_ERROR = 1;
	private final int XML_ERROR = 2;
	private final int SERVER_ERROR = 3;
	private final int ACTIVITY_SEARCH = 0;
	private final int ACTIVITY_ALERT_LIST = 1;
	private static final int REFRESH_ID = Menu.FIRST;
    public static ListActivity LA = null;
	static final String[] OPTIONS = new String[] {
		"View Latest Alerts",
	    "Search Alerts"
		};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LA = this;
		mOverviewXMLHandler = new OverviewXMLHandler();
		mDbHelper = new ServerDbAdapter(this);
		mDbHelper.open();
		mGotStatistics = false; 
		
		// Hide the title bar
        this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.server_view);

		// Display all errors on the Swinedroid ListActivity
		mEMH = new ErrorMessageHandler(Swinedroid.LA,
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
		
		// Display snort monitoring options
		setListAdapter(new ArrayAdapter<String>(this, R.layout.server_view_row, OPTIONS));

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
			}
		}
		if (mRowId == null) {
			Bundle extras = getIntent().getExtras();
			mRowId = extras != null ? extras.getLong(ServerDbAdapter.KEY_ROWID)
					: null;
		}

		if (mRowId != null) {
			Cursor server = mDbHelper.fetchServer(mRowId);
			startManagingCursor(server);
			mHostText = server.getString(server
					.getColumnIndexOrThrow(ServerDbAdapter.KEY_HOST));
			mPortInt = server.getInt(server
					.getColumnIndexOrThrow(ServerDbAdapter.KEY_PORT));
			mUsernameText = server.getString(server
					.getColumnIndexOrThrow(ServerDbAdapter.KEY_USERNAME));
			mPasswordText = server.getString(server
					.getColumnIndexOrThrow(ServerDbAdapter.KEY_PASSWORD));
		}
		
		mServerViewTitleText.setText(mHostText + " Severity Statistics");
		if(!mGotStatistics){
			// Display the progress dialog first
			pd = ProgressDialog.show(this, "", "Connecting. Please wait...", true);
			Thread thread = new Thread(this);
			thread.start();
		}
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, REFRESH_ID, 0, R.string.menu_refresh);
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
        }
        return super.onMenuItemSelected(featureId, item);
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(ServerDbAdapter.KEY_ROWID, mRowId);
		if(mGotStatistics){
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
		} else {
			pd.dismiss();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i;
        switch(position){
        	case 0:
	        	i = new Intent(ServerView.this, AlertList.class);
	        	i.putExtra(ServerDbAdapter.KEY_ROWID, mRowId);
	        	i.putExtra("mSpinnerText", "");
	        	i.putExtra("mSearchTermText", "");
	        	startActivityForResult(i, ACTIVITY_ALERT_LIST);
        	break;
	        case 1:
	        	i = new Intent(this, AlertSearch.class);
	        	i.putExtra(ServerDbAdapter.KEY_ROWID, mRowId);
	        	startActivityForResult(i, ACTIVITY_SEARCH);
	        break;
        }
        //Intent i = new Intent(this, ServerView.class);
        //i.putExtra(ServerDbAdapter.KEY_ROWID, id);
        //startActivityForResult(i, ACTIVITY_VIEW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
    }
    
	public void run() {
		try {
			mOverviewXMLHandler.createElement(this, mHostText, mPortInt, mUsernameText, mPasswordText, "overview");
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
		}
		handler.sendEmptyMessage(DOCUMENT_VALID);
	}

	// Catch and display any errors sent to the handler, otherwise populate all statistics fields
	private volatile Handler handler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			pd.dismiss();
			switch(message.what){
				case IO_ERROR:
					mEMH.DisplayErrorMessage("Could not connect to server.  Please ensure that your settings are correct and try again later.");
				break;
				case XML_ERROR:
					mEMH.DisplayErrorMessage("Server responded with an invalid XML document.  Please try again later.");
				break;
				case SERVER_ERROR:
					mEMH.DisplayErrorMessage((String) message.obj);
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
				break;
			}
			if(message.what != DOCUMENT_VALID){
				mDbHelper.close();
				finish();
			}

		}
	};
}
