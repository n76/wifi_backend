package org.fitchfamily.android.wifi_backend.ui.data;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014,2015  Tod Fitch
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.fitchfamily.android.wifi_backend.R;
import org.fitchfamily.android.wifi_backend.database.AccessPoint;
import org.fitchfamily.android.wifi_backend.database.Database;

public class WifiListAdapter extends CursorAdapter<WifiListAdapter.ViewHolder> {
    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(listener != null) {
                String bssid = (String) v.getTag();

                listener.onWifiClicked(bssid);
            }
        }
    };

    private Listener listener;
    private int columnSsid;
    private int columnBssid;

    public WifiListAdapter() {
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getString(columnBssid).hashCode();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.wifi_list_content, parent, false);
        view.setOnClickListener(clickListener);
        return new ViewHolder(view);
    }

    @Override
    protected void onCursorChanged(@NonNull Cursor cursor) {
        super.onCursorChanged(cursor);
        columnSsid = cursor.getColumnIndexOrThrow(Database.COL_SSID);
        columnBssid = cursor.getColumnIndexOrThrow(Database.COL_BSSID);
    }

    @Override
    public void bind(ViewHolder holder, Cursor cursor) {
        holder.bind(cursor.getString(columnSsid), cursor.getString(columnBssid));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title, id;
        private final View view;

        public ViewHolder(View view) {
            super(view);
            this.view = view;

            id = (TextView) view.findViewById(R.id.id);
            title = (TextView) view.findViewById(R.id.title);
        }

        public ViewHolder bind(String ssid, String bssid) {
            view.setTag(bssid);

            title.setText(ssid);
            id.setText(AccessPoint.readableBssid(bssid));

            return this;
        }
    }

    public WifiListAdapter listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public Listener listener() {
        return listener;
    }

    public interface Listener {
        void onWifiClicked(String bssid);
    }
}
