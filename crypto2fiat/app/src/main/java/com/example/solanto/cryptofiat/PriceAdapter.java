package com.example.solanto.cryptofiat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Created by Solanto on 04/11/2017.
 */

public class PriceAdapter extends BaseAdapter {

    private LayoutInflater mInflater;
    String[] pairs;


    PriceAdapter(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_pair, null);
        }

        ((TextView) convertView.findViewById(R.id.pair_data)).setText(pairs[position]);


        return convertView;
    }
}
