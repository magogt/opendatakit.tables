/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.tables.activities.importexport;

import java.io.File;

import org.opendatakit.tables.R;
import org.opendatakit.tables.data.DbHelper;
import org.opendatakit.tables.data.KeyValueStore;
import org.opendatakit.tables.data.TableProperties;
import org.opendatakit.tables.tasks.ImportRequest;
import org.opendatakit.tables.tasks.ImportTask;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * An activity for importing CSV files to a table.
 */
public class ImportCSVActivity extends AbstractImportExportActivity {

	/** view IDs (for use in testing) */
	public static int NTNVAL_ID = 1;
	public static int TABLESPIN_ID = 2;
	public static int FILENAMEVAL_ID = 3;
	public static int IMPORTBUTTON_ID = 4;

	/* the list of table properties */
	private TableProperties[] tps;
	/* the list of table names */
	private String[] tableNames;
	/* the view for inputting the new table name (label and text field) */
	private View newTableViews;
	/* the text field for getting the new table name */
	private EditText ntnValField;
	/* the table name spinner */
	private Spinner tableSpin;
	/* the text field for getting the filename */
	private EditText filenameValField;
	/* the button for selecting a file */
	private Button pickFileButton;
	/** The button to import a table. */
	private Button mImportButton;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getView());
	}

	/**
	 * @return the view
	 */
	private View getView() {
		LinearLayout v = new LinearLayout(this);
		v.setOrientation(LinearLayout.VERTICAL);
		// adding the filename field
		LinearLayout fn = new LinearLayout(this);
		fn.setOrientation(LinearLayout.VERTICAL);
		TextView fnLabel = new TextView(this);
		fnLabel.setText(getString(R.string.import_csv_filename));
		fnLabel.setTextColor(getResources().getColor(R.color.black));
		fn.addView(fnLabel);
		filenameValField = new EditText(this);
		filenameValField.setId(FILENAMEVAL_ID);
		fn.addView(filenameValField);
		v.addView(fn);
		pickFileButton = new Button(this);
		pickFileButton.setText(getString(R.string.import_choose_csv_file));
		pickFileButton.setOnClickListener(new PickFileButtonListener(getString(R.string.import_choose_csv_file)));
		v.addView(pickFileButton);
		// Horizontal divider
		View ruler1 = new View(this); ruler1.setBackgroundColor(getResources().getColor(R.color.black));
		v.addView(ruler1,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
		// adding the table spinner
		TextView etn = new TextView(this);
		etn.setText(getString(R.string.import_disposition_label));
		etn.setTextColor(getResources().getColor(R.color.black));
		tableSpin = new Spinner(this);
		tableSpin.setId(TABLESPIN_ID);
		tps = TableProperties.getTablePropertiesForAll(
		        DbHelper.getDbHelper(this), KeyValueStore.Type.ACTIVE);
		tableNames = new String[tps.length + 1];
		tableNames[0] = getString(R.string.import_new_table);
		int counter = 1;
		for (TableProperties tp : tps) {
		    tableNames[counter] = tp.getDisplayName();
		    counter++;
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, tableNames);
		adapter.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item);
		tableSpin.setAdapter(adapter);
		tableSpin.setSelection(0);
		tableSpin.setOnItemSelectedListener(new tableSpinListener());
		v.addView(etn);
		v.addView(tableSpin);
		// adding the new table name field
		LinearLayout ntn = new LinearLayout(this);
		ntn.setOrientation(LinearLayout.VERTICAL);
		TextView ntnLabel = new TextView(this);
		ntnLabel.setText(getString(R.string.enter_new_table_name));
		ntnLabel.setTextColor(getResources().getColor(R.color.black));
		ntn.addView(ntnLabel);
		ntnValField = new EditText(this);
		ntnValField.setId(NTNVAL_ID);
		ntnValField.setText(getString(R.string.import_new_table));
		ntn.addView(ntnValField);
		newTableViews = ntn;
		v.addView(newTableViews);
		// Horizontal divider
		View ruler2 = new View(this); ruler2.setBackgroundColor(getResources().getColor(R.color.black));
		v.addView(ruler2,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
		// adding the import button
		this.mImportButton = new Button(this);
		this.mImportButton.setId(IMPORTBUTTON_ID);
		this.mImportButton.setText(getString(R.string.import_import_new_table));
		this.mImportButton.setOnClickListener(new ImportButtonListener());
		v.addView(this.mImportButton);
		// wrapping in a scroll view
		ScrollView scroll = new ScrollView(this);
		scroll.addView(v);
		return scroll;
	}

	/**
	 * Attempts to import a CSV file.
	 */
	private void importSubmission() {
		File file = new File(filenameValField.getText().toString().trim());
		String tableName = null;
		TableProperties tp = null;
		int pos = tableSpin.getSelectedItemPosition();
		ImportTask task = new ImportTask(this);
		if(pos == 0) {
			tableName = ntnValField.getText().toString();
            showDialog(IMPORT_IN_PROGRESS_DIALOG);
			task.execute(new ImportRequest(true, null, tableName, file));
		} else {
		    tp = tps[pos - 1];
            showDialog(IMPORT_IN_PROGRESS_DIALOG);
		    task.execute(new ImportRequest(false, tp.getTableId(), null, file));
		}
		/**
		Handler iHandler = new ImporterHandler();
		ImporterThread iThread = new ImporterThread(iHandler, tableName, tp,
		        file, (pos == 0));
		showDialog(IMPORT_IN_PROGRESS_DIALOG);
		iThread.start();
		**/
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
	        Intent data) {
	    if(resultCode == RESULT_CANCELED) {return;}
	    Uri fileUri = data.getData();
	    String filepath = fileUri.getPath();
	    filenameValField.setText(filepath);
	}

	/**
	 * A listener for the table name spinner. Adds or removes the "New Table"
	 * name field as necessary.
	 */
	private class tableSpinListener
			implements AdapterView.OnItemSelectedListener {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			if(position == 0) {
				newTableViews.setVisibility(View.VISIBLE);
				mImportButton.setText(getString(R.string.import_import_new_table));
			} else {
				newTableViews.setVisibility(View.GONE);
				mImportButton.setText(getString(R.string.import_append_table));
			}
		}
		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	}

	/**
	 * A listener for the import button. Calls importSubmission() on click.
	 */
	private class ImportButtonListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			importSubmission();
		}
	}

}