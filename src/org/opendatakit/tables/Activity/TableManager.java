/* * Copyright (C) 2012 University of Washington * * Licensed under the Apache License, Version 2.0 (the "License"); you may not * use this file except in compliance with the License. You may obtain a copy of * the License at * * http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the * License for the specific language governing permissions and limitations under * the License. */package org.opendatakit.tables.Activity;import java.util.ArrayList;import java.util.HashMap;import java.util.List;import java.util.Map;import org.opendatakit.tables.R;import org.opendatakit.tables.Activity.importexport.ImportExportActivity;import org.opendatakit.tables.Task.InitializeTask;import org.opendatakit.tables.activities.ConflictResolutionActivity;import org.opendatakit.tables.activities.Controller;import org.opendatakit.tables.data.ColumnProperties;import org.opendatakit.tables.data.DbHelper;import org.opendatakit.tables.data.KeyValueStore;import org.opendatakit.tables.data.Preferences;import org.opendatakit.tables.data.TableProperties;import org.opendatakit.tables.data.TableType;import org.opendatakit.tables.util.ConfigurationUtil;import android.app.AlertDialog;import android.content.Context;import android.content.DialogInterface;import android.content.Intent;import android.os.Bundle;import android.util.Log;import android.view.ContextMenu;import android.view.ContextMenu.ContextMenuInfo;import android.view.LayoutInflater;import android.view.View;import android.view.View.OnClickListener;import android.view.ViewGroup;import android.view.WindowManager;import android.widget.AdapterView;import android.widget.EditText;import android.widget.ImageView;import android.widget.ListView;import android.widget.SimpleAdapter;import android.widget.TextView;import android.widget.Toast;import com.actionbarsherlock.app.SherlockListActivity;import com.actionbarsherlock.view.Menu;import com.actionbarsherlock.view.MenuItem;import com.actionbarsherlock.view.SubMenu;public class TableManager extends SherlockListActivity {	public static final int ADD_NEW_TABLE     		= 1;	public static final int ADD_NEW_SECURITY_TABLE 	= 2;	public static final int IMPORT_EXPORT			= 3;	public static final int SET_DEFAULT_TABLE 		= 4;	public static final int SET_SECURITY_TABLE      = 5;	public static final int SET_SHORTCUT_TABLE      = 6;	public static final int REMOVE_TABLE      		= 7;	public static final int ADD_NEW_SHORTCUT_TABLE  = 8;	public static final int UNSET_DEFAULT_TABLE     = 9;	public static final int UNSET_SECURITY_TABLE    = 10;	public static final int UNSET_SHORTCUT_TABLE    = 11;	public static final int AGGREGATE               = 12;	public static final int LAUNCH_TPM              = 13;	public static final int LAUNCH_CONFLICT_MANAGER = 14;	public static final int LAUNCH_DPREFS_MANAGER   = 15;	public static final int LAUNCH_SECURITY_MANAGER = 16;	private static String[] from = new String[] {"label", "ext", "options"};	private static int[] to = new int[] { android.R.id.text1, android.R.id.text2, R.id.row_settings };	private List<Map<String, String>> fMaps;	private DbHelper dbh;	private Preferences prefs;	private TableProperties[] tableProps;	private SimpleAdapter arrayAdapter;	@Override	public void onCreate(Bundle savedInstanceState) {		super.onCreate(savedInstanceState);		dbh = DbHelper.getDbHelper(this);		prefs = new Preferences(this);		// Remove title of activity		setTitle("");		// Set Content View		setContentView(R.layout.plain_list);		init();		refreshList();	}	/**	 * initializes TableManager by importing csv files listed in the	 * config.properties file	 */	private void init() {		if (ConfigurationUtil.isChanged(prefs)) {			new InitializeTask(this).execute();			refreshList();		}	}	/**	 * Gets the Preferences for TableManager	 */	public Preferences getPrefs() {		return prefs;	}	@Override	public void onResume() {		super.onResume();		refreshList();	}	private void makeNoTableNotice() {		List<HashMap<String, String>> fillMaps = new ArrayList<HashMap<String, String>>();		HashMap<String, String> temp = new HashMap<String, String>();		temp.put("label", "Use + submenu to add a new table");		fillMaps.add(temp);		arrayAdapter = new SimpleAdapter(this, fillMaps, R.layout.plain_list_row, from, to);		setListAdapter(arrayAdapter);	}	/**	 * re-populates the Table Manager's List View and sets the onClickListener for	 * the settings icon on the right each row 	 */	// (no longer necessary to long-click on the row to get the context menu)	public void refreshList() {		registerForContextMenu(getListView());		tableProps = TableProperties.getTablePropertiesForAll(dbh,				KeyValueStore.Type.ACTIVE);		Log.d("TM", "refreshing list, tableProps.length=" + tableProps.length);		if (tableProps.length == 0) {			makeNoTableNotice();			return;		}		String defTableId = prefs.getDefaultTableId();		fMaps = new ArrayList<Map<String, String>>();		for(TableProperties tp : tableProps) {			Map<String, String> map = new HashMap<String, String>();			map.put("label", tp.getDisplayName());			if (tp.getTableType() == TableType.security) {				map.put("ext", "SMS Access Control Table");			} else if (tp.getTableType() == TableType.shortcut) {				map.put("ext", "Shortcut Table");			}			if(tp.getTableId() == defTableId) {				if(map.get("ext") == null) {					map.put("ext", "Default Table");				} else {					map.put("ext", map.get("ext") + "; Default Table");				}			}			fMaps.add(map);		}		// fill in the grid_item layout		arrayAdapter = new RowAdapter(); 		setListAdapter(arrayAdapter);		// clicking the row name opens that table		ListView lv = getListView();		lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {			@Override			public void onItemClick(AdapterView<?> adView, View view,					int position, long id) {				// Load Selected Table				loadSelectedTable(position);			}		});		// Disable the ability to open a contextual menu by long clicking on the 		// table name (so only the settings icon can open the contextual menu)		//		lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {		//		//			@Override		//			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,		//					int arg2, long arg3) {		//				return true;		//			}		//					//		});	} 	private void loadSelectedTable(int index) {		TableProperties tp = tableProps[index];		Controller.launchTableActivity(this, tp, true);	}	@Override	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {		super.onCreateContextMenu(menu, v, menuInfo);		AdapterView.AdapterContextMenuInfo acmi =				(AdapterView.AdapterContextMenuInfo) menuInfo;		TableProperties tp = tableProps[acmi.position];		if(tp.getTableId().equals(prefs.getDefaultTableId())) {			menu.add(0, UNSET_DEFAULT_TABLE, 0, "Unset as Default Table");		} else {			menu.add(0, SET_DEFAULT_TABLE, 0, "Set as Default Table");		}		TableType tableType = tp.getTableType();		if (tableType == TableType.data) {			if (couldBeSecurityTable(tp)) {				menu.add(0, SET_SECURITY_TABLE, 0, "Set as Access Control Table");			}			if (couldBeShortcutTable(tp)) {				menu.add(0, SET_SHORTCUT_TABLE, 0, "Set as Shortcut Table");			}		} else if (tableType == TableType.security) {			menu.add(0, UNSET_SECURITY_TABLE, 0, "Unset as Access Control Table");		} else if (tableType == TableType.shortcut) {			menu.add(0, UNSET_SHORTCUT_TABLE, 0, "Unset as Shortcut Table");		}		menu.add(0, REMOVE_TABLE, 1, "Delete the Table");		menu.add(0, LAUNCH_TPM, 2, "Edit Table Properties");		menu.add(0, LAUNCH_CONFLICT_MANAGER, 3, "Manage Conflicts");		menu.add(0, LAUNCH_SECURITY_MANAGER, 4, "Security Manager");	}	private boolean couldBeSecurityTable(TableProperties tp) {		String[] expected = { "phone_number", "id", "password" };		return checkTable(expected, tp);	}	private boolean couldBeShortcutTable(TableProperties tp) {		String[] expected = { "name", "input_format", "output_format" };		return checkTable(expected, tp);	}	private boolean checkTable(String[] expectedCols, TableProperties tp) {		ColumnProperties[] columns = tp.getColumns();		if (columns.length < expectedCols.length) {			return false;		}		for (int i = 0; i < expectedCols.length; i++) {			if (!expectedCols[i].equals(columns[i].getElementKey())) {				return false;			}		}		return true;	}	public boolean onContextItemSelected(android.view.MenuItem item) {		AdapterView.AdapterContextMenuInfo info= (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();		final TableProperties tp = tableProps[info.position];		switch (item.getItemId()) {		case SET_DEFAULT_TABLE:			prefs.setDefaultTableId(tp.getTableId());			refreshList();			return true;		case UNSET_DEFAULT_TABLE:			prefs.setDefaultTableId(null);			refreshList();			return true;		case SET_SECURITY_TABLE:			tp.setTableType(TableType.security);			refreshList();			return true;		case UNSET_SECURITY_TABLE:			tp.setTableType(TableType.data);			refreshList();			return true;		case SET_SHORTCUT_TABLE:			tp.setTableType(TableType.shortcut);			refreshList();			return true;		case UNSET_SHORTCUT_TABLE:			tp.setTableType(TableType.data);			refreshList();			return true;		case REMOVE_TABLE:			AlertDialog confirmDeleteAlert;			// Prompt an alert box			AlertDialog.Builder alert = 			new AlertDialog.Builder(TableManager.this);			alert.setTitle("Delete " + tp.getDisplayName() + "?");			// OK Action => delete the row			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {				public void onClick(DialogInterface dialog, int whichButton) {					tp.deleteTable();					refreshList();				}			});			// Cancel Action			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {				public void onClick(DialogInterface dialog, int whichButton) {					// Canceled.				}			});			// show the dialog			confirmDeleteAlert = alert.create();			confirmDeleteAlert.show();			return true;		case LAUNCH_TPM:		{			Intent i = new Intent(this, TablePropertiesManager.class);			i.putExtra(TablePropertiesManager.INTENT_KEY_TABLE_ID, tp.getTableId());			startActivity(i);			return true;		}		case LAUNCH_CONFLICT_MANAGER:		{			Intent i = new Intent(this, ConflictResolutionActivity.class);			i.putExtra(Controller.INTENT_KEY_TABLE_ID, tp.getTableId());			i.putExtra(Controller.INTENT_KEY_IS_OVERVIEW, false);			startActivity(i);			return true;		}		case LAUNCH_SECURITY_MANAGER:		{			Intent i = new Intent(this, SecurityManager.class);			i.putExtra(SecurityManager.INTENT_KEY_TABLE_ID, tp.getTableId());			startActivity(i);			return true;		}		}		return(super.onOptionsItemSelected(item));	}	// CREATE OPTION MENU	@Override	public boolean onCreateOptionsMenu(Menu menu) {		super.onCreateOptionsMenu(menu);		// Sub-menu containing different "add" menus		SubMenu addNew = menu.addSubMenu("Add Table");		addNew.setIcon(R.drawable.content_new);		MenuItem subMenuItem = addNew.getItem();		subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);		addNew.add(0, ADD_NEW_TABLE, 0, "Add New Data Table");		// Commented these out for demo purposes, as their actions are currently		// undefined.		//addNew.add(0, ADD_NEW_SECURITY_TABLE, 0, "Add New Access Control Table");		//addNew.add(0, ADD_NEW_SHORTCUT_TABLE, 0, "Add New Shortcut Table");		addNew.add(0, IMPORT_EXPORT, 0, "File Import/Export");		MenuItem item;		item = menu.add(0, AGGREGATE, 0, "Sync");		item.setIcon(R.drawable.sync_icon);		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);		item = menu.add(0, LAUNCH_DPREFS_MANAGER, 0, "Display Preferences");		item.setIcon(R.drawable.settings_icon2);		item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);		return true;	}	// HANDLE OPTION MENU	@Override	public boolean onMenuItemSelected(int featureId, MenuItem item) {		Log.d("timing", "menu item selected");		// HANDLES DIFFERENT MENU OPTIONS		switch(item.getItemId()) {		case 0: 			return true;		case ADD_NEW_TABLE:			alertForNewTableName(true, TableType.data, null, null);			return true;		case ADD_NEW_SECURITY_TABLE:			alertForNewTableName(true, TableType.security, null, null);			return true;		case ADD_NEW_SHORTCUT_TABLE:			alertForNewTableName(true, TableType.shortcut, null, null);			return true;		case IMPORT_EXPORT:			Intent i = new Intent(this, ImportExportActivity.class);			startActivity(i);			return true;		case AGGREGATE:			Intent j = new Intent(this, Aggregate.class);			startActivity(j);			return true;		case LAUNCH_DPREFS_MANAGER:			Intent k = new Intent(this, DisplayPrefsActivity.class);			startActivity(k);			return true;		}		return super.onMenuItemSelected(featureId, item);	}	// Ask for a new table name.	/*	 * Note that when prompting for a new data table, the following parameters 	 * are passed to the method:	 * isNewTable == true	 * tableType == data	 * tp == null	 * givenTableName == null	 */	private void alertForNewTableName(final boolean isNewTable, 			final TableType tableType, final TableProperties tp, String givenTableName) {		AlertDialog newTableAlert;		// Prompt an alert box		AlertDialog.Builder alert = new AlertDialog.Builder(this);		alert.setTitle("Name of New Table");		// Set an EditText view to get user input 		final EditText input = new EditText(this);		alert.setView(input);		if (givenTableName != null) 			input.setText(givenTableName);		// OK Action => Create new Column		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {			public void onClick(DialogInterface dialog, int whichButton) {				String newTableName = input.getText().toString().trim();				if (newTableName == null || newTableName.equals("")) {					// Table name is empty string					toastTableNameError("Table name cannot be empty!");					alertForNewTableName(isNewTable, tableType, tp, null);				} else {					if (isNewTable) 						addTable(newTableName, tableType);					else						tp.setDisplayName(newTableName);					Log.d("TM", "got here");					refreshList();				}			}		});		// Cancel Action		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {			public void onClick(DialogInterface dialog, int whichButton) {				// Canceled.			}		});		newTableAlert = alert.create();		newTableAlert.getWindow().setSoftInputMode(WindowManager.				LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);		newTableAlert.show();	}	private void toastTableNameError(String msg) {		Context context = getApplicationContext();		Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);		toast.show();	}	private void addTable(String tableName, TableType tableType) {		String dbTableName =				TableProperties.createDbTableName(dbh, tableName);		// If you're adding through the table manager, you're using the phone,		// and consequently you should be adding to the active store.		@SuppressWarnings("unused")		TableProperties tp = TableProperties.addTable(dbh, dbTableName,				tableName, tableType, KeyValueStore.Type.ACTIVE);	}	public void contextualSettingsClicked(View view) {		openContextMenu(view);	}	class RowAdapter extends SimpleAdapter {		RowAdapter() {			super(TableManager.this, fMaps, R.layout.plain_list_row, from, to);		}		public View getView(int position, View convertView, ViewGroup parent) {			View row = convertView;			if (row == null) {																	LayoutInflater inflater=getLayoutInflater();				row = inflater.inflate(R.layout.plain_list_row, parent, false);			}			// Current Position in the List			final int currentPosition = position;			Map<String, String> currentTable = fMaps.get(position);			// Register name of table at each row in the list view			TextView label = (TextView)row.findViewById(android.R.id.text1);					label.setText(currentTable.get("label"));			// Register ext info for table			TextView ext = (TextView)row.findViewById(android.R.id.text2);			ext.setText(currentTable.get("ext"));			// Settings icon opens a context menu for that table			final ImageView settingsIcon = (ImageView)row.findViewById(R.id.row_settings);			settingsIcon.setClickable(true);			settingsIcon.setOnClickListener(new OnClickListener() {				@Override				public void onClick(View v) {					openContextMenu(v);				}			});			return(row);		}	}}