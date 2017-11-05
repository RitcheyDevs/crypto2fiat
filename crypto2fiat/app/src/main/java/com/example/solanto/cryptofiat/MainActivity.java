package com.example.solanto.cryptofiat;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.support.v4.text.TextUtilsCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.solanto.cryptofiat.db.TaskContract;
import com.example.solanto.cryptofiat.db.TaskDbHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TaskDbHelper mHelper;
    private ListView taskListView;
    private ArrayAdapter<String> pairListAdapter;
    String fiat_, crypto_;
    AlertDialog alertDialog;
    boolean downloadComplete, downloadSuccessful;
    String[] justPairs;
    ArrayList<String> networkPairs;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHelper = new TaskDbHelper(this);
        setContentView(R.layout.activity_main);
        taskListView = (ListView) findViewById(R.id.list_pairs);
        networkPairs = new ArrayList<>();

        updateUI();
        Toast.makeText(this, "Updating Prices", Toast.LENGTH_LONG).show();
        startBackgroundJob();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_task:
                final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                LayoutInflater inflater = this.getLayoutInflater();
                View dialogView = inflater.inflate(R.layout.new_pair, null);
                dialogBuilder.setView(dialogView);
                Spinner cryptoSpinner = (Spinner) dialogView.findViewById(R.id.cryptospinner);
                Spinner fiatSpinner = (Spinner) dialogView.findViewById(R.id.fiatspinner);
                cryptoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        crypto_ = parent.getItemAtPosition(position).toString();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        crypto_ = "BTC";
                    }
                });
                fiatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        fiat_ = parent.getItemAtPosition(position).toString();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        fiat_ = "USD";
                    }
                });

               Button submitPair = (Button) dialogView.findViewById(R.id.submit_pair);
                submitPair.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addAPair();
                        alertDialog.dismiss();
                    }
                });
                alertDialog = dialogBuilder.create();
                alertDialog.show();

            default:
                return super.onOptionsItemSelected(item);
        }
    }



    public void addNewPair(String pair){
        SQLiteDatabase db = mHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COL_PAIR_TITLE, pair);
        db.insertWithOnConflict(TaskContract.TaskEntry.TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        Log.d(TAG, "New Pair Inserted");
        updateUI(); //network operations and adapter update should happen here
        Toast.makeText(this, "New Pair Added", Toast.LENGTH_SHORT).show();
    }

    public ArrayList<String> getPairsList(){
        ArrayList<String> allPairs = new ArrayList<>();
        SQLiteDatabase db = mHelper.getReadableDatabase();
        Cursor cursor = db.query(TaskContract.TaskEntry.TABLE, new String[]{TaskContract.TaskEntry._ID, TaskContract.TaskEntry.COL_PAIR_TITLE}, null, null, null, null, null);
        while (cursor.moveToNext()){
            int colIndex = cursor.getColumnIndex(TaskContract.TaskEntry.COL_PAIR_TITLE);
            allPairs.add(cursor.getString(colIndex));
        }
        cursor.close();
        db.close();
        return allPairs;
    }

    public void updateListView(ArrayList<String> allpairs){
        if (pairListAdapter == null) {
            pairListAdapter = new ArrayAdapter<>(this, R.layout.item_pair, R.id.pair_data, allpairs);
            taskListView.setAdapter(pairListAdapter);
        }
        else {
            pairListAdapter.clear();
            pairListAdapter.addAll(allpairs);
            pairListAdapter.notifyDataSetChanged();
        }

    }

    public void updateUI(){
        ArrayList<String> newList = getPairsList();
        updateListView(newList);
    }

    public void deletePair(View view){
        View item = (View) view.getParent();
        TextView item_text = (TextView) item.findViewById(R.id.pair_data);
        String pairToDelete = String.valueOf(item_text.getText());
        deleteFromDb(pairToDelete);
        updateUI();
    }

    private void deleteFromDb(String pairToDelete){
        SQLiteDatabase db = mHelper.getWritableDatabase();
        db.delete(TaskContract.TaskEntry.TABLE, TaskContract.TaskEntry.COL_PAIR_TITLE + " = ?", new String[]{pairToDelete});
        db.close();
    }

    private void addAPair(){
        String pair_ticker = crypto_ + "-" + fiat_;
        if (!isDuplicate(pair_ticker)){
            addNewPair(pair_ticker + " : " + "0.00");
        }
        else {
            Toast.makeText(this, "Pair Already Exists", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDuplicate(String pair){
        ArrayList<String> list = getPairsList();
        String[] bound_list = list.toArray(new String[list.size()]);
        for (String p:
             bound_list) {
            String iter_pair = p.split(":")[0].trim();
            Log.d("ITER_PAIR", iter_pair);
            if (iter_pair.equals(pair)) return true;
        }
        return false;
    }

    void runMainRequest(String apiURL) {
        ConnectionHelper con = ConnectionHelper.getInstance(this.getApplicationContext());
        downloadComplete = false;
        downloadSuccessful = false;


        // Request a JSON response from the provided URL.
        JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, apiURL, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try{
                    networkPairs.clear();
                    JSONObject items = response.getJSONObject("BTC");
                    for (int i=0; i < items.names().length(); i++) {
                        String curr2 = items.names().getString(i).trim();
                        String value2 = String.valueOf(items.get(items.names().getString(i)));
                        String ticker = "BTC-" + curr2;
                        if (Arrays.asList(justPairs).contains(ticker)){
                            String newTicker = "BTC-" + curr2 + " : " + value2;
                            networkPairs.add(newTicker);
                        }
                    }

                    JSONObject items2 = response.getJSONObject("ETH");
                    for (int i=0; i < items2.names().length(); i++) {
                        String curr2 = items2.names().getString(i).trim();
                        String value2 = String.valueOf(items2.get(items2.names().getString(i)));
                        String ticker = "ETH-" + curr2;
                        if (Arrays.asList(justPairs).contains(ticker)){
                            String newTicker = "ETH-" + curr2 + " : " + value2;
                            networkPairs.add(newTicker);
                        }
                    }
                    Log.d("UPDATE", "UPDATING");
                    updateListView(networkPairs);
                    downloadComplete = true;
                    downloadSuccessful = true;

                }
                catch (JSONException e){
                    Log.e("PARSING", "Invalid JSON");
                    downloadComplete = true;
                    downloadSuccessful = false;
                }


            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("NETWORK", "Something went wrong");
                Toast.makeText(MainActivity.this, "Something went wrong", Toast.LENGTH_SHORT).show();
                downloadComplete = true;
                downloadSuccessful = false;
            }
        });

        con.addToRequestQueue(jsonRequest);


    }

    private void updatePrices(){
        ArrayList<String> currencyList = getPairsList();
        Set set = new HashSet();
        justPairs = new String[currencyList.size()];
        int counter = 0;
        for (String ticker : currencyList){
            String pot1 = ticker.split(":")[0].trim();
            justPairs[counter] = pot1;
            counter += 1;
            String curr1 = pot1.split("-")[1];
            set.add(curr1);
        }
        String[] uniqueCurrencies = (String[]) set.toArray(new String[set.size()]);
        String urlAttachee = TextUtils.join(",", uniqueCurrencies);
        Log.d("UNIQUE", urlAttachee);
        String apiURL = "https://min-api.cryptocompare.com/data/pricemulti?fsyms=BTC,ETH&tsyms=" + urlAttachee;
        runMainRequest(apiURL);
    }

    private void startBackgroundJob(){
        final Handler handler = new Handler();
        final Runnable job = new Runnable() {
            @Override
            public void run() {
                updatePrices();
                startBackgroundJob();
            }
        };
        handler.postDelayed(job, 3000);

    }



}
